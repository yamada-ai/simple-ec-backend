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
