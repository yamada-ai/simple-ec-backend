# 戦略比較（Export Strategies）

このドキュメントは、1対多データの横持ち CSV エクスポートに対する各戦略を整理する。

詳細な数学モデルは [problem-model.md](problem-model.md) を参照。

## 現在のアーキテクチャ

現在の属性 CSV エクスポートは、戦略ごとのデータ取得を `OrderAttributeRowSource` に分離している。

```text
OrderAttributeExportService
  -> OrderAttributeExportSchema
  -> OrderAttributeRowSource
     -> PreloadOrderAttributeRowSource
     -> MultisetOrderAttributeRowSource
     -> SequenceWindowOrderAttributeRowSource
     -> SpliteratorWindowOrderAttributeRowSource
  -> OrderAttributeCsvWriter
```

`OrderAttributeExportService` は、属性定義から schema を作り、strategy を選び、CSV writer に渡すだけにしている。

この構造により、SQL Pivot や Imperative ResultSet baseline を追加しても、既存戦略の責務を壊しにくい。

## Strategy Matrix

| 戦略 | 横展開が起きる場所 | DB からの結果の形 | JVM メモリ傾向 | 主なコスト |
| --- | --- | --- | --- | --- |
| PRELOAD | JVM | base orders + 全属性値 | $\Theta(V)$ | Map preload、heap、GC |
| MULTISET | DB + jOOQ mapping + JVM CSV projection | ネスト済み属性を持つ $N$ 行 | $\Theta(A + a_{\text{max}})$ + nested mapping | JSONB emulation、nested materialization |
| SEQUENCE_WINDOW | JVM ordered group fold | 縦持ち JOIN 済み行 | $\Theta(A + a_{\text{max}})$ | ordered streaming、注文ごとの Map |
| SPLITERATOR_WINDOW | JVM ordered group fold | 縦持ち JOIN 済み行 | $\Theta(A + a_{\text{max}})$ | custom Spliterator の状態機械 |
| SQL Pivot | DB | 横持ちの $N$ 行 | 現行実装では `OrderAttributeCsvRow` に復元 | conditional aggregation、巨大な動的 SELECT |
| JSON aggregation | DB + JSON parse | JSON オブジェクトを持つ $N$ 行 | parse 対象に依存 | JSONB object aggregation と parse |
| Imperative ResultSet | JVM ordered group fold | 縦持ち JOIN 済み行 | $\Theta(A + a_{\text{max}})$ | 最も低レベルな app baseline |
| Direct Writer | CSV layer | 取得元に依存 | 行あたりの allocation を削減 | escaping / writer の正しさ |

## PRELOAD

### やっていること

1. 全属性値を JVM の Map にロードする。
2. base の注文行を streaming する。
3. 各注文について、対応する属性 Map を引く。
4. CSV 行を1つ emit する。

擬似コード:

```kotlin
val attrMap: Map<Long, Map<Long, String>> = repository.loadAttributeValueMap(from, to)

repository.streamOrdersBase(from, to).use { stream ->
    stream.asSequence()
        .map { base -> base.toCsvRow(attrMap[base.orderId] ?: emptyMap()) }
        .forEach(csvWriter::write)
}
```

### 長所

- メンタルモデルが単純。
- $V$ が heap に十分収まる場合は速い。
- DB クエリの形がシンプル。

### 短所

- JVM メモリが $V$ に比例して増える。
- Dense/Wide なデータセットでは heap と GC を圧迫しやすい。
- 小規模ベンチマークではメモリリスクを隠したまま速く見えがちである。

### 向いている場面

- 小〜中規模のエクスポート。
- $V$ が有界であることがわかっている。
- heap に余裕があるバッチ環境。

## MULTISET

### やっていること

jOOQ `MULTISET` を使い、注文ごとに属性値のネストしたリストを取得する。

現在の read model:

```kotlin
OrderWithAttributes(
    orderId = ...,
    attributes = List<OrderAttributeValue>
)
```

### 長所

- コードの形がドメインの read model と一致する。
- 手動での ordered windowing を避けられる。
- 親子データが1つのクエリ結果として表現される。

