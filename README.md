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

## 技術スタック

- **Kotlin** 1.9.25
- **Spring Boot** 3.5.x
- **PostgreSQL** 17
- **jOOQ** 3.19.x (型安全 SQL)
- **Flyway** (DB マイグレーション)
- **Kotest** (テスト)
- **Detekt** (静的解析)
- **OpenAPI Generator** (API スキーマ駆動開発)

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
# 100 顧客、1000 注文、約 5000 明細を生成
curl -X POST "http://localhost:8080/admin/seed?customers=100&orders=1000"
```

### データ削除

```bash
curl -X DELETE "http://localhost:8080/admin/truncate"
```

## 実験用 API

### CSV エクスポート（メインの実験対象）

```bash
# Kotlin Sequence 版
curl "http://localhost:8080/api/v1/export/orders?strategy=sequence" > orders_sequence.csv

# Java Stream (flatMap) 版
curl "http://localhost:8080/api/v1/export/orders?strategy=stream-flatmap" > orders_flatmap.csv

# Java Stream (mapMulti) 版
curl "http://localhost:8080/api/v1/export/orders?strategy=stream-mapmulti" > orders_mapmulti.csv

# カスタム Spliterator 版
curl "http://localhost:8080/api/v1/export/orders?strategy=spliterator" > orders_spliterator.csv
```

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

## 📝 TODO

- [ ] Admin API 実装（データ投入・削除）
- [ ] CRUD API 実装（Customer, Order, OrderItem）
- [ ] CSV エクスポート実装
  - [ ] Kotlin Sequence 版
  - [ ] Java Stream (flatMap) 版
  - [ ] Java Stream (mapMulti) 版
  - [ ] カスタム Spliterator 版
- [ ] パフォーマンス計測用テスト
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
