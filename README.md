# Simple EC Backend: 1-to-Many Wide CSV Export Lab

Kotlin/JVM で **1対多データを固定ヘッダ CSV へ横展開する設計と性能**を検証する実験用 EC バックエンド。

## プロジェクト概要

このプロジェクトは、注文 `Order` に複数のユーザ定義属性 `OrderAttributeValue` が紐づく 1対多データを、CSV では「1注文=1行」の横持ち形式に変換する処理を題材にする。

目的は、単なる CSV 出力 Tips ではなく、次の責務配置を比較すること。

- Kotlin `Sequence` / Java `Spliterator` で ordered windowing する
- jOOQ `MULTISET` で親子データをネスト取得する
- 属性値を JVM Map に preload する
- PostgreSQL 側で SQL Pivot / JSON aggregation する
- CSV writer と allocation / GC / I/O のコストを分けて見る

Kotlin Fest 2026 に向けて、業務データエクスポートの設計・性能・計測を説明できる実験リポジトリとして整備している。

### 本質的な課題

RDB 上では、注文属性値は自然には縦持ちになる。

```text
order_id | attribute_definition_id | value
-------- | ----------------------- | ----------------
1        | gift_wrap               | yes
1        | delivery_note           | morning delivery
3        | campaign_id             | summer-2026
```

しかし、業務 CSV では固定ヘッダの横持ち形式で出したい。

```text
order_id,customer_name,gift_wrap,delivery_note,campaign_id
1,Alice,yes,morning delivery,
2,Bob,,,
3,Carol,,,summer-2026
```

入力が sparse でも、固定ヘッダ CSV は dense な出力になる。存在しない属性値も空セルとして出力する必要があるため、正しい CSV を出す限り $\Omega(N \cdot A)$ の出力下界は避けられない。

詳細は [docs/problem-model.md](docs/problem-model.md) を参照。

## ドキュメント

| Document | Purpose |
| --- | --- |
| [docs/problem-model.md](docs/problem-model.md) | 具体例、$N$, $A$, $V$, $a_{\text{max}}$, $B$, $S$、出力下界、責務配置の問題定義 |
| [docs/strategies.md](docs/strategies.md) | PRELOAD / MULTISET / WINDOW / SQL Pivot / ResultSet baseline の比較 |
| [docs/benchmark/measurement-design.md](docs/benchmark/measurement-design.md) | benchmark artifact、Prometheus、round-robin / isolated の測定設計 |
| [docs/benchmark/results.md](docs/benchmark/results.md) | 測定結果の索引。smoke run と formal run の置き場 |
| [docs/talk-outline.md](docs/talk-outline.md) | Kotlin Fest 2026 向け発表アウトラインとプロポーザル本文案 |
| [docs/architecture.md](docs/architecture.md) | レイヤ構成と依存ルール |

## 検証マトリクス

### Main: 1対多データの横持ち CSV エクスポート

| Strategy | 実装方法 | 主な責務配置 | メモリ傾向 |
|----------|---------|------------|-----------|
| **preload** | 全属性値を JVM Map に格納 | JVM heap に $V$ を保持 | $\Theta(V)$ |
| **multiset** | jOOQ `MULTISET` で親子をネスト取得 | DB 側ネスト化 + jOOQ mapping | $\Theta(A + a_{\text{max}})$ + nested mapping |
| **sequence-window** | Kotlin `Sequence` + orderId windowing | JVM 側 ordered group fold | $\Theta(A + a_{\text{max}})$ |
| **spliterator-window** | custom `Spliterator` + orderId windowing | JVM 側 ordered group fold | $\Theta(A + a_{\text{max}})$ |
| **sql-pivot** | PostgreSQL conditional aggregation | DB 側横展開 | `OrderAttributeCsvRow` に復元 |
| **imperative-result-set** | JDBC ResultSet に近い手続き baseline | JVM 側 ordered group fold | $\Theta(A + a_{\text{max}})$ |
| **json-aggregation** | PostgreSQL `jsonb_object_agg` | DB 側JSONB集約 + JVM parse | JSONB payload に依存 |

