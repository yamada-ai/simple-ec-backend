# Simple EC Backend

Kotlin + Spring Boot で作る、Stream/Sequence パフォーマンス比較実験用の小規模 EC バックエンド。

## プロジェクト概要

このプロジェクトは、**Kotlin Sequence と Java Stream における「join問題」と「動的列ピボット」のパフォーマンス・設計比較**を目的とした実験リポジトリです。

### 本質的な課題

Stream/Sequenceは**join機構を持たない**。この制約下で以下の問題にどう対処するかを実験的に検証します：

1. **1→多のjoin問題**
   - Order → OrderItem のような親子関係を、SQLなしでどう結合するか
   - 全件取得 → Map化 → アプリ側join の是非

2. **動的列ピボット問題**（本プロジェクトのコア）
   - ユーザ定義の「注文属性」（Order Attributes）を列として動的にCSV出力したい
   - 例：`order_id, customer_name, ..., ギフト包装, 配送指示, キャンペーンID`
   - 属性は0〜10個でユーザが任意に追加可能
   - これを「行展開」ではなく「列展開」で出力する難しさ

### 検証テーマ

- Kotlin `Sequence` vs Java `Stream` (`flatMap`, `mapMulti`) vs カスタム `Spliterator`
- **動的カスタム項目のピボット処理**をストリーミングで実現できるか
- 遅延評価における "pull" (Sequence) vs "push" (Stream) の内部挙動
- メモリ効率・速度・GC挙動の比較

### なぜこの問題が難しいか

- **SQLでのjoin完結が困難**：列が動的なのでSQLのピボットでは対応しきれない
- **RDBは行方向が得意**：列を動的に増やす処理はDB側で完結させにくい
- **Stream/Sequenceにjoin不在**：標準では2つのストリームをキーでjoinできない

## 🧪 検証マトリクス

### Phase 1: 行展開パターン（Order → OrderItem）

| Strategy | 実装方法 | 評価型 | 中間オブジェクト | 期待されるメモリ効率 |
|----------|---------|--------|----------------|------------------|
| **Sequence** | `flatMap` on Kotlin Sequence | Pull (遅延) | 最小 | ⭐⭐⭐ |
| **Stream flatMap** | `flatMap` on Java Stream | Push (遅延) | 中間Stream生成 | ⭐⭐ |
| **Stream mapMulti** | `mapMulti` (Java 16+) | Push (遅延) | Consumer直接 | ⭐⭐⭐ |
| **Spliterator** | カスタムSpliterator | Pull (制御可能) | 手動管理 | ⭐⭐⭐ |

### Phase 2: 列展開パターン（動的Order Attributes）✅ 実装済み

| Strategy | 実装方法 | 理論モデル | メモリ効率 | 実測 (30k orders × 15 attrs, Dense) |
|----------|---------|------------|-----------|----------|
| **preload** | 全属性値をMapに格納 | M_app = O(V) | ⭐ (全データメモリ展開) | 0.41s ⚡ |
| **multiset** | jOOQのmultiset機能 | T_DB が異なる | ⭐⭐ (materialization) | 1.22s |
| **sequence-window** | Sequence + Windowing | M_app = O(a_max) | ⭐⭐⭐ (ストリーミング) | 0.80s ✅ 推奨 |
| **spliterator-window** | カスタムSpliterator + Windowing | M_app = O(a_max) | ⭐⭐⭐ (ストリーミング) | 0.74s |