### 短所

- PostgreSQL には native `MULTISET` がないため、jOOQ は SQL/JSON / JSONB 式でエミュレートする。
- DB 側で nested aggregation / materialization のコストを払う可能性がある。
- jOOQ がネスト結果をデコードして Kotlin オブジェクトへマッピングし直す必要がある。
- 単純な縦持ち JOIN より必ずしも速いわけではない。

### 重要な解釈

`MULTISET` は、小規模実行で遅いからといって「悪い」戦略と判断すべきではない。

これは異なる責務配置を表している。

```text
JVM 側での手動 windowing
  vs
DB 側でのネスト構築 + jOOQ の nested mapping
```

このリポジトリにとっては、その違いこそが比較したいポイントである。

### 向いている場面

- 最低レベルの制御より、コードの明快さが重要な場合。
- 親子 read model を CSV エクスポート以外でも再利用する場合。
- $A$ と $V$ が中程度の場合。

## SEQUENCE_WINDOW

### やっていること

`order_id, attribute_definition_id` でソートされた縦持ち JOIN 済みストリームを取得し、Kotlin `Sequence` で隣接行を `order_id` ごとにグルーピングする。

擬似コード:

```kotlin
streamOrdersWithAttributes(from, to)
    .asSequence()
    .windowByOrderId()
```

### 長所

- メモリ上に保持するのは処理中の注文の属性だけ。
- 変換を lazy な sequence として表現できる。
- ordered streaming に適している。

### 短所

- 正しさが入力の順序に依存する。
- `Sequence` は dense output の下界そのものは解決しない。
- 出力行ごとに Map、CSV レコード生成用の List を allocation する点は変わらない。

### 正しさの前提条件

入力は次の順序である必要がある。

```text
order_id asc, attribute_definition_id asc
```

同一の `order_id` が分断された複数グループとして現れると、同じ注文が複数の CSV 行として emit されてしまう。

また、PostgreSQL JDBC で実際に streaming するには、`fetchSize > 0` と transaction 境界が必要である。現行実装では export query に `fetchSize(1000)` を指定し、CSV 書き込み全体を read-only transaction 内で実行する。

### 向いている場面

- $V$ が大きく、PRELOAD のメモリリスクが高い場合。
- DB が ordered な行を効率よく返せる場合。
- CSV を逐次生成したい場合。

## SPLITERATOR_WINDOW

### やっていること

`SEQUENCE_WINDOW` と同じ ordered group fold を、自作の `Spliterator` として実装する。

### 長所

- Stream 側の状態機械を明示的にできる。
- characteristics を正直に宣言できる。
- 一部の stream 変換が安全に split できない理由を説明するのに有用。

### 短所

- `Sequence` よりコード量が多い。
- `trySplit()` が `null` を返すため、意図的に非並列である。
- 正しさは依然として入力の順序に依存する。

### Spliterator の characteristics

- `ORDERED` は該当する場合、source から継承される。
- emit する行には `NONNULL` を設定する。
- 縦持ちの入力行が横持ちの出力行になるため、`SIZED` / `SUBSIZED` は設定しない。
- 任意の split が順序境界を壊しうるため、`trySplit()` は `null` を返す。

### 向いている場面

- Java Stream/Spliterator の仕組みを説明したい場合。
- Kotlin `Sequence` をより低レベルな stream primitive と比較したい場合。

## SQL Pivot

状態: 実装済み。

### やっていること

属性定義から動的に conditional aggregation のフィールドを生成する。

擬似コード:

```kotlin
val pivotColumns = definitions.map { def ->
    DSL.max(
        DSL.`when`(
            ORDER_ATTRIBUTE_VALUE.ATTRIBUTE_DEFINITION_ID.eq(def.id.value),
            ORDER_ATTRIBUTE_VALUE.VALUE
        )
    ).`as`("attr_${def.id.value}")
}
```

クエリの形:

```sql
select
  order.id,
  customer.name,
  max(case when attribute_definition_id = 1 then value end) as attr_1,
  max(case when attribute_definition_id = 2 then value end) as attr_2
from orders
left join order_attribute_value ...
group by order.id, customer.name
order by order.id
```