戦略ごとの詳細は [docs/strategies.md](docs/strategies.md) を参照。

### Legacy: Order -> OrderItem 行展開

初期の検証として、Order -> OrderItem の行展開パターンも残している。

| Strategy | 実装方法 | 評価型 | 中間オブジェクト |
|----------|---------|--------|----------------|
| **Sequence** | `flatMap` on Kotlin Sequence | Pull | 最小 |
| **Stream flatMap** | `flatMap` on Java Stream | Push | 中間Stream生成 |
| **Stream mapMulti** | `mapMulti` (Java 16+) | Push | Consumer直接 |
| **Spliterator** | カスタムSpliterator | Pull | 手動管理 |

### 検証環境条件

| 項目 | 値 | 備考 |
|------|-----|------|
| メモリ制限 | 512MB | `-Xmx512m` |
| 測定データセット | 3条件 | Baseline/Sparse/Wide（詳細は下記） |
| 測定系統 | round-robin artifact | `samples.jsonl` + Prometheus range data |
| fetchSize / cursor | `fetchSize=1000` + read-only transaction | PostgreSQL JDBC の cursor 条件を満たす export stream query |

**データセット詳細**:

| 条件 | N (orders) | A (attrs) | 密度（ā） | V (属性値総数) | 目的 |
|------|-----------|----------|----------|--------------|------|
| **Baseline** | 30,000 | 15 | Dense (15) | 450,000 | 基準測定 |
| **Sparse** | 30,000 | 15 | Sparse (2) | 60,000 | メモリ差検証 |
| **Wide** | 30,000 | 120 | Dense (120) | 3,600,000 | N·A支配検証 |

### 検証の焦点

- **理論モデルの検証**: $N \cdot A$ 支配、$V$（属性値総数）の影響、メモリ使用量（$M_{\text{app}} = O(V)$ vs $O(A + a_{\text{max}})$）
- **データ密度の影響**: Dense vs Sparse でのメモリ・GC挙動の差
- **動的列スケール**: 列数（A）増加時の列埋めコスト、I/O支配
- **ストリーミング処理**: 遅延評価でメモリ圧迫を回避できるか

## 技術スタック

- **Kotlin** 2.0.21
- **Java** 21 (Temurin)
- **Spring Boot** 3.5.9
- **PostgreSQL** 17
- **jOOQ** 3.19.28 (型安全 SQL + fetchLazy/fetchStream)
- **Flyway** 10.21.0 (DB マイグレーション)
- **Kotest** 5.9.1 (テスト)
- **Detekt** 1.23.8 (静的解析)
- **OpenAPI Generator** 7.10.0 (API スキーマ駆動開発)
- **Apache Commons CSV** 1.12.0 (CSV生成)
- **Micrometer + Prometheus** (メトリクス収集)

## アーキテクチャ

層構成と依存ルールは [`docs/architecture.md`](docs/architecture.md) を参照。

## 🗄 DB構成

### 基本エンティティ（DDD: 単数形）
- `customer`（顧客）
- `order`（注文）
- `order_item`（注文明細）→ 初期検証用の行展開データ

### 1対多横持ち CSV 用（本プロジェクトのコア）
- `order_attribute_definition`（注文属性定義）
  - ユーザが定義するカスタム項目（例：ギフト包装種別、配送指示、キャンペーンID）
- `order_attribute_value`（注文属性値）
  - 各注文に対する属性値のスパース行列
  - `(order_id, attribute_definition_id, value)` 構造
  - **これを CSV 出力時に固定ヘッダの横持ち形式へ変換する**のが本題

## 🚀 セットアップ

### 1. PostgreSQL 起動

```bash
docker compose up -d
```

- PostgreSQL: `localhost:5433` (ポート5432が使用中の場合に5433を使用)
- pgAdmin: `http://localhost:5050`
  - Email: `admin@example.com`
  - Password: `admin`

> **Note**: デフォルトのPostgreSQLポート（5432）が既に使用されている場合、compose.yamlで5433ポートを使用しています。

