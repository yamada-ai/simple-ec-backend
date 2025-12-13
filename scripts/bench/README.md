# Phase 2 ベンチマーク実行スクリプト

Phase 2 戦略（preload / multiset / sequence-window / spliterator-window）の性能検証を再現できるようにするためのスクリプト群です。

## 変更履歴

**v2.0 (API版)** - Admin API経由でデータ投入
- `seed_benchmark_dataset.sh`: Admin API (`/admin/seed`) を使用
- `seed_benchmark_dataset.sql`: 削除（API版では不要）
- **メリット**:
  - DB接続情報不要（アプリURLだけでOK）
  - トランザクション管理がアプリ側で適切に処理される
  - ドメインモデルのバリデーションが効く
  - 同じロジックで生成（再現性保証）

**v1.0 (SQL版)** - PostgreSQL直接接続（deprecated）
- psqlコマンドでSQLを直接実行
- DB接続情報（host/port/user/password）が必要

---

## 前提条件

- アプリケーションが起動していること（デフォルト: `http://localhost:8080`）
- メトリクスを取りたい場合は Actuator/Prometheus を有効化
  - `docker compose up -d prometheus grafana`（オプション）

---

## 1. データセット投入

ベンチマーク用に customers / orders / attributes / attribute values を一括生成します。

### 基本的な使い方

```bash
# 例1: デフォルト（5,000注文、15属性、seed=42）
./scripts/bench/seed_benchmark_dataset.sh

# 例2: 引数で指定
./scripts/bench/seed_benchmark_dataset.sh 10000 15 100

# 例3: 環境変数で指定
ORDERS=30000 ATTRS=15 SEED=999 ./scripts/bench/seed_benchmark_dataset.sh
```

### パラメータ

| パラメータ | デフォルト | 説明 |
|-----------|----------|------|
| `ORDERS` | 5000 | 生成する注文数 |
| `ATTRS` | 15 | 生成する属性定義数 |
| `SEED` | 42 | 乱数シード（再現性のため） |
| `BASE_URL` | http://localhost:8080 | アプリケーションのURL |

### 生成されるデータ

- **Customers**: `ORDERS / 5`（最低1件）
- **Orders**: `ORDERS`
- **Order Items**: 注文ごとに3〜7個（ランダム）
- **Attribute Definitions**: `ATTRS`
  - `name`: `attr_1`, `attr_2`, ...
  - `label`: `属性1`, `属性2`, ...
- **Attribute Values**: `ORDERS × ATTRS`
  - 形式: `v{defId}_{orderId % 10}`（決定的生成）

### 内部動作

1. **Truncate**: `/admin/truncate` で既存データを削除
2. **Seed**: `/admin/seed?customers=...&orders=...&attrs=...&seed=...` で新規データ生成

---

## 2. ベンチマーク実行

ウォームアップ＋本計測をまとめて実行し、結果をディレクトリに保存します。

### 基本的な使い方

```bash
# 例1: デフォルト（5,000注文、15属性、4戦略）
./scripts/bench/run_benchmark.sh

# 例2: 大量データでOOMテスト
ORDERS=30000 ATTRS=15 ./scripts/bench/run_benchmark.sh

# 例3: 特定戦略のみ
STRATEGIES="multiset sequence-window" ./scripts/bench/run_benchmark.sh

# 例4: データ投入をスキップ（既にデータがある場合）
SKIP_SEED=1 ./scripts/bench/run_benchmark.sh

# 例5: 強制的にデータ再投入
SKIP_SEED=0 ./scripts/bench/run_benchmark.sh

# 例6: 自動判定（デフォルト）
# データが既に期待件数存在すればスキップ、なければseed実行
./scripts/bench/run_benchmark.sh
```

### パラメータ

| パラメータ | デフォルト | 説明 |
|-----------|----------|------|
| `BASE_URL` | http://localhost:8080 | アプリケーションのURL |
| `ORDERS` | 5000 | 注文数（seed時に使用） |
| `ATTRS` | 15 | 属性定義数（seed時に使用） |
| `WARMUP_RUNS` | 2 | ウォームアップ回数 |
| `MEASURE_RUNS` | 3 | 本計測回数 |
| `STRATEGIES` | preload multiset sequence-window spliterator-window | 実行する戦略 |
| `OUT_ROOT` | docs/benchmark/runs | 結果の保存先ルート |
| `RUN_LABEL` | YYYYmmdd-HHMMSS | 実行ラベル |
| `SKIP_SEED` | auto | auto=自動判定, 0=必ずseed, 1=必ずスキップ |
| `METRICS_BASE` | (空) | Actuatorメトリクス取得URL（例: http://localhost:8080/actuator） |

