# ベンチマーク測定ガイド

本ドキュメントでは、動的列CSV出力の4戦略（PRELOAD / MULTISET / SEQUENCE_WINDOW / SPLITERATOR_WINDOW）のベンチマーク測定手順を説明する。

## 測定設計の概要

### データセット（3条件）

| 条件 | N | A | 密度（ā） | V (属性値総数) | 検証目的 |
|------|---|---|----------|--------------|----------|
| **Baseline** | 30,000 | 15 | Dense (15) | 450,000 | 基準測定 |
| **Sparse** | 30,000 | 15 | Sparse (2) | 60,000 | メモリ差（V vs a_max） |
| **Wide** | 30,000 | 120 | Dense (120) | 3,600,000 | N·A 支配の検証 |

### 測定系統（2系統）

**系統1: Compute測定（主軸）**
- エンドポイント: `/api/export/orders/attributes/benchmark`
- 出力先: NullOutputStream + DigestOutputStream（MD5）
- 目的: I/O を除いた純粋な計算コスト（T_DB + T_app）を測定

**系統2: 実ファイル出力（参考）**
- エンドポイント: `/api/export/orders/attributes`
- 出力先: 実ファイル（CSV）
- 目的: I/O を含む現実の処理時間（T_DB + T_app + T_IO）を測定

### 測定方法

- **試行回数**: warmup 5回 + measurement 10回
- **指標**: median（中央値）、IQR（四分位範囲）、max（最大値）
- **順序バイアス除去**: ラウンドロビン実行（戦略順序を固定しない）

## ⚠️ 重要: メトリクス収集の注意点

### 問題: 一気に実行するとメトリクスが取得できない

Micrometer/Prometheus でメトリクス（JVM memory, GC pause 等）を収集するには：
1. Prometheusの **scrape interval**（デフォルト15秒）を考慮する必要がある
2. 各測定の**間に十分な待機時間**を設けないと、メトリクスが更新される前に次の測定が始まる
3. 特に **GC 統計**や**ヒープピーク値**は、測定中にscrapeされないと記録されない

### 対策

1. **各戦略の測定後に30秒待機** → Prometheusが少なくとも2回scrape
2. **warmup完了後に30秒待機** → JITコンパイル完了を確実にする
3. **各条件（Baseline/Sparse/Wide）間で手動実行** → メトリクスを確実に記録

## 測定手順（推奨）

### 事前準備

1. **アプリケーション起動確認**
```bash
curl http://localhost:8080/actuator/health
```

2. **Prometheus動作確認**
```bash
curl http://localhost:9090/-/healthy
```

3. **Grafanaダッシュボード準備**（オプション）
   - http://localhost:3300 にアクセス
   - JVM Memory / GC Dashboard を開く

---

## 条件1: Baseline（N=30k, A=15, Dense）

### Step 1: データ準備

```bash
# 既存データ削除
curl -X DELETE "http://localhost:8080/admin/truncate"

# Baseline データ生成（Dense, 30k orders, 15 attrs）
curl -X POST "http://localhost:8080/admin/seed?customers=6000&orders=30000&attrs=15"

# データ確認
curl "http://localhost:8080/admin/summary" | jq .
# => {"customers":6000,"orders":30000,"orderItems":~150000,"attributeDefinitions":15}
```

### Step 2: 測定実行

**⚠️ 重要**: 各条件で1回だけ実行し、**30分程度待機してからメトリクスを記録**

```bash
cd scripts/bench

# 系統1（Compute測定） + 系統2（実ファイル出力）を実行
ORDERS=30000 ATTRS=15 WARMUP=5 RUNS=10 CONDITION="baseline" ./run_benchmark.sh
```

実行内容：
- warmup 5回（各戦略をラウンドロビンで5ラウンド）
- measurement 10回（各戦略をラウンドロビンで10ラウンド）
- 各戦略測定後に30秒待機（Prometheusのscrapeを待つ）
- 結果は `docs/benchmark/runs/baseline_YYYYMMDD_HHMMSS/` に保存

### Step 3: メトリクス記録

測定完了後、**Prometheus/Grafana から以下を記録**：

**処理時間**（系統1: Compute）:
- `docs/benchmark/runs/baseline_*/compute_summary.txt` に自動記録済み

**メモリ使用量**（JVM Heap Peak）:
```promql
max_over_time(
  sum(jvm_memory_used_bytes{area="heap", job="simple-ec-backend"})[30m]
)
```

**GC統計**（GC Pause累積時間）:
```promql
increase(
  jvm_gc_pause_seconds_sum{job="simple-ec-backend"}[30m]
)
```

これらを手動でスプレッドシートや `docs/benchmark/results.md` に記録。

---

