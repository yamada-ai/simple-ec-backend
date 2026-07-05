# Benchmark Results（測定結果の索引）

このファイルは、発表・記事で使う benchmark 結果の索引である。

Raw artifacts は `docs/benchmark/runs/<run-id>/` に保存する。

## 解釈方針（Interpretation Policy）

このリポジトリの benchmark は、絶対性能ランキングを作るためのものではない。

目的は、同じ「1対多データの横持ち CSV エクスポート」に対して、各戦略がどこにコストを移動するかを見ることである。

| Run type | 主な問い |
| --- | --- |
| smoke | runner, MD5, artifact 保存が動くか |
| round-robin | elapsed median / IQR の比較 |
| isolated | heap / GC / allocation の strategy 別帰属 |
| explain-only | DB 実行計画、buffers、actual rows |
| file-output | 実ディスク I/O を含めた参考値 |

## Smoke Run（動作確認）

この run は正式な性能測定結果ではない。

artifact runner が現行の全戦略を実行でき、出力の同一性を検証できることだけを確認するものである。

実行ディレクトリ:

```text
docs/benchmark/runs/smoke_100x5_20260705-144001/
```

データセット:

| Variable | Value |
| --- | ---: |
| $N$ | 100 |
| $A$ | 5 |
| $B$ | 5 |
| $S = N (A + B)$ | 1,000 cells |
| warmup | 1 |
| measurement | 2 |

処理時間:

| Strategy | Samples | Median ms | IQR ms | Min ms | Max ms | MD5 matched |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| multiset | 2 | 36 | 10 | 31 | 41 | yes |
| preload | 2 | 16 | 7 | 13 | 20 | yes |
| sequence-window | 2 | 13 | 0 | 13 | 13 | yes |
| spliterator-window | 2 | 15 | 2 | 14 | 16 | yes |

注記:

- `runs=2` は性能に関する結論を出すには小さすぎる。
- `MULTISET` はこの小規模実行でも固定オーバーヘッドを示しており、仮説生成として有用である。
- 正式な結果には、より大きいデータセット、十分な測定サンプル数、リソースメトリクスが必要である。

## Cursor / fetchSize 修正の予備確認

PostgreSQL JDBC の cursor 条件を満たすため、`fetchStream()` を使う export query に `fetchSize(1000)` を指定し、CSV 書き込み全体を read-only transaction 内で実行するようにした。

あわせて、`SEQUENCE_WINDOW` / `SPLITERATOR_WINDOW` が使っていなかった `order_attribute_definition` JOIN を削除した。

この確認は正式 benchmark ではなく、修正前後で以下を確認するための予備測定である。

- MD5 が一致すること
- elapsed が大きく悪化しないこと
- heap peak が悪化しないこと

条件:

| Variable | Value |
| --- | ---: |
| $N$ | 5,000 |
| $A$ | 15 |
| $V$ | 75,000 |
| $S = N (A + B)$ | 100,000 cells |
| warmup | 1 |
| measurement | 3 |
| backend restart | strategy ごとに実施 |

実行ディレクトリ:

```text
docs/benchmark/runs/before_cursor_sequence_window_isolated_5000x15_20260705_1759/
docs/benchmark/runs/after_cursor_sequence_window_isolated_5000x15_20260705_1802/
docs/benchmark/runs/before_cursor_spliterator_window_isolated_5000x15_20260705_1800/
docs/benchmark/runs/after_cursor_spliterator_window_isolated_5000x15_20260705_1803/
```

概要:

| Strategy | Revision | Median ms | Min ms | Max ms | Heap used max | MD5 matched |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| sequence-window | before | 158 | 156 | 250 | 224.9 MB | yes |
| sequence-window | after | 176 | 148 | 202 | 217.9 MB | yes |
| spliterator-window | before | 175 | 167 | 279 | 301.8 MB | yes |
| spliterator-window | after | 153 | 133 | 189 | 217.8 MB | yes |

注意:

- Prometheus counter 系の `Increase` は backend restart と scrape 境界の影響を受けるため、この予備確認では heap used max と MD5/elapsed を主に見る。
- 正式な resource profile には isolated runner と JFR / GC log の整備が必要である。

## SQL Pivot 追加後の smoke 解釈メモ

この run は正式な性能測定結果ではない。

SQL Pivot 戦略を追加した直後に、全5戦略の疎通、MD5同一性、round-robin runner の動作を確認するために実行した。

実行ディレクトリ:

```text
docs/benchmark/runs/sql_pivot_smoke_5000x15_20260705_1925/
```

条件:

| Variable | Value |
| --- | ---: |
| $N$ | 5,000 |
| $A$ | 15 |
| $V$ | 75,000 |
| $B$ | 5 |
| $S = N (A + B)$ | 100,000 cells |
| warmup | 1 |
| measurement | 3 |

処理時間:

| Strategy | Samples | Median ms | IQR ms | Min ms | Max ms | MD5 matched |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| preload | 3 | 95 | 34 | 94 | 128 | yes |
| sql-pivot | 3 | 103 | 4 | 102 | 106 | yes |
| spliterator-window | 3 | 133 | 11 | 130 | 141 | yes |
| sequence-window | 3 | 138 | 2 | 137 | 139 | yes |
| multiset | 3 | 265 | 18 | 260 | 278 | yes |

解釈:

- この規模では `preload` と `sql-pivot` がほぼ同着で速い。$N=5{,}000, A=15, V=75{,}000$ では `preload` の $\Theta(V)$ メモリ保持がまだ支配的になりにくく、DB側で横展開して1回で返す `sql-pivot` と並ぶのは自然である。
- `preload` の 128ms は measurement 1回目だけの外れ値で、2回目と3回目は 94ms / 95ms である。measurement=3 では IQR は外れ値1つで大きく歪むため、IQRの戦略間比較には使わない。
- `multiset` は warmup 516ms から measurement median 265ms まで大きく下がった。`MULTISET` は warmup の効き方が大きい可能性があるため、正式runでは warmup=5 以上、measurement=10 以上で確認する。
- round-robin の開始戦略は round ごとにローテートしており、順序バイアスを減らす runner の意図はこの run でも機能している。

Wide 条件での注意:

- `sql-pivot` が $A$ 増加で失速した場合、DB側の `MAX(CASE WHEN ...)` 評価だけを原因にしない。
- 実装上、`OrderRepositoryImpl.mapToOrderAttributePivotRow()` は1注文行ごとに全 `pivotFields` を `record.get(pivot.field)` で走査し、`Map<Long, String>` へ戻している。したがって JVM側の pivot row mapping も $\Theta(N \cdot A)$ である。
- `sequence-window` / `spliterator-window` はDBから縦持ちで受け取り、現在の注文に存在する属性を畳み込む。一方 `sql-pivot` はDBから密な $A$ 列を受け取るため、Wide条件では DB CPU、jOOQ `Record.get()`、allocation、CSV writer のどこが支配的かを分けて見る必要がある。
- 正式なWide比較では elapsed だけでなく、heap peak、allocation、GC pause、可能なら `EXPLAIN (ANALYZE, BUFFERS)` と JFR allocation profile を合わせて確認する。

## Formal Result Slots（正式測定の記入枠）

正式な run は、生成され次第ここに追加していく。

### Baseline

目標条件:

| Variable | Value |
| --- | ---: |
| $N$ | 30,000 |
| $A$ | 15 |
| density | dense |
| $V$ | 450,000 |

状態: reproducible artifact形式ではまだ記録されていない。

### Sparse

目標条件:

| Variable | Value |
| --- | ---: |
| $N$ | 30,000 |
| $A$ | 15 |
| average attributes | 2 |
| $V$ | 60,000 |

状態: reproducible artifact形式ではまだ記録されていない。

### Wide

目標条件:

| Variable | Value |
| --- | ---: |
| $N$ | 30,000 |
| $A$ | 120 |
| density | dense |
| $V$ | 3,600,000 |

状態: reproducible artifact形式ではまだ記録されていない。

## 報告すべきメトリクス（Metrics To Report）

最低限:

- elapsed median
- elapsed IQR
- max
- MD5 equality
- heap peak
- GC pause total

追加:

- allocation rate
- allocated bytes per run
- rows/sec
- cells/sec
- CSV output bytes
- DB query time
- EXPLAIN actual rows / buffers
- JFR allocation profile

## チャート計画（Chart Plan）

チャートは Grafana のスクリーンショットをコピーするのではなく、生のrun artifactから生成すべきである。

初期チャートセット:

- strategy別 elapsed median + IQR
- strategy別 elapsed samples
- heap used の時系列
- GC pause の時系列
- allocation rate の時系列
- strategy resource placement matrix

Grafana はライブ観測には引き続き有用である。記事・発表の一次情報源は run artifact ディレクトリとする。