### 2. 初回セットアップ（マイグレーション & コード生成）

```bash
# Flywayマイグレーション実行
./gradlew flywayMigrate

# jOOQコード生成（マイグレーション後に実行）
./gradlew generateJooq

# ビルド
./gradlew clean build
```

> **Note**: `generateJooq`は自動的に`flywayMigrate`に依存するよう設定されています。

### 3. アプリケーション起動

```bash
./gradlew bootRun
```

- API: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui.html`

## データ投入（実験用）

Admin API でテストデータを投入・削除できます（実験用機能）。

### データ投入

```bash
# 基本: 100 顧客、1000 注文を生成
curl -X POST "http://localhost:8080/admin/seed?customers=100&orders=1000"

# 属性付き: 100 顧客、1000 注文、15 属性定義を生成
curl -X POST "http://localhost:8080/admin/seed?customers=100&orders=1000&attrs=15"

# 再現可能なデータ: seed パラメータで固定
curl -X POST "http://localhost:8080/admin/seed?customers=100&orders=1000&attrs=15&seed=42"
```

### データ確認

```bash
# 現在のデータ件数を確認
curl "http://localhost:8080/admin/summary"
# => {"customers":100,"orders":1000,"orderItems":~5000,"attributeDefinitions":15}
```

### データ削除

```bash
curl -X DELETE "http://localhost:8080/admin/truncate"
```

### ベンチマーク用データ投入（スクリプト版）

大量データでのパフォーマンス検証用：

```bash
# デフォルト: 5,000 注文 × 15 属性
./scripts/bench/seed_benchmark_dataset.sh

# カスタム: 30,000 注文 × 15 属性
./scripts/bench/seed_benchmark_dataset.sh 30000 15

# 環境変数で指定
ORDERS=10000 ATTRS=20 SEED=999 ./scripts/bench/seed_benchmark_dataset.sh
```

詳細は [`scripts/bench/README.md`](scripts/bench/README.md) を参照。

## 実験用 API

### Phase 1: CSV エクスポート（Order → OrderItem 行展開）✅ 実装済み

```bash
# Kotlin Sequence 版
curl "http://localhost:8080/api/export/orders?strategy=sequence" > orders_sequence.csv

# Java Stream (flatMap) 版
curl "http://localhost:8080/api/export/orders?strategy=stream-flatmap" > orders_flatmap.csv

# Java Stream (mapMulti) 版
curl "http://localhost:8080/api/export/orders?strategy=stream-mapmulti" > orders_mapmulti.csv

# カスタム Spliterator 版
curl "http://localhost:8080/api/export/orders?strategy=spliterator" > orders_spliterator.csv
```

### Main: CSV エクスポート（注文属性の横持ち展開）

```bash
# Map 事前ロード版
curl "http://localhost:8080/api/export/orders/attributes?strategy=preload" > orders_preload.csv

# jOOQ multiset 版
curl "http://localhost:8080/api/export/orders/attributes?strategy=multiset" > orders_multiset.csv

# Sequence + Windowing 版
curl "http://localhost:8080/api/export/orders/attributes?strategy=sequence-window" > orders_sequence.csv

# Spliterator + Windowing 版
curl "http://localhost:8080/api/export/orders/attributes?strategy=spliterator-window" > orders_spliterator.csv

# SQL Pivot / Conditional Aggregation 版
curl "http://localhost:8080/api/export/orders/attributes?strategy=sql-pivot" > orders_sql_pivot.csv

# Imperative ResultSet baseline 版
curl "http://localhost:8080/api/export/orders/attributes?strategy=imperative-result-set" > orders_resultset.csv

# PostgreSQL JSON aggregation 版
curl "http://localhost:8080/api/export/orders/attributes?strategy=json-aggregation" > orders_json_aggregation.csv
```

### ベンチマーク実行（reproducible artifact runner）

事前に backend と Prometheus を起動し、データ投入を済ませる。

```bash
python3 scripts/bench/run_benchmark.py \
  --condition baseline \
  --mode round-robin \
  --orders 30000 \
  --attrs 15 \
  --warmup 5 \
  --runs 10