## 条件2: Sparse（N=30k, A=15, ā=2）

### Step 1: データ準備

```bash
# 既存データ削除
curl -X DELETE "http://localhost:8080/admin/truncate"

# Sparse データ生成（sparse=true, 30k orders, 15 attrs）
curl -X POST "http://localhost:8080/admin/seed?customers=6000&orders=30000&attrs=15&sparse=true"

# データ確認（attributeValuesCreated が約60,000になることを確認）
curl "http://localhost:8080/admin/summary" | jq .
# => {"customers":6000,"orders":30000,"attributeDefinitions":15}
# attributeValuesCreated: ~60,000（平均2個/注文）
```

### Step 2: 測定実行

```bash
cd scripts/bench

# 系統1 + 系統2 を実行
ORDERS=30000 ATTRS=15 WARMUP=5 RUNS=10 CONDITION="sparse" ./run_benchmark.sh
```

### Step 3: メトリクス記録

Baseline と同様に、Prometheus/Grafana からメトリクスを記録。

---

## 条件3: Wide（N=30k, A=120, Dense）

### Step 1: データ準備

```bash
# 既存データ削除
curl -X DELETE "http://localhost:8080/admin/truncate"

# Wide データ生成（Dense, 30k orders, 120 attrs）
curl -X POST "http://localhost:8080/admin/seed?customers=6000&orders=30000&attrs=120"

# データ確認
curl "http://localhost:8080/admin/summary" | jq .
# => {"customers":6000,"orders":30000,"attributeDefinitions":120}
# attributeValuesCreated: 3,600,000
```

### Step 2: 測定実行

```bash
cd scripts/bench

# 系統1 + 系統2 を実行
ORDERS=30000 ATTRS=120 WARMUP=5 RUNS=10 CONDITION="wide" ./run_benchmark.sh
```

### Step 3: メトリクス記録

Baseline と同様に、Prometheus/Grafana からメトリクスを記録。

---

## 結果の整理

### 系統1（Compute測定）の結果

各条件の `docs/benchmark/runs/{condition}_*/compute_summary.txt` に以下が記録される：

```
Strategy: preload
  Median: 940 ms
  IQR: 50 ms
  Max: 1020 ms
  MD5: e1df506292e0b9d83e533fcd9ee5d158

Strategy: multiset
  ...
```

### 系統2（実ファイル出力）の結果

`docs/benchmark/runs/{condition}_*/file_summary.txt` に記録される。

### メトリクス（手動記録）

スプレッドシートまたは `docs/benchmark/results.md` に以下を記録：

| Condition | Strategy | Median (ms) | Heap Peak (MB) | GC Pause (ms) |
|-----------|----------|-------------|----------------|---------------|
| Baseline | preload | 940 | 380 | 150 |
| Baseline | multiset | 2888 | 120 | 80 |
| ... | ... | ... | ... | ... |

---

## トラブルシューティング

### メトリクスが取得できない

**原因**: Prometheus scrape が間に合っていない

**対策**:
1. 測定後に十分な待機時間（30-60秒）を設ける
2. Prometheus の scrape interval を短くする（`prometheus.yml` で `scrape_interval: 5s` など）
3. 各条件を別々に実行し、条件間で十分な時間を空ける

### MD5ハッシュが一致しない

**原因**: データ順序やフォーマットの違い

**対策**:
1. データを再生成（truncate + seed）
2. 属性定義の順序を確認（ID昇順になっているか）
3. ログでエラーを確認

### メモリ不足（OOM）

**原因**: Wide条件でPRELOAD戦略がメモリを大量消費

**対策**:
1. これは想定内（Wide条件でPRELOADのメモリ限界を確認するのが目的）
2. `-Xmx512m` の制限を一時的に上げる（ただし記事では512MBでの結果を示す）
3. PRELOAD 以外の戦略でWide条件を測定

---

## 実験スケジュール例

| 日時 | 作業 | 所要時間 |
|------|------|---------|
| Day 1 | Baseline データ準備 + 測定 | 1時間 |
| Day 1 | Baseline メトリクス記録 | 30分 |
| Day 2 | Sparse データ準備 + 測定 | 1時間 |
| Day 2 | Sparse メトリクス記録 | 30分 |
| Day 3 | Wide データ準備 + 測定 | 1.5時間 |
| Day 3 | Wide メトリクス記録 | 30分 |
| Day 4 | 結果整理 + グラフ作成 | 2時間 |

**合計**: 約7時間（実測定時間 + メトリクス記録）

---

## 次のステップ

1. 測定結果を `docs/benchmark/results.md` にまとめる
2. グラフ作成（処理時間、メモリ、GC）
3. 考察を記述（理論モデルとの対応）
4. Qiita記事の「実測結果」章を執筆
