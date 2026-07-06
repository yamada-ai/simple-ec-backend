# Benchmark Scripts

1対多データの横持ち CSV エクスポート戦略を測定するためのスクリプト群。

発表・記事で使う正の記録は、Grafana スクショではなく `docs/benchmark/runs/<run-id>/` の artifact とする。

## Scripts

### `run_benchmark.py`

推奨 runner。

現行の全戦略を round-robin で実行し、benchmark API の JSON と Prometheus range query を run artifact として保存する。

保存先:

```text
docs/benchmark/runs/<run-id>/
  run.json
  environment.json
  samples.json
  samples.jsonl
  samples.csv
  summary.json
  summary.md
  prometheus/
    queries.json
    range/*.json
```

処理時間の正は benchmark API が返す `elapsedMs` と `md5`。

Prometheus は heap / GC / allocation / CPU の補助時系列として使う。

### `seed_benchmark_dataset.sh`

Admin API 経由で benchmark 用データを投入する薄い wrapper。

`N=30,000, A=15` 程度の baseline では使えるが、`N=30,000, A=120` の Wide dense では seed 処理自体が JVM heap を圧迫するため使わない。

### `seed_benchmark_dataset_sql.sh`

PostgreSQL の `generate_series` で benchmark 用データを直接投入する wrapper。

Wide dense のように $V$ が大きい条件ではこちらを使う。アプリケーションの Admin API を経由しないため、seed 処理が backend JVM heap を消費しにくい。

```bash
ORDERS=30000 ATTRS=120 SEED=20260705 \
  ./scripts/bench/seed_benchmark_dataset_sql.sh
```

生成される属性値は Admin API seed と同じく `v{definitionId}_{orderId % 10}` 形式である。

### `charts.py`

run artifact から発表・記事用の静的チャートを生成する。

ホストの Python 環境には依存せず、Docker Compose の `bench-charts` service から実行する。

```bash
docker compose run --rm bench-charts \
  --run-dir docs/benchmark/runs/<run-id>
```

出力先:

```text
docs/benchmark/runs/<run-id>/charts/
  elapsed-median-iqr.svg
  elapsed-median-iqr.png
  elapsed-samples.svg
  elapsed-samples.png
  heap-used-over-time.svg
  gc-pause-over-time.svg
  allocation-rate-over-time.svg
  process-cpu-over-time.svg
```

SVG は登壇資料や記事での再利用、PNG はプレビュー用途を想定する。

### `run_benchmark.sh`

旧 runner。

互換用に残している。再分析可能な artifact を残す用途では `run_benchmark.py` を使う。

## Basic Usage

Backend と Prometheus を起動してから実行する。

```bash
docker compose up -d backend prometheus
```

データ投入:

```bash
ORDERS=30000 ATTRS=15 SEED=42 ./scripts/bench/seed_benchmark_dataset.sh
```

Sparse データ投入:

```bash
SPARSE=true ORDERS=30000 ATTRS=15 SEED=42 ./scripts/bench/seed_benchmark_dataset.sh
```

Wide dense のデータ投入:

```bash
ORDERS=30000 ATTRS=120 SEED=42 ./scripts/bench/seed_benchmark_dataset_sql.sh
```

Benchmark:

```bash
python3 scripts/bench/run_benchmark.py \
  --condition baseline \
  --mode round-robin \
  --orders 30000 \
  --attrs 15 \
  --warmup 5 \
  --runs 10
```

特定戦略だけ測る:

```bash
python3 scripts/bench/run_benchmark.py \
  --condition baseline-window-only \
  --orders 30000 \
  --attrs 15 \
  --warmup 5 \
  --runs 10 \
  --strategies sequence-window,spliterator-window
```

データ件数チェックをスキップする:

```bash
python3 scripts/bench/run_benchmark.py \
  --condition local-debug \
  --skip-data-check
```

## Recommended Datasets

| Condition | $N$ orders | $A$ attrs | Density | $V$ values | Purpose |
| --- | ---: | ---: | --- | ---: | --- |
| baseline | 30,000 | 15 | dense | 450,000 | 基準測定 |
| sparse | 30,000 | 15 | average 2 attrs/order | 60,000 | sparse input と dense output の差 |
| wide | 30,000 | 120 | dense | 3,600,000 | $N \cdot A$ 支配と writer/allocation の確認 |

## Runner Options

| Option | Default | Meaning |
| --- | --- | --- |
| `--base-url` | `http://localhost:8080` | Backend URL |
| `--prometheus-url` | `http://localhost:9090` | Prometheus URL |
| `--condition` | `default` | Run condition name |
| `--mode` | `round-robin` | Currently only `round-robin` is implemented |
| `--orders` | `30000` | Expected order count |
| `--attrs` | `15` | Expected attribute definition count |
| `--warmup` | `5` | Warmup rounds |
| `--runs` | `10` | Measurement rounds |
| `--strategies` | all current strategies | Comma or space separated strategy names |
| `--metrics-wait` | `30` | Wait after run before Prometheus collection |
| `--sample-wait` | `0` | Wait between samples |
| `--skip-data-check` | false | Skip `/admin/summary` validation |
| `--run-id` | generated | Explicit run id |

## Reading Results

Start with:

```text
docs/benchmark/runs/<run-id>/summary.md
```

Then inspect:

```text
samples.jsonl
prometheus/range/*.json
environment.json
```

`samples.jsonl` is the source of truth for elapsed time and MD5 equality.

Prometheus files are useful for run-wide resource interpretation. In round-robin mode, heap / GC / allocation are not strictly attributable to a single strategy.

Charts can be regenerated from the same run directory:

```bash
docker compose run --rm bench-charts \
  --run-dir docs/benchmark/runs/<run-id>
```

## Run Type Policy

| Run type | Status | Main use |
| --- | --- | --- |
| smoke | available | runner and MD5 sanity check |
| round-robin | available | elapsed median / IQR comparison |
| isolated | planned | strategy-specific heap / GC / allocation |
| explain-only | planned | DB execution plan and buffers |
| file-output | planned | real disk I/O reference |

See [docs/benchmark/measurement-design.md](../../docs/benchmark/measurement-design.md) for the full measurement design.
