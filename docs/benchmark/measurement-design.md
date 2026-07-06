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
  explain/
    *.sql
    *.explain.json
    *.explain.txt
  jfr/
    *.jfr
    *-summary.txt
```

### `samples.jsonl`

1行1sampleの処理時間記録。`elapsedMs` と `md5` は benchmark API の返却値を使う。

`benchmarkMode=csv` では、row source 取得、横展開、CSV writer、MD5 計算までを含めた通常のエクスポート時間を測る。

`benchmarkMode=rows` では、schema loading と row source の消費だけを行い、CSV writer と MD5 計算を通さない。これは $T_{\text{app}}$ をさらに「取得・mapping側」と「CSV出力側」に分けるための補助測定である。`rowCount`, `attributeValueCount`, `orderIdChecksum` は、rows mode が結果を最後まで消費したことを確認するための軽量な証跡として保存する。

### `prometheus/range/*.json`

Prometheus HTTP API の `/api/v1/query_range` の raw response を保存する。あとから PromQL や step の影響を確認できるよう、整形済みの数値だけでなく raw JSON を残す。

### `explain/*.explain.json`

`EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)` の出力。DB側の実行時間、actual rows、buffers、sort spill などを見る。

これはCSV writerやjOOQ mappingを含まない。したがって、benchmark API の `csv` / `rows` elapsed と組み合わせて、$T_{\text{DB}}$ と $T_{\text{app}}$ の切り分けに使う。

現状の `collect_explain.py` は、`vertical-join`, `sql-pivot`, `json-aggregation`, `preload-base`, `preload-attributes` のSQL形状を保存する。jOOQ `MULTISET` が生成するSQL/JSONエミュレーションの完全な保存は未実装である。

## Run Type

| Run type | 主目的 | 信頼する指標 | 注意 |
| --- | --- | --- | --- |
| `round-robin` | 処理時間比較 | elapsed median/IQR, MD5 | heap / GC は複数戦略が混ざる |
| `isolated` | resource profile | heap peak, GC pause, allocation | P1 で実装予定 |
| `file-output` | 実 I/O 込み確認 | wall time, bytes, MD5 | ディスク状態に左右される |
| `explain-only` | DB 実行形状 | plan, buffers, actual rows | JVM/CSV処理は含まない |
| `jfr-attached` | JVM allocation/profile | JFR events, jfr summary | sampleごとの記録サイズが増える |

現状は `round-robin` の artifact 保存、`csv` / `rows` の benchmark mode、measurement sample への JFR 添付を実装している。`isolated` と `explain-only` は未実装である。

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

CSV writer を外して row source drain のみを測る場合:

```bash
python3 scripts/bench/run_benchmark.py \
  --condition baseline-rows \
  --benchmark-mode rows \
  --orders 30000 \
  --attrs 15 \
  --warmup 5 \
  --runs 10
```

JFR を measurement sample ごとに保存する場合:

```bash
python3 scripts/bench/run_benchmark.py \
  --condition baseline-jfr \
  --orders 30000 \
  --attrs 15 \
  --warmup 5 \
  --runs 3 \
  --jfr
```

SQL実行計画を保存する場合:

```bash
python3 scripts/bench/collect_explain.py \
  --run-dir docs/benchmark/runs/<run-id>
```

## Next

- P1: `isolated` mode を追加し、strategy ごとに JVM/container を再起動して heap / GC / allocation の帰属を明確にする。
- P1: jOOQ `MULTISET` が生成するSQL/JSONエミュレーションの `EXPLAIN` 保存方法を決める。
- P1: JFR summary から allocation profile の主要イベントを抽出し、summary に載せる。
- P2: GC log を run artifact に紐づける。