**測定設計**（詳細は [Issue #18](https://github.com/yamada-ai/simple-ec-backend/issues/18)）:
- **データセット**: 3条件（Baseline: N=30k/A=15/Dense、Sparse: N=30k/A=15/ā=2、Wide: N=30k/A=120/Dense）
- **系統1（主）**: NullOutputStream + MD5（Compute測定、I/O除外）
- **系統2（参考）**: 実ファイル出力（I/O含む）
- **測定回数**: warmup 5回 + measurement 10回
- **指標**: median + IQR + max（p95は使わない）

### 検証環境条件

| 項目 | 値 | 備考 |
|------|-----|------|
| メモリ制限 | 512MB | `-Xmx512m` |
| 測定データセット | 3条件 | Baseline/Sparse/Wide（詳細は下記） |
| 測定系統 | 2系統 | Compute（主）+ 実ファイル出力（参考） |
| jOOQ fetchSize | 1,000 | カーソルチャンク |

**データセット詳細**:

| 条件 | N (orders) | A (attrs) | 密度（ā） | V (属性値総数) | 目的 |
|------|-----------|----------|----------|--------------|------|
| **Baseline** | 30,000 | 15 | Dense (15) | 450,000 | 基準測定 |
| **Sparse** | 30,000 | 15 | Sparse (2) | 60,000 | メモリ差検証 |
| **Wide** | 30,000 | 120 | Dense (120) | 3,600,000 | N·A支配検証 |

### 検証の焦点

- **理論モデルの検証**: N·A 支配、V（属性値総数）の影響、メモリ使用量（M_app = O(V) vs O(a_max)）
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

## 🗄 DB構成

### 基本エンティティ（DDD: 単数形）
- `customer`（顧客）
- `order`（注文）
- `order_item`（注文明細）→ 1対多のメイン検証対象

### 動的列ピボット用（本プロジェクトのコア）
- `order_attribute_definition`（注文属性定義）
  - ユーザが定義するカスタム項目（例：ギフト包装種別、配送指示、キャンペーンID）
- `order_attribute_value`（注文属性値）
  - 各注文に対する属性値のスパース行列
  - `(order_id, attribute_definition_id, value)` 構造
  - **これをCSV出力時に列方向にピボットする**のが本題

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

# 属性付き: 100 顧客、1000 注文、15 属性定義を生成（Phase 2 用）
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

### Phase 2: CSV エクスポート（動的属性列展開）✅ 実装済み

```bash
# Map 事前ロード版（最速だがメモリ消費大）
curl "http://localhost:8080/api/export/orders/attributes?strategy=preload" > orders_preload.csv

# jOOQ multiset 版
curl "http://localhost:8080/api/export/orders/attributes?strategy=multiset" > orders_multiset.csv

# Sequence + Windowing 版（推奨：メモリ効率とパフォーマンスのバランス）
curl "http://localhost:8080/api/export/orders/attributes?strategy=sequence-window" > orders_sequence.csv

# Spliterator + Windowing 版
curl "http://localhost:8080/api/export/orders/attributes?strategy=spliterator-window" > orders_spliterator.csv
```

### ベンチマーク実行（自動化スクリプト）

4戦略を一括実行してパフォーマンス比較（2系統測定）：

```bash
# Baseline条件: 30,000 注文 × 15 属性（Dense）
# warmup 5回 + measurement 10回
ORDERS=30000 ATTRS=15 WARMUP=5 RUNS=10 ./scripts/bench/run_benchmark.sh

# Sparse条件: 30,000 注文 × 15 属性（ā=2）
ORDERS=30000 ATTRS=15 SPARSE_MODE=true WARMUP=5 RUNS=10 ./scripts/bench/run_benchmark.sh

# Wide条件: 30,000 注文 × 120 属性（Dense）
ORDERS=30000 ATTRS=120 WARMUP=5 RUNS=10 ./scripts/bench/run_benchmark.sh

# 特定戦略のみ実行
STRATEGIES="preload sequence-window" ./scripts/bench/run_benchmark.sh

# データ投入をスキップ（2回目以降は自動判定）
SKIP_SEED=1 ./scripts/bench/run_benchmark.sh
```

**測定系統**:
- **系統1（主）**: `/api/export/orders/attributes/benchmark` でCompute測定（NullOutputStream + MD5）
- **系統2（参考）**: `/api/export/orders/attributes` で実ファイル出力（I/O含む）

結果は `docs/benchmark/runs/` に保存されます。詳細は [`scripts/bench/README.md`](scripts/bench/README.md) および [Issue #18](https://github.com/yamada-ai/simple-ec-backend/issues/18) を参照。

## プロジェクト構成

```
src/
├── main/
│   ├── kotlin/
│   │   └── com/example/simple_ec_backend/
│   │       ├── presentation/      # API層（OpenAPI生成）
│   │       ├── application/       # ユースケース層
│   │       ├── domain/            # ドメインモデル
│   │       ├── infrastructure/    # jOOQ, Repository実装
│   │       └── export/            # CSV エクスポート実装（実験対象）
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

### 完了 ✅

- [x] Admin API 実装（seed, truncate, summary）
- [x] Phase 1: CSV エクスポート（Order → OrderItem 行展開）
  - [x] Kotlin Sequence 版
  - [x] Java Stream (flatMap) 版
  - [x] Java Stream (mapMulti) 版
  - [x] カスタム Spliterator 版
- [x] Phase 2: CSV エクスポート（動的属性列展開）
  - [x] Map 事前ロード版（preload）
  - [x] jOOQ multiset 版
  - [x] Sequence + Windowing 版（sequence-window）
  - [x] Spliterator + Windowing 版（spliterator-window）
- [x] Order Attributes CRUD API（属性定義・属性値）
- [x] ベンチマーク自動化スクリプト（`scripts/bench/`）
- [x] パフォーマンス計測・比較（実測結果付き）

### 今後の拡張案

- [ ] Observability 強化（Prometheus/Grafana ダッシュボード）
- [ ] 他のエクスポート形式（JSON, Excel）
- [ ] フロントエンド（オプション）

## 📖 参考資料

- [Kotlin Sequence](https://kotlinlang.org/docs/sequences.html)
- [Java Stream API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/stream/Stream.html)
- [jOOQ Documentation](https://www.jooq.org/doc/latest/manual/)

## ⚠️ 注意事項

このプロジェクトは **実験・学習目的** です。本番環境での使用は想定していません。

- Admin API (`/admin/*`) は認証なし（実験用）
- エラーハンドリングは最小限
- セキュリティ対策は未実装
