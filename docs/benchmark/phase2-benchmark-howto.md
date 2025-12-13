# Phase 2 ベンチマーク実行手順

Phase 2 戦略（map-preload / multiset / sequence-window / spliterator-window）の性能検証を再現できるようにするための手順とスクリプトです。Grafana/Prometheus を併用する場合は、アプリ側の Actuator/Prometheus 有効化が前提です。

## 前提
- `docker compose up -d postgres` で DB が起動している（デフォルトポート 5433）。
- メトリクスを取りたい場合は `docker compose up -d prometheus grafana` も起動する（後述）。
- アプリを起動しておく（例：`JAVA_TOOL_OPTIONS="-Xms512m -Xmx512m -Xlog:gc*:file=logs/gc.log:time,uptime" ./gradlew bootRun`）。
- base URL はデフォルト `http://localhost:8080`。変更する場合は `BASE_URL` を指定。

## データセット投入
ベンチマーク用に orders / attributes / attribute values を一括生成します。

```bash
# 例: 10,000注文、属性15件、シード0.5
./scripts/bench/seed_benchmark_dataset.sh 10000 15 0.5

# 環境変数で細かく指定する場合
ORDERS=5000 ATTRS=15 SEED=0.42 DB_HOST=localhost DB_PORT=5433 ./scripts/bench/seed_benchmark_dataset.sh
```

- `scripts/bench/seed_benchmark_dataset.sql` を psql で実行します。
- 既存データは `TRUNCATE ... RESTART IDENTITY CASCADE` でリセットされます。

## ベンチマーク実行
ウォームアップ＋本計測をまとめて実行し、結果をディレクトリに保存します。

```bash
# 例: デフォルト（orders=5000, attrs=15, strategies=4種）
./scripts/bench/run_benchmark.sh

# 例: 30,000件、ウォームアップ1回/本番3回、出力先を変更
ORDERS=30000 ATTRS=15 WARMUP_RUNS=1 MEASURE_RUNS=3 OUT_ROOT=docs/benchmark/runs ./scripts/bench/run_benchmark.sh

# 例: 特定戦略のみ
STRATEGIES="multiset sequence-window" ./scripts/bench/run_benchmark.sh
```

- デフォルトではシード投入も自動実行します（`SKIP_SEED=1` でスキップ可）。
- 各戦略ごとに `warmupN` + `runN` を実行し、結果を `docs/benchmark/runs/<timestamp>_<orders>orders_<attrs>attrs/` 以下に保存します。
  - `artifacts/*.csv` : 出力CSV本体
  - `*.md5` : CSVのMD5ハッシュ（整合性確認用）
  - `*.json` : `curl -w` で取得した time_total / size_download / http_code などの実測
  - `*-http-metrics.json` / `*-memory.json` / `*-gc.json` : Actuator経由のメトリクス（`METRICS_BASE` を指定した場合）

### Actuator / Prometheus メトリクスを同時に取る場合
- アプリは Actuator + Prometheus registry を有効化済み（/actuator/prometheus）。
- `docker compose up -d prometheus grafana` でメトリクス基盤を起動。
  - Prometheus が `http://host.docker.internal:8080/actuator/prometheus` をスクレイプ。
  - Grafana (http://localhost:3000, admin/admin) に Prometheus データソースを自動登録。
- スクリプトからも Actuator JSON を保存したい場合は `METRICS_BASE=http://localhost:8080/actuator ./scripts/bench/run_benchmark.sh`
  - 取得するメトリクス:
    - `http.server.requests`（URI=/api/export/orders/attributes, method=GET）
    - `jvm.memory.used`
    - `jvm.gc.pause`

## 記録と整理
- 実験1セットごとに `OUT_ROOT` 配下にディレクトリが作られます。`RUN_LABEL` を指定すると任意の名前でまとめられます。
  - 例: `RUN_LABEL=20241208-seq-vs-split ./scripts/bench/run_benchmark.sh`
- 各戦略の CSV の MD5 が一致していることを確認すれば、出力整合性チェックを自動化できます。
- GCログを取得する場合は、アプリ起動時に `JAVA_TOOL_OPTIONS` で `-Xlog:gc*:file=logs/gc.log:time,uptime` 等を設定してください。

## 注意
- 大量データ（30,000 orders × 15 attrs）では DB 生成も時間がかかります。`seed_benchmark_dataset.sql` は psql のセットシードを利用し、再現性を持たせています。
- ベンチマーク実行前にアプリを再起動してヒープをリセットしておくと測定が安定します。
