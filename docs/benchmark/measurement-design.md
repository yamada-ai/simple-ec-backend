# Benchmark Measurement Design

このドキュメントは、Grafana スクショ運用をやめ、benchmark run の一次データを保存するための設計メモである。

## 方針

- 処理時間の正は benchmark API の JSON とする。
- Prometheus は heap / GC / allocation / CPU の補助時系列として保存する。
- Grafana は観察用であり、発表・記事に使う図表は `docs/benchmark/runs/<run-id>/` の artifact から再生成する。
- round-robin run と isolated run は目的を分ける。

## Run Artifact

新しい runner は以下を保存する。

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

### `samples.jsonl`

1行1sampleの処理時間記録。`elapsedMs` と `md5` は benchmark API の返却値を使う。

### `prometheus/range/*.json`

Prometheus HTTP API の `/api/v1/query_range` の raw response を保存する。あとから PromQL や step の影響を確認できるよう、整形済みの数値だけでなく raw JSON を残す。

## Run Type

| Run type | 主目的 | 信頼する指標 | 注意 |
| --- | --- | --- | --- |
| `round-robin` | 処理時間比較 | elapsed median/IQR, MD5 | heap / GC は複数戦略が混ざる |
| `isolated` | resource profile | heap peak, GC pause, allocation | P1 で実装予定 |
| `file-output` | 実 I/O 込み確認 | wall time, bytes, MD5 | ディスク状態に左右される |
| `explain-only` | DB 実行形状 | plan, buffers, actual rows | JVM/CSV処理は含まない |

P0 では `round-robin` の artifact 保存のみ実装する。

## Prometheus Metrics

P0 runner は以下を `query_range` で保存する。

- `heap-used-bytes`
- `heap-committed-bytes`
- `heap-max-bytes`
- `gc-pause-seconds-sum`
- `gc-pause-count`
- `allocation-bytes-total`
- `process-cpu-usage`
- `system-cpu-usage`
- `hikaricp-connections-active`
- `http-latency-p50`
- `http-latency-p95`

`http.server.requests` は query parameter の `strategy` を tag にしないため、strategy 別の処理時間の正には使わない。strategy 別 elapsed は `samples.jsonl` を使う。

## Usage

事前に backend と Prometheus を起動し、データセットを投入しておく。

```bash
python3 scripts/bench/run_benchmark.py \
  --condition baseline \
  --mode round-robin \
  --orders 30000 \
  --attrs 15 \
  --warmup 5 \
  --runs 10
```

データ件数チェックをスキップする場合:

```bash
python3 scripts/bench/run_benchmark.py --skip-data-check
```

## Next

- P1: `isolated` mode を追加し、strategy ごとに JVM/container を再起動して heap / GC / allocation の帰属を明確にする。
- P1: chart 生成を追加する。
- P2: JFR / GC log / `EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)` を run artifact に紐づける。