### 出力ディレクトリ構造

```
docs/benchmark/runs/
└── 20241210-153045_5000orders_15attrs/
    ├── artifacts/
    │   ├── preload-warmup1.csv
    │   ├── preload-warmup2.csv
    │   ├── preload-run1.csv
    │   ├── preload-run2.csv
    │   ├── preload-run3.csv
    │   └── ... (他の戦略も同様)
    ├── preload-run1.json          # curl -w で取得したメタデータ
    ├── preload-run1.md5           # CSV の MD5 ハッシュ
    ├── preload-run1-http-metrics.json  # (METRICS_BASE指定時)
    ├── preload-run1-memory.json        # (METRICS_BASE指定時)
    └── preload-run1-gc.json            # (METRICS_BASE指定時)
```

### メトリクス取得（オプション）

Actuator経由でメトリクスを取得する場合：

```bash
METRICS_BASE=http://localhost:8080/actuator ./scripts/bench/run_benchmark.sh
```

取得するメトリクス：
- `http.server.requests`（URI=/api/export/orders/attributes, method=GET）
- `jvm.memory.used`
- `jvm.gc.pause`

### Grafana/Prometheus併用

```bash
# 1. メトリクス基盤を起動
docker compose up -d prometheus grafana

# 2. アプリを起動（GCログ有効化）
JAVA_TOOL_OPTIONS="-Xms512m -Xmx512m -Xlog:gc*:file=logs/gc.log:time,uptime" \
  ./gradlew bootRun

# 3. ベンチマーク実行
METRICS_BASE=http://localhost:8080/actuator ./scripts/bench/run_benchmark.sh

# 4. Grafanaで可視化
# http://localhost:3000 (admin/admin)
```

---

## 3. 結果の検証

### CSV整合性の確認

全戦略で同じCSVが出力されることを確認：

```bash
cd docs/benchmark/runs/20241210-153045_5000orders_15attrs/

# 各戦略のrun1のMD5を比較
cat preload-run1.md5
cat multiset-run1.md5
cat sequence-window-run1.md5
cat spliterator-window-run1.md5
```

すべて同じMD5ハッシュ値であればOK ✅

### 処理時間の比較

```bash
# run1のtime_totalを抽出
jq '.time_total' *-run1.json
```

### メモリ使用量の比較

```bash
# メモリメトリクスを抽出
jq '.measurements[0].value' *-run1-memory.json
```

---

## 注意事項

1. **大量データ（30,000 orders × 15 attrs）**:
   - データ生成に時間がかかる（API経由で数分）
   - preload戦略でOOM発生想定（`-Xmx512m`推奨）

2. **ベンチマーク実行前の準備**:
   - アプリを再起動してヒープをリセット
   - 他のプロセスを停止して測定を安定化

3. **再現性**:
   - 同じ`SEED`を使えば同じデータが生成される
   - 同じデータで同じCSVが出力されることを確認

---

## トラブルシューティング

### データ投入が失敗する

```bash
# アプリが起動しているか確認
curl http://localhost:8080/admin/summary

# エラーメッセージを確認
./scripts/bench/seed_benchmark_dataset.sh 2>&1 | tee seed.log
```

### ベンチマーク実行が失敗する

```bash
# 手動でAPIを呼び出してみる
curl "http://localhost:8080/api/export/orders/attributes?strategy=sequence-window" \
  -o test.csv

# レスポンスコードを確認
curl -w "%{http_code}\n" -o /dev/null \
  "http://localhost:8080/api/export/orders/attributes?strategy=sequence-window"
```

### メトリクスが取得できない

```bash
# Actuatorが有効か確認
curl http://localhost:8080/actuator

# Prometheusエンドポイントが有効か確認
curl http://localhost:8080/actuator/prometheus | head -20
```