```

この runner は以下を `docs/benchmark/runs/<run-id>/` に保存する。

```text
run.json
environment.json
samples.jsonl
samples.csv
summary.json
summary.md
prometheus/range/*.json
```

処理時間の正は `/api/export/orders/attributes/benchmark` の JSON (`elapsedMs`, `md5`) とする。Prometheus は heap / GC / allocation / CPU の補助時系列として保存する。

旧 `scripts/bench/run_benchmark.sh` は互換用に残している。発表・記事用の再分析可能な記録は Python runner を使う。

## プロジェクト構成

```
src/
├── main/
│   ├── kotlin/
│   │   └── com/example/ec/
│   │       ├── presentation/      # HTTP controller, mapper, streaming response
│   │       ├── application/       # use case, application service, export strategy
│   │       ├── domain/            # entity, value object, repository contract, read model
│   │       └── infrastructure/    # jOOQ, repository implementation
│   └── resources/
│       ├── db/migration/          # Flyway マイグレーション
│       ├── openapi/               # OpenAPI 定義
│       └── application.yaml       # 設定ファイル
└── test/
    └── kotlin/                    # Kotest テスト
```

## 開発タスク

```bash
# DBマイグレーション
./gradlew flywayMigrate

# コード生成
./gradlew generateJooq      # jOOQ (自動的にflywayMigrateを実行)
./gradlew openApiGenerate   # OpenAPI

# 静的解析
./gradlew detekt

# テスト実行
./gradlew test

# ビルド
./gradlew clean build

# アプリケーション起動
./gradlew bootRun
```

### 🛠 トラブルシューティング

**jOOQ生成がエラーになる場合**:
```bash
# DBが起動しているか確認
docker ps | grep simple-ec-postgres

# マイグレーションを先に実行
./gradlew flywayMigrate

# jOOQ再生成
./gradlew clean generateJooq
```

**ポート競合の場合**:
- `compose.yaml`でポート番号を変更
- `application.yaml`と`build.gradle.kts`のJDBC URLも同様に変更

## 📝 実装状況

### 完了

- [x] Admin API 実装（seed, truncate, summary）
- [x] Legacy: CSV エクスポート（Order → OrderItem 行展開）
  - [x] Kotlin Sequence 版
  - [x] Java Stream (flatMap) 版
  - [x] Java Stream (mapMulti) 版
  - [x] カスタム Spliterator 版
- [x] Main: 1対多データの横持ち CSV エクスポート
  - [x] Map 事前ロード版（preload）
  - [x] jOOQ multiset 版
  - [x] Sequence + Windowing 版（sequence-window）
  - [x] Spliterator + Windowing 版（spliterator-window）
  - [x] SQL Pivot / Conditional Aggregation 版（sql-pivot）
  - [x] Imperative ResultSet baseline 版（imperative-result-set）
  - [x] PostgreSQL JSON aggregation 版（json-aggregation）
- [x] Order Attributes CRUD API（属性定義・属性値）
- [x] Benchmark artifact runner（`scripts/bench/run_benchmark.py`）
- [x] P0 docs（problem model, strategies, measurement design, talk outline）

### 今後の拡張案

- [ ] Baseline / Sparse / Wide の formal benchmark
- [ ] Chart generation
- [ ] SQL Pivot strategy
- [ ] Imperative ResultSet baseline
- [ ] JFR / EXPLAIN artifacts
- [ ] Direct Writer comparison
- [ ] JSON aggregation strategy

## 📖 参考資料

- [Kotlin Sequence](https://kotlinlang.org/docs/sequences.html)
- [Java Stream API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/stream/Stream.html)
- [jOOQ Documentation](https://www.jooq.org/doc/latest/manual/)

## ⚠️ 注意事項

このプロジェクトは **実験・学習目的** です。本番環境での使用は想定していません。

- Admin API (`/admin/*`) は認証なし（実験用）
- エラーハンドリングは最小限
- セキュリティ対策は未実装
