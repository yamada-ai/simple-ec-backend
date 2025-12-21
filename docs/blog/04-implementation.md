# 実装戦略の具体化

本章では、前章で定式化した理論モデルを実装に落とし込む。ここでの目的は「完全なコード提示」ではなく、各戦略の核心的な設計判断と、それが理論モデルのどの部分に対応するかを明示することである。

## 実験環境と技術スタック

本章の実装および計測は、以下の技術スタックで行っている。

**言語・実行環境**:
- Kotlin 2.0.21
- Java 21 (Temurin)
- JVM 設定: `-Xmx512m`（ヒープ上限512MB）

**フレームワーク・ライブラリ**:
- Spring Boot 3.5.9
- jOOQ 3.19.28（型安全 SQL + `fetchLazy` / `fetchStream`）
- Apache Commons CSV 1.12.0（CSV 生成）

**データベース**:
- PostgreSQL 17
- Flyway 10.21.0（マイグレーション）

**計測・監視**:
- Micrometer + Prometheus（メトリクス収集）
  - 処理時間: `http.server.requests`（p50/p95/max）
  - メモリ: `jvm.memory.used` / `jvm.memory.max`
  - GC: pause時間、allocation rate、promotion rate

**実行形態**: 単一 JVM プロセス、同期実行

## 実験用データモデルと前提

### データセット設計

本稿では、理論モデルの各成分（$N \cdot A$、$V$、$a_{\text{max}}$）を独立に検証するため、以下の3条件で測定を行う：

| 条件 | $N$ | $A$ | 密度 $\bar{a}$ | $V$ | 検証目的 |
|------|-----|-----|---------------|-----|----------|
| **Baseline** | 30,000 | 15 | Dense (15) | 450,000 | 基準測定 |
| **Sparse** | 30,000 | 15 | Sparse (2) | 60,000 | メモリ差（$V$ vs $a_{\text{max}}$） |
| **Wide** | 30,000 | 120 | Dense (120) | 3,600,000 | $N \cdot A$ 支配の検証 |

**設計方針**:
- **Baseline**: 中程度の列数（$A=15$）で密データ（最悪ケース寄り）、基準となる測定
- **Sparse**: 同一の $A$ で密度を下げ、PRELOAD（$O(V)$）と WINDOW（$O(a_{\text{max}})$）のメモリ差を観測
- **Wide**: 列数 $A$ を増やし、動的列問題の本質である $N \cdot A$ 支配（列埋めコスト）を直接検証

### 測定系統（2系統）

本稿では、計算コストと I/O コストを分離するため、2系統の測定を行う。

**系統1: Compute測定（主軸）**
- **出力先**: `NullOutputStream` + `DigestOutputStream`（MD5計算のみ）
- **目的**: I/O を除いた純粋な計算コスト（$T_{\text{DB}} + T_{\text{app}}$）を測定
- **利点**: ファイルサイズ制限なし、Wide条件（$A=120$）も安全に測定可能
- **実装**: ベンチマーク専用エンドポイント `/api/export/orders/attributes/benchmark`

**系統2: 実ファイル出力（参考）**
- **出力先**: 実ファイル（CSV）
- **目的**: I/O を含む現実の処理時間（$T_{\text{DB}} + T_{\text{app}} + T_{\text{IO}}$）を測定
- **位置づけ**: 参考値（I/O は環境依存が大きいため）

### 測定方法

- **試行回数**: warmup 5回 + measurement 10回
- **指標**: median（中央値）、IQR（四分位範囲）、max（最大値）
  - p95 は試行回数が少ないため使用しない
- **順序バイアス除去**: ラウンドロビン実行（戦略順序を固定しない）
- **メトリクス**:
  - 処理時間: 系統1では API レスポンス、系統2では実測時間
  - メモリ: `jvm.memory.used{area="heap"}` のピーク値（$M_{\text{app}}$ の近似）
  - GC: `jvm.gc.pause_seconds_sum`（GC停止時間の累積）

### 検証基準

全戦略で同一の CSV が出力されることを MD5 ハッシュで検証する。これにより、出力の正しさを保証したうえで、性能とメモリの差異のみを比較対象とする。

## 各戦略の実装方針

以下では、各戦略の核心部分のみを抜粋して示す。完全な実装は GitHub リポジトリを参照されたい。

### 1. PRELOAD

**設計方針**: 属性値を全件ロードし、`Map<OrderId, Map<DefId, Value>>` に展開して参照。

**核心部分**:

```kotlin
// 1. 属性値を全件取得し Map 化（O(V)）
val attrMap: Map<Long, Map<Long, String>> =
    orderRepository.loadAttributeValueMap(from, to)

// 2. 注文をストリーミングしながら Map 参照（O(N·A)）
orderRepository.streamOrdersBase(from, to).use { stream ->
    stream.forEach { base ->
        val row = OrderAttributeCsvRow(
            orderId = base.orderId,
            customerId = base.customerId,
            customerName = base.customerName,
            customerEmail = base.customerEmail,
            orderDate = base.orderDate,
            attributes = attrMap[base.orderId] ?: emptyMap()  // O(1) lookup
        )
        csvPrinter.printRecord(row.toRecord(definitionIds))
    }
}
```

**対応する理論モデル**:

$$
T_{\text{app}} = O(V + N \cdot A), \quad M_{\text{app}} = O(V)
$$

全属性値をメモリに保持するため、$V$ に比例したメモリを消費する。

### 2. MULTISET

**設計方針**: jOOQ の `multiset()` 機能を用いて、DB 側で「注文 → 属性値群」をネスト化。

