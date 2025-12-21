# ベンチマークスクリプト

動的列CSV出力の4戦略（PRELOAD / MULTISET / SEQUENCE_WINDOW / SPLITERATOR_WINDOW）のベンチマーク測定スクリプト。

## スクリプト

### `run_benchmark.sh`

2系統測定（Compute主軸 + 実ファイル参考）を実行し、median/IQR/max を自動計算する。

**主な機能**:
- ラウンドロビン実行（順序バイアス除去）
- Prometheusのscrapeを待つ待機時間（デフォルト30秒）
- MD5ハッシュ検証
- Summary統計の自動計算

### `seed_benchmark_dataset.sh`

ベンチマーク用データを生成する（オプション、手動seed推奨）。

---

## 使い方

### 基本的な使い方

```bash
# データを手動で準備
curl -X DELETE "http://localhost:8080/admin/truncate"
curl -X POST "http://localhost:8080/admin/seed?customers=6000&orders=30000&attrs=15"

# ベンチマーク実行
cd scripts/bench
ORDERS=30000 ATTRS=15 CONDITION="baseline" ./run_benchmark.sh
```

### 環境変数

| 変数 | デフォルト | 説明 |
|------|-----------|------|
| `BASE_URL` | `http://localhost:8080` | APIのベースURL |
| `ORDERS` | `5000` | 注文数 |
| `ATTRS` | `15` | 属性定義数 |
| `SPARSE_MODE` | `false` | Sparseモード（データ準備時の参考情報） |
| `WARMUP` | `5` | warmup回数 |
| `RUNS` | `10` | 測定回数 |
| `CONDITION` | `default` | 測定条件名（出力ディレクトリ名に使用） |
| `STRATEGIES` | `preload multiset sequence-window spliterator-window` | 測定する戦略 |
| `SKIP_SEED` | `auto` | データ準備をスキップ（`auto` / `0` / `1`） |
| `METRICS_WAIT` | `30` | 各測定後の待機時間（秒） |

---

## 実験実施例

### 条件1: Baseline（N=30k, A=15, Dense）

```bash
# データ準備
curl -X DELETE "http://localhost:8080/admin/truncate"
curl -X POST "http://localhost:8080/admin/seed?customers=6000&orders=30000&attrs=15"

# ベンチマーク実行
cd scripts/bench
ORDERS=30000 ATTRS=15 WARMUP=5 RUNS=10 CONDITION="baseline" ./run_benchmark.sh
```

### 条件2: Sparse（N=30k, A=15, ā=2）

```bash
# データ準備
curl -X DELETE "http://localhost:8080/admin/truncate"
curl -X POST "http://localhost:8080/admin/seed?customers=6000&orders=30000&attrs=15&sparse=true"

# ベンチマーク実行
cd scripts/bench
ORDERS=30000 ATTRS=15 SPARSE_MODE=true WARMUP=5 RUNS=10 CONDITION="sparse" ./run_benchmark.sh
```

### 条件3: Wide（N=30k, A=120, Dense）

```bash
# データ準備
curl -X DELETE "http://localhost:8080/admin/truncate"
curl -X POST "http://localhost:8080/admin/seed?customers=6000&orders=30000&attrs=120"

# ベンチマーク実行
cd scripts/bench
ORDERS=30000 ATTRS=120 WARMUP=5 RUNS=10 CONDITION="wide" ./run_benchmark.sh
```

---

## 出力ファイル

### ディレクトリ構造

```
docs/benchmark/runs/{condition}_{timestamp}/
├── compute/                          # 系統1: Compute測定
│   ├── preload-warmup1.json
│   ├── preload-run1.json
│   ├── ...
│   ├── sequence-window-run10.json
│   └── ...
├── file/                             # 系統2: 実ファイル出力（5回に1回）
│   ├── preload-run1.csv
│   ├── preload-run1.md5
│   └── ...
└── summary.txt                       # Summary統計
```

### summary.txt の例

```
Benchmark Summary: baseline
Date: 2025-12-21 17:00:00

--- preload ---
  Samples: 10
  Median: 940 ms
  IQR: 50 ms (Q1=920, Q3=970)
  Min: 900 ms, Max: 1020 ms
  MD5: e1df506292e0b9d83e533fcd9ee5d158

--- multiset ---
  Samples: 10
  Median: 2888 ms
  IQR: 120 ms (Q1=2850, Q3=2970)
  Min: 2800 ms, Max: 3050 ms
  MD5: e1df506292e0b9d83e533fcd9ee5d158

...
```

---

## 測定の流れ

1. **Warmup Phase** (5 rounds)
   - 各ラウンドでラウンドロビンで全戦略を実行
   - 系統1（Compute）のみ
   - 各測定後に5秒待機

2. **Warmup完了後に30秒待機** → JITコンパイル完了を確実にする

3. **Measurement Phase** (10 runs)
   - 各ランでラウンドロビンで全戦略を実行
   - 系統1（Compute）: 毎回実行
   - 系統2（実ファイル）: 5回に1回（run1, run6）
   - 各測定後に30秒待機 → Prometheusのscrapeを待つ

4. **Summary統計の自動計算**
   - median, IQR, min, max を計算
   - MD5ハッシュを検証
   - `summary.txt` に出力

---

## ⚠️ 重要: メトリクス収集

スクリプト完了後、**30-60秒待機してから** Prometheus/Grafana でメトリクスを記録してください。

### 記録すべきメトリクス

**1. JVM Heap Peak**（各戦略のメモリ使用量ピーク）:
```promql
max_over_time(
  sum(jvm_memory_used_bytes{area="heap", job="simple-ec-backend"})[30m]
)
```

**2. GC Pause Total**（GC停止時間の累積）:
```promql
increase(
  jvm_gc_pause_seconds_sum{job="simple-ec-backend"}[30m]
)
```

**3. GC Allocation Rate**（アロケーション速度、取得できれば）:
```promql
rate(
  jvm_gc_memory_allocated_bytes_total{job="simple-ec-backend"}[5m]
)
```

### 詳細な手順

測定ガイドを参照してください:
- [`docs/benchmark/measurement-guide.md`](../../docs/benchmark/measurement-guide.md)

---

## トラブルシューティング

### データが一致しない（SKIP_SEED=auto でエラー）

```bash
# 手動でデータを準備してから再実行
curl -X DELETE "http://localhost:8080/admin/truncate"
curl -X POST "http://localhost:8080/admin/seed?customers=6000&orders=30000&attrs=15"

# SKIP_SEED=1 で強制スキップ
SKIP_SEED=1 ./run_benchmark.sh
```

### MD5ハッシュが一致しない

データの順序やフォーマットの問題。データを再生成してください。

### 特定の戦略のみ実行したい

```bash
STRATEGIES="preload sequence-window" ./run_benchmark.sh
```

### 待機時間を調整したい

```bash
# Prometheusのscrapeを待つ時間を60秒に
METRICS_WAIT=60 ./run_benchmark.sh

# warmup中の待機も含めて短くしたい（非推奨）
METRICS_WAIT=10 ./run_benchmark.sh
```

---

## 次のステップ

1. 3条件（Baseline / Sparse / Wide）をそれぞれ測定
2. 各条件でメトリクスを記録
3. 結果を `docs/benchmark/results.md` にまとめる
4. グラフを作成（処理時間、メモリ、GC）
5. 考察を記述（理論モデルとの対応）