### 長所

- JVM は注文ごとに1行だけ受け取る。
- JVM 側で主要な横展開を行う必要がない。
- PostgreSQL 側で横持ち化した結果を、既存の `OrderAttributeCsvRow` に戻して共通 writer に載せられる。

### 短所

- SQL の長さが $A$ に比例して伸びる。
- DB が conditional aggregation のコストを払う。
- 動的な SELECT は型付き DTO にしづらい。
- クエリプランと DB CPU が支配的になる。
- 現行実装では既存 interface を維持するため、DB 側で横展開した結果を JVM 側で `Map<DefinitionId, Value>` に戻している。

### 向いている場面

- CSV を直接出力したい場合。
- DB に十分な CPU とメモリがある場合。
- DB 側の横展開と JVM 側の横展開を比較したい場合。

## JSON Aggregation

状態: 未実装（計画中）。

### やること（予定）

PostgreSQL の JSONB aggregation を明示的に使う。

```sql
jsonb_object_agg(attribute_definition_id, value)
```

### 長所

- JSONB という責務を明示的にできる。
- jOOQ `MULTISET` との比較対象として良い。
- DB からの結果の形は注文ごとに1行になる。

### 短所

- JSONB の生成・parse コストがかかる。
- 型変換が直接的ではなくなる。
- CSV の列順や欠損セルの扱いは別途必要。

### 向いている場面

- PostgreSQL 側のネストしたペイロード戦略を説明したい場合。
- jOOQ の抽象化を明示的な SQL と比較したい場合。

## Imperative ResultSet Baseline

状態: 未実装（計画中）。

### やること（予定）

`Sequence` / `Spliterator` / jOOQ `Record` mapping を使わず、同じ ordered group fold を実装する。

擬似コード:

```kotlin
while (rs.next()) {
    if (currentOrderId != rs.orderId) {
        flushCurrentOrder()
        startNextOrder()
    }
    addAttributeIfPresent()
}
flushCurrentOrder()
```

### 長所

- アルゴリズムのコストと抽象化のコストを分離できる。
- allocation と elapsed time の低レベルな baseline になる。
- `Sequence` のオーバーヘッドが実際に効くかどうかを判断するのに有用。

### 短所

- リソース管理をより手動で行う必要がある。
- アプリケーションコードとしては idiomatic ではない。
- 正しさを間違えやすい。

### 向いている場面

- ベンチマークの baseline。
- allocation profiling。
- 抽象化と制御のトレードオフを説明する場合。

## Direct CSV Writer

状態: 未実装（計画中）。

### やること（予定）

`OrderAttributeCsvRow.toRecord()` が行ごとに `List<String>` を生成しているのを避ける。

現在の形:

```kotlin
val values = schema.definitionIds.map { defId -> attributes[defId] ?: "" }
return listOf(baseColumns...) + values
```

考えられる直接書き込みの形:

```kotlin
printer.print(orderId)
printer.print(customerId)
...
schema.definitionIds.forEach { defId ->
    printer.print(attributes[defId] ?: "")
}
printer.println()
```

### 長所

- 行あたりの allocation を削減できる。
- CSV writer のコストをデータ取得戦略から分離できる。

### 短所

- CSV エスケープの正しさを維持する必要がある。
- writer がより手続き的になりうる。
- DB 取得戦略とは別軸で比較すべきである。

### 向いている場面

- allocation profiling。
- $N \cdot A$ のセル処理が支配的な wide なデータセット。

## Recommended PR Sequence

各ステップは MD5 の一致を維持すべきなので、小さい PR に分けるのが望ましい。

1. 問題モデルと現行戦略のドキュメント化。
2. formal benchmark result workflow と chart generation の追加。
3. SQL Pivot 戦略の追加。
4. Imperative ResultSet baseline の追加。
5. Direct Writer 比較の追加。
6. JSON aggregation 戦略の追加。
7. isolated run、JFR、EXPLAIN artifacts の追加。