**核心部分**:

```kotlin
// jOOQ でネスト取得
select(
    ORDER.ID,
    CUSTOMER.NAME,
    CUSTOMER.EMAIL,
    ORDER.ORDER_DATE,
    multiset(
        select(
            ORDER_ATTRIBUTE_VALUE.ATTRIBUTE_DEFINITION_ID,
            ORDER_ATTRIBUTE_VALUE.VALUE
        )
        .from(ORDER_ATTRIBUTE_VALUE)
        .where(ORDER_ATTRIBUTE_VALUE.ORDER_ID.eq(ORDER.ID))
    ).`as`("attributes")
)
.from(ORDER)
.join(CUSTOMER).on(ORDER.CUSTOMER_ID.eq(CUSTOMER.ID))
```

アプリ側では、ネストされた属性値リストを Map に変換して列埋め：

```kotlin
stream.forEach { order ->
    val valueMap = order.attributes.associate {
        it.attributeDefinitionId.value to it.value
    }
    val row = OrderAttributeCsvRow(
        orderId = order.orderId,
        // ...
        attributes = valueMap
    )
    printer.printRecord(row.toRecord(definitionIds))
}
```

**対応する理論モデル**:

$$
T_{\text{app}} = O(N \cdot (A + \bar{a})) = O(N \cdot A + V)
$$

ただし $T_{\text{DB}}$ が異なり、内部的に結果の materialization や相関サブクエリ評価が発生する可能性があるため、純粋なストリーミング処理とは性質が異なる。

### 3. SEQUENCE_WINDOW

**設計方針**: 縦持ち JOIN 結果を Kotlin Sequence で窓処理し、order_id が変わるまでをバッファリング。

**核心部分**:

```kotlin
// 縦持ち JOIN を order_id でソート
orderRepository.streamOrdersWithAttributes(from, to).use { stream ->
    stream.asSequence()
        .windowByOrderId()  // 拡張関数で窓処理
        .forEach { row ->
            csvPrinter.printRecord(row.toRecord(definitionIds))
        }
}
```

`windowByOrderId()` の実装（概念）:

```kotlin
fun Sequence<OrderAttributeJoinedRow>.windowByOrderId(): Sequence<OrderAttributeCsvRow> = sequence {
    var currentOrderId: Long? = null
    val buffer = mutableMapOf<Long, String>()
    var baseInfo: OrderAttributeJoinedRow? = null

    for (row in this@windowByOrderId) {
        if (row.orderId != currentOrderId && currentOrderId != null) {
            // 注文確定 → emit
            yield(OrderAttributeCsvRow.from(baseInfo!!, buffer.toMap()))
            buffer.clear()
        }

        baseInfo = row
        currentOrderId = row.orderId

        if (row.attributeDefinitionId != null) {
            buffer[row.attributeDefinitionId] = row.attributeValue!!
        }
    }

    // 最後の注文を emit
    if (baseInfo != null) {
        yield(OrderAttributeCsvRow.from(baseInfo, buffer.toMap()))
    }
}
```

**対応する理論モデル**:

$$
T_{\text{app}} = O(R + N \cdot A), \quad M_{\text{app}} = O(a_{\text{max}})
$$

1注文分のバッファのみを保持するため、メモリは $a_{\text{max}}$ に比例。

### 4. SPLITERATOR_WINDOW

**設計方針**: Stream の世界で窓処理を実現するため、カスタム Spliterator を実装。

本戦略は性能最適化というより、Stream API 上で window 処理を実現可能であることを示す実験的実装である。

**核心部分**:

```kotlin
class OrderAttributeWindowSpliterator(
    private val source: Spliterator<OrderAttributeJoinedRow>
) : Spliterators.AbstractSpliterator<OrderAttributeCsvRow>(
    Long.MAX_VALUE,
    Spliterator.ORDERED or Spliterator.NONNULL
) {
    private val buffer = mutableMapOf<Long, String>()
    private var currentOrderId: Long? = null
    private var baseInfo: OrderAttributeJoinedRow? = null

    override fun tryAdvance(action: Consumer<in OrderAttributeCsvRow>): Boolean {
        while (source.tryAdvance { row ->
            if (row.orderId != currentOrderId && currentOrderId != null) {
                // emit して return true
                action.accept(OrderAttributeCsvRow.from(baseInfo!!, buffer.toMap()))
                buffer.clear()
                baseInfo = row
                currentOrderId = row.orderId
                // バッファに追加
                return@tryAdvance
            }

            baseInfo = row
            currentOrderId = row.orderId
            if (row.attributeDefinitionId != null) {
                buffer[row.attributeDefinitionId] = row.attributeValue!!
            }
        }) { /* continue */ }

        // 最後の注文を emit
        if (baseInfo != null) {
            action.accept(OrderAttributeCsvRow.from(baseInfo!!, buffer.toMap()))
            baseInfo = null
            return true
        }
        return false
    }
}
```

**対応する理論モデル**:

SEQUENCE_WINDOW と同様に $O(R + N \cdot A)$ だが、Stream API の push モデル上で実装される点が異なる。

## 実装の完全版

各戦略の完全な実装は以下のリポジトリを参照：

- [OrderAttributeExportService.kt](https://github.com/yamada-ai/simple-ec-backend/blob/main/src/main/kotlin/com/example/ec/application/export/OrderAttributeExportService.kt)
- [ベンチマークスクリプト](https://github.com/yamada-ai/simple-ec-backend/tree/main/scripts/bench)

次章では、これらの実装を用いた実測結果を示し、理論モデルとの対応を検証する。
