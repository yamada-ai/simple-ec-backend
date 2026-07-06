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

## Imperative ResultSet 追加後の smoke 解釈メモ

この run は正式な性能測定結果ではない。

`Sequence` / `Spliterator` / jOOQ `Record` mapping を避けた ordered group fold baseline として、`imperative-result-set` を追加した直後の疎通確認である。

実行ディレクトリ:

```text
docs/benchmark/runs/imperative_resultset_smoke_5000x15_20260705_1951/
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
| preload | 3 | 101 | 17 | 94 | 111 | yes |
| sql-pivot | 3 | 109 | 12 | 103 | 115 | yes |
| imperative-result-set | 3 | 126 | 7 | 122 | 129 | yes |
| sequence-window | 3 | 134 | 7 | 132 | 139 | yes |
| spliterator-window | 3 | 137 | 4 | 134 | 138 | yes |
| multiset | 3 | 263 | 31 | 255 | 286 | yes |

解釈:

- 全戦略で MD5 が一致したため、`imperative-result-set` は既存戦略と同じCSVを生成できている。
- この小規模runでは `imperative-result-set` が window系より少し速い。ただし measurement=3 なので、差を結論として扱うには不十分である。
- `imperative-result-set` は jOOQ `Record` 生成と Kotlin `Sequence` / Java `Spliterator` の抽象を避ける。そのため、正式測定では「ordered group fold 自体のコスト」と「抽象・mappingのコスト」を分離する比較点として使う。
- コードの保守性は他戦略より低い。SQL文字列、parameter binding、ResultSet close を手で持つため、実務実装として推すのではなく baseline として扱う。

## JSON aggregation 追加後の smoke 解釈メモ

この run は正式な性能測定結果ではない。

PostgreSQL の `jsonb_object_agg(attribute_definition_id, value)` で注文ごとの属性MapをDB側で作り、JVM側でJSONBをparseする `json-aggregation` 戦略を追加した直後の疎通確認である。

実行ディレクトリ:

```text
docs/benchmark/runs/json_aggregation_smoke_5000x15_20260705_2002/
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
| preload | 3 | 93 | 5 | 89 | 94 | yes |
| sql-pivot | 3 | 103 | 5 | 100 | 105 | yes |
| json-aggregation | 3 | 118 | 16 | 107 | 123 | yes |
| imperative-result-set | 3 | 127 | 15 | 124 | 139 | yes |
| sequence-window | 3 | 136 | 22 | 127 | 149 | yes |
| spliterator-window | 3 | 139 | 7 | 133 | 140 | yes |
| multiset | 3 | 283 | 11 | 278 | 289 | yes |

解釈:

- 全戦略で MD5 が一致したため、`json-aggregation` は既存戦略と同じCSVを生成できている。
- この小規模runでは `json-aggregation` は `MULTISET` よりかなり速く、`SQL Pivot` に近い位置に出ている。
- これは「DB側でJSONB payloadを作ること」自体と「jOOQ `MULTISET` 抽象・nested mapping」の切り分けに使える可能性がある。
- ただし measurement=3 の smoke なので、正式な結論には使わない。Wide条件では JSONB生成、JVM側JSON parse、CSV writer allocation のどこが支配的かを `EXPLAIN` / JFR / allocation profile で確認する。

## Baseline / Wide-lite round-robin 解釈メモ

この run は正式測定に近い条件だが、まだ単一マシン・local Docker 上の予備測定として扱う。

### Baseline dense

実行ディレクトリ:

```text
docs/benchmark/runs/baseline_30k_15_dense_20260705_2029/
```

条件:

| Variable | Value |
| --- | ---: |
| $N$ | 30,000 |
| $A$ | 15 |
| $V$ | 450,000 |
| $B$ | 5 |
| $S = N (A + B)$ | 600,000 cells |
| warmup | 5 |
| measurement | 10 |

処理時間:

| Strategy | Samples | Median ms | IQR ms | Min ms | Max ms | MD5 matched |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| preload | 10 | 325 | 76 | 306 | 431 | yes |
| sql-pivot | 10 | 389 | 9 | 376 | 403 | yes |
| imperative-result-set | 10 | 490 | 25 | 462 | 573 | yes |
| json-aggregation | 10 | 505 | 6 | 499 | 573 | yes |
| spliterator-window | 10 | 571 | 30 | 556 | 608 | yes |
| sequence-window | 10 | 586 | 152 | 545 | 1405 | yes |
| multiset | 10 | 1414 | 88 | 1345 | 1599 | yes |

Prometheus summary:

| Metric | Value |
| --- | ---: |
| heap used max | 361.0 MB |
| GC pause total increase | 1.704 s |
| GC count increase | 243 |
| allocated bytes increase | 67.9 GB |

解釈:

- `preload` は $V=450{,}000$ ではまだ最速である。$\Theta(V)$ のメモリ保持は理論上の弱点だが、この規模では速度面の利点が先に出ている。
- `multiset` は明確に遅い。PostgreSQLでは jOOQ `MULTISET` がネイティブ構文ではなくJSONB系のエミュレーションになり、DB側ネスト化と jOOQ mapping のコストを払っている可能性が高い。
- `sequence-window` は measurement 中に 1405ms の外れ値が出た。focused run では再現しなかったため、現時点では戦略固有の安定性問題ではなく、round-robin中の局所的外乱として扱う。
- `sequence-window` と `spliterator-window` は中央値が近い。同じ ordered group fold を Kotlin `Sequence` と手書き `Spliterator` で表現しても、この条件では抽象差が支配的ではない。

### Window focused stability

実行ディレクトリ:

```text
docs/benchmark/runs/baseline_sequence_stability_30k_15_20260705_2038/
```

条件:

| Variable | Value |
| --- | ---: |
| $N$ | 30,000 |
| $A$ | 15 |
| $V$ | 450,000 |
| warmup | 3 |
| measurement | 20 |

処理時間:

| Strategy | Samples | Median ms | IQR ms | Min ms | Max ms | MD5 matched |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| imperative-result-set | 20 | 490 | 7 | 480 | 562 | yes |
| sequence-window | 20 | 555 | 10 | 537 | 569 | yes |
| spliterator-window | 20 | 561 | 8 | 550 | 606 | yes |

解釈:

- `sequence-window` の baseline round-robin 外れ値はこの focused run では再現しなかった。
- `imperative-result-set` はやや速いが、差は劇的ではない。jOOQ `Record` mapping や Kotlin `Sequence` の抽象コストが支配的というより、CSV横展開とDB/JDBC I/Oが支配している可能性がある。

### Wide-lite dense

実行ディレクトリ:

```text
docs/benchmark/runs/wide_lite_10k_120_dense_20260706_0030/
```

条件:

| Variable | Value |
| --- | ---: |
| $N$ | 10,000 |
| $A$ | 120 |
| $V$ | 1,200,000 |
| $B$ | 5 |
| $S = N (A + B)$ | 1,250,000 cells |
| warmup | 3 |
| measurement | 5 |

処理時間:

| Strategy | Samples | Median ms | IQR ms | Min ms | Max ms | MD5 matched |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| preload | 5 | 988 | 24 | 962 | 1013 | yes |
| imperative-result-set | 5 | 1260 | 9 | 1246 | 1265 | yes |
| json-aggregation | 5 | 1260 | 12 | 1251 | 1289 | yes |
| spliterator-window | 5 | 1338 | 3 | 1327 | 1339 | yes |
| sequence-window | 5 | 1344 | 5 | 1337 | 1368 | yes |
| sql-pivot | 5 | 2217 | 14 | 2216 | 2258 | yes |
| multiset | 5 | 3344 | 63 | 3237 | 3372 | yes |

Prometheus summary:

| Metric | Value |
| --- | ---: |
| heap used max | 527 MB |
| heap max | 537 MB |
| GC pause total increase | 3.617 s |
| GC count increase | 359 |
| allocated bytes increase | 80.1 GB |

解釈:

- `preload` はまだ速いが、heap used max が heap max にほぼ張り付いている。Wide本番では $\Theta(V)$ のメモリ保持がOOMリスクになるため、他戦略と混ぜた round-robin からは外す判断が妥当である。
- `sql-pivot` は $A=120$ で失速した。DB側の `MAX(CASE WHEN ...)` だけでなく、JVM側で全 `pivotFields` を行ごとに走査する $\Theta(N \cdot A)$ mapping コストも候補である。
- `sequence-window` と `spliterator-window` はWide-liteでもほぼ同等で安定している。実装思想は違うが、同じ ordered group fold を実行しているため、この条件では差が表面化していない。
- `json-aggregation` は予想より強い。DB側JSONB生成とJVM側JSON parseのコストはあるが、`MULTISET` よりは明確に軽い。
- `multiset` はWide-liteでも遅い。ただしheap面の危険というより、既知の低速さが測定時間を伸ばす点が問題である。Wide本番に含める場合は「遅さが $A$ 増加でどこまで伸びるか」を見る目的に限定する。

### Wide full seed attempt

`N=30,000, A=120, V=3,600,000` のWide本番を Admin API seed で投入しようとしたところ、backend JVM が `-Xmx512m` で Full GC / Evacuation Failure ループに入った。

観察:

| Metric / Signal | Value |
| --- | ---: |
| backend heap max | 約537 MB |
| jvm memory usage after GC | 約0.99 |
| major GC pause total | 300s超まで増加 |
| dataset after abort | 0 rows |

解釈:

- これは export 戦略の失敗ではなく、seed 経路の失敗である。
- `SeedDataUseCase` は `List<Order>` と `List<OrderAttributeValue>` を全件生成してから保存するため、Wide dense では seed 処理自体が $\Theta(V)$ のJVMメモリを要求する。
- 正式測定では、データ準備コストを export 戦略の比較に混ぜないため、Wide dense は `scripts/bench/seed_benchmark_dataset_sql.sh` でPostgreSQLへ直接投入する。

### Wide full dense

`scripts/bench/seed_benchmark_dataset_sql.sh` で PostgreSQL に直接データ投入してから実行した。

実行ディレクトリ:

```text
docs/benchmark/runs/wide_30k_120_dense_20260706_2358/
```

条件:

| Variable | Value |
| --- | ---: |
| $N$ | 30,000 |
| $A$ | 120 |
| $V$ | 3,600,000 |
| $B$ | 5 |
| $S = N (A + B)$ | 3,750,000 cells |
| warmup | 3 |
| measurement | 5 |
| excluded strategy | `preload` |

処理時間:

| Strategy | Samples | Median ms | IQR ms | Min ms | Max ms | MD5 matched |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| imperative-result-set | 5 | 3832 | 313 | 3786 | 4142 | yes |
| json-aggregation | 5 | 4053 | 15 | 4034 | 4071 | yes |
| sequence-window | 5 | 4377 | 188 | 4326 | 4574 | yes |
| spliterator-window | 5 | 4666 | 259 | 4339 | 4724 | yes |
| sql-pivot | 5 | 4806 | 23 | 4743 | 4880 | yes |
| multiset | 5 | 10182 | 27 | 10143 | 10205 | yes |

Prometheus summary:

| Metric | Value |
| --- | ---: |
| heap used max | 354.7 MB |
| heap max | 536.9 MB |
| GC pause total increase | 1.085 s |
| GC count increase | 629 |
| allocated bytes increase | 199.7 GB |

生成チャート:

```text
docs/benchmark/runs/wide_30k_120_dense_20260706_2358/charts/
```

解釈:

- `preload` はWide-liteでheap天井に近づいたため、このrunでは除外した。これは速度比較から逃げたのではなく、$\Theta(V)$ のメモリ保持が他戦略の測定を巻き込むことを避けるためである。
- `imperative-result-set` が最速だった。ただし measurement round 1/2 と round 3以降で 4.1s 台から 3.8s 台へ分かれており、warmup=3 ではまだ定常化しきっていない可能性がある。下限比較点として扱うには focused stability run で再確認する。
- `json-aggregation` は `imperative-result-set` にかなり近い。PostgreSQLで注文ごとの属性MapをJSONB化し、JVM側でparseするコストはあるが、この条件では `MULTISET` よりかなり軽い。
- `sequence-window` と `spliterator-window` は同じ ordered group fold を異なる抽象で表現している。Wide fullでも差は大きくなく、`Sequence` の抽象コストが支配的ではない可能性が高い。
- `sql-pivot` はWide-liteと同様に $A=120$ で相対的に遅い。DB側の条件付き集約と、JVM側で全pivot列を読む $\Theta(N \cdot A)$ mapping の両方が候補である。
- `multiset` は約10秒で明確に遅い。遅さはheap天井ではなく、PostgreSQL上のJSONB系エミュレーション、ネスト化、jOOQ mapping に寄っている可能性がある。
- run全体の heap used max は約355MBで、preloadなしでは512MiB heap上限に張り付いていない。一方で allocated bytes increase は約200GBであり、巨大CSV横展開では短命オブジェクト生成が大きいことが見える。

### Wide imperative focused stability

Wide full run では `imperative-result-set` の measurement が前半 4.1s 台、後半 3.8s 台に分かれていたため、同じWide fullデータセットで `imperative-result-set` だけを再測定した。

実行ディレクトリ:

```text
docs/benchmark/runs/wide_imperative_stability_30k_120_20260707_0008/
```

条件:

| Variable | Value |
| --- | ---: |
| $N$ | 30,000 |
| $A$ | 120 |
| $V$ | 3,600,000 |
| $S = N (A + B)$ | 3,750,000 cells |
| warmup | 8 |
| measurement | 20 |

処理時間:

| Strategy | Samples | Median ms | IQR ms | Min ms | Max ms | MD5 matched |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| imperative-result-set | 20 | 3860 | 122 | 3842 | 4777 | yes |

Prometheus summary:

| Metric | Value |
| --- | ---: |
| heap used max | 335.7 MB |
| heap max | 536.9 MB |
| GC pause total increase | 0.376 s |
| GC count increase | 274 |
| allocated bytes increase | 87.9 GB |

読み取り:

- warmup 8回後の中心値は 3.85s 前後で、Wide full round-robin の median 3832ms と大きく矛盾しない。
- 一方で measurement 中にも 4138ms / 4167ms / 4391ms / 4777ms の外れ値が出ており、前回の二段階パターンを単純な warmup 不足だけで説明するのは弱い。
- `imperative-result-set` は最速寄りの下限比較点として有用だが、「常に安定して最速」と言い切るより、jOOQ `Record` mapping と抽象コストを避けた baseline として扱う方が誠実である。

### Baseline / Sparse 横断比較

Baseline dense と Sparse は、$N=30{,}000, A=15, S=600{,}000$ を固定し、$V$ だけを 450,000 から 53,985 へ変えた比較である。

この比較では、各戦略が $V$ にどれだけ依存しているか、あるいは $N \cdot A$ に近い固定コストをどれだけ持っているかが見える。

| Strategy | Baseline median | Sparse median | Baseline / Sparse |
| --- | ---: | ---: | ---: |
| sql-pivot | 389 ms | 338 ms | 1.15x |
| preload | 325 ms | 148 ms | 2.20x |
| multiset | 1414 ms | 392 ms | 3.61x |
| json-aggregation | 505 ms | 159 ms | 3.18x |
| imperative-result-set | 490 ms | 115 ms | 4.26x |
| spliterator-window | 571 ms | 125 ms | 4.57x |
| sequence-window | 586 ms | 126 ms | 4.65x |

読み取り:

- `sql-pivot` はSparseになってもほぼ変わらない。入力の $V$ ではなく、DB側のpivot式とJVM側のpivot field走査が $N \cdot A$ に近い固定コストとして効いているためである。
- window系と `imperative-result-set` は、$V$ が減ると大きく速くなる。DBから受け取る縦持ち行数 $R \approx N + V$ に依存しているというモデルと整合する。
- `preload` / `multiset` / `json-aggregation` は中間に位置する。実属性値数 $V$ に依存する部分と、注文行 $N$ やネスト/Map生成に由来する固定コストが混ざっていると読める。
- この横断比較は、単一条件の速度ランキングより重要である。Baselineだけなら `sql-pivot` は速く見えるが、疎性を変えると「疎な入力の恩恵を受けにくい戦略」だと分かる。

## Formal Result Slots（正式測定の記入枠）

正式な run は、生成され次第ここに追加していく。

### Baseline

実行ディレクトリ:

```text
docs/benchmark/runs/baseline_30k_15_dense_20260705_2029/
```

条件:

| Variable | Value |
| --- | ---: |
| $N$ | 30,000 |
| $A$ | 15 |
| density | dense |
| $V$ | 450,000 |
| $B$ | 5 |
| $S = N (A + B)$ | 600,000 cells |
| warmup | 5 |
| measurement | 10 |

処理時間:

| Strategy | Samples | Median ms | IQR ms | Min ms | Max ms | MD5 matched |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| preload | 10 | 325 | 76 | 306 | 431 | yes |
| sql-pivot | 10 | 389 | 9 | 376 | 403 | yes |
| imperative-result-set | 10 | 490 | 25 | 462 | 573 | yes |
| json-aggregation | 10 | 505 | 6 | 499 | 573 | yes |
| spliterator-window | 10 | 571 | 30 | 556 | 608 | yes |
| sequence-window | 10 | 586 | 152 | 545 | 1405 | yes |
| multiset | 10 | 1414 | 88 | 1345 | 1599 | yes |

Prometheus run summary:

| Metric | Value |
| --- | ---: |
| heap used max | 361.0 MB |
| GC pause total increase | 1.704 sec |
| GC count increase | 243 |
| allocated bytes increase | 67.9 GB |

解釈:

- 全戦略で MD5 が一致した。
- $V=450{,}000$ では `preload` の $\Theta(V)$ メモリ保持はまだ破綻せず、このrunでは最速だった。
- `sql-pivot` はIQRが小さく、elapsedが安定している。ただし $A$ が増えたWide条件では、DB側 conditional aggregation と JVM側 pivot field走査の両方が増える。
- `multiset` はこの条件でも明確に遅い。jOOQ `MULTISET` のコード上の明快さと、PostgreSQL上のネスト構築・mappingコストは分けて扱う。

#### sequence-window 外れ値の追加確認

Baseline full runでは、`sequence-window` に 725ms / 1405ms / 712ms の跳ねがあった。

生データ上、跳ねた回はいずれも `multiset` の直後に実行されている。

```text
round1: multiset(1599ms) -> sequence-window(725ms)
round2: multiset(1355ms) -> sequence-window(1405ms)
round8: multiset(1494ms) -> sequence-window(712ms)
```

ただし、`multiset` 直後でも跳ねない回があるため、これは因果ではなく観察に留める。

PrometheusのGC時系列を見ると、725ms / 712ms はGC pause増分が大きいscrape区間と重なっていた。一方、最大外れ値の 1405ms は該当scrape区間のGC pause増分が約17msで、GC pauseだけでは説明しにくい。

Prometheus scrape間隔は5秒で、各sampleは0.5〜1.4秒程度である。そのため、round-robin runだけでsample単位のresource attributionを厳密に行うのは難しい。

同じデータセットで `sequence-window`, `spliterator-window`, `imperative-result-set` の3戦略だけを再測定した focused run では、`sequence-window` の外れ値は再現しなかった。

実行ディレクトリ:

```text
docs/benchmark/runs/baseline_sequence_stability_30k_15_20260705_2038/
```

focused run:

| Strategy | Samples | Median ms | IQR ms | Min ms | Max ms | MD5 matched |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| imperative-result-set | 20 | 490 | 7 | 480 | 562 | yes |
| sequence-window | 20 | 555 | 10 | 537 | 569 | yes |
| spliterator-window | 20 | 561 | 8 | 550 | 606 | yes |

読み取り:

- `sequence-window` 自体が恒常的に不安定、とは言いにくい。
- full round-robin runの外れ値は、直前戦略影響、JVM heap/GC状態、DB/host schedulingなどの局所的外乱である可能性がある。
- 正式なresource attributionには、strategyごとの isolated run、JFR、GC log の保存が必要である。

### Sparse

実行ディレクトリ:

```text
docs/benchmark/runs/sparse_30k_15_avg1_8_20260707_0012/
```

条件:

| Variable | Value |
| --- | ---: |
| $N$ | 30,000 |
| $A$ | 15 |
| average attributes | 1.80 |
| $V$ | 53,985 |
| $S = N (A + B)$ | 600,000 cells |
| warmup | 5 |
| measurement | 10 |

処理時間:

| Strategy | Samples | Median ms | IQR ms | Min ms | Max ms | MD5 matched |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| imperative-result-set | 10 | 115 | 3 | 114 | 133 | yes |
| spliterator-window | 10 | 125 | 3 | 121 | 130 | yes |
| sequence-window | 10 | 126 | 5 | 123 | 130 | yes |
| preload | 10 | 148 | 10 | 138 | 201 | yes |
| json-aggregation | 10 | 159 | 5 | 157 | 168 | yes |
| sql-pivot | 10 | 338 | 7 | 334 | 348 | yes |
| multiset | 10 | 392 | 10 | 386 | 417 | yes |

Prometheus summary:

| Metric | Value |
| --- | ---: |
| heap used max | 382.9 MB |
| heap max | 536.9 MB |
| GC pause total increase | 0.274 s |
| GC count increase | 97 |
| allocated bytes increase | 30.8 GB |

読み取り:

- Sparse入力でもCSVは固定ヘッダなので、出力セル数 $S=600,000$ はBaseline denseと同じである。
- `sequence-window` / `spliterator-window` / `imperative-result-set` はかなり近い。入力の $V$ が小さいため、ordered group fold の差より固定列CSV出力の共通コストが見えやすい。
- `preload` は $V=53,985$ ではメモリ面の弱点が出にくいが、window系より少し遅い。
- `sql-pivot` はSparseでも遅い。入力がsparseでもDB側は `A=15` 個のpivot式と、JVM側は $N \cdot A$ のpivot field走査を行うため、疎性の恩恵を受けにくい。
- `multiset` はSparseでも最も遅い。Wide条件ほどではないが、ネスト化・JSONB系エミュレーション・mappingの固定コストが残っている可能性がある。
- 目標では平均属性数2としていたが、現行Admin API seedの分布では実測平均1.80になった。正式資料では実測 $V$ を併記する。

### Wide

実行ディレクトリ:

```text
docs/benchmark/runs/wide_30k_120_dense_20260706_2358/
```

条件:

| Variable | Value |
| --- | ---: |
| $N$ | 30,000 |
| $A$ | 120 |
| density | dense |
| $V$ | 3,600,000 |
| $S = N (A + B)$ | 3,750,000 cells |
| warmup | 3 |
| measurement | 5 |
| excluded strategy | `preload` |

処理時間:

| Strategy | Samples | Median ms | IQR ms | Min ms | Max ms | MD5 matched |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| imperative-result-set | 5 | 3832 | 313 | 3786 | 4142 | yes |
| json-aggregation | 5 | 4053 | 15 | 4034 | 4071 | yes |
| sequence-window | 5 | 4377 | 188 | 4326 | 4574 | yes |
| spliterator-window | 5 | 4666 | 259 | 4339 | 4724 | yes |
| sql-pivot | 5 | 4806 | 23 | 4743 | 4880 | yes |
| multiset | 5 | 10182 | 27 | 10143 | 10205 | yes |

Prometheus summary:

| Metric | Value |
| --- | ---: |
| heap used max | 354.7 MB |
| heap max | 536.9 MB |
| GC pause total increase | 1.085 s |
| GC count increase | 629 |
| allocated bytes increase | 199.7 GB |

読み取り:

- `preload` はWide-liteでheap上限に近づいたため除外した。
- `sql-pivot` は $A=120$ で window 系より遅くなり、DB側 conditional aggregation と JVM側 pivot field走査の仮説と整合した。
- `multiset` は約10秒台で、Wide条件でも明確に遅い。
- `imperative-result-set` は最速だが、measurement前半と後半で二段階に分かれているため、focused stability run で定常値を確認する。

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

生成方法:

```bash
docker compose run --rm bench-charts \
  --run-dir docs/benchmark/runs/<run-id>
```

初期チャートセット:

- strategy別 elapsed median + IQR
- strategy別 elapsed samples
- heap used の時系列
- GC pause の時系列
- allocation rate の時系列
- process CPU の時系列

今後追加するチャート:

- strategy resource placement matrix
- Baseline / Sparse / Wide の横断比較
- $A$ scaling / $N$ scaling

Grafana はライブ観測には引き続き有用である。記事・発表の一次情報源は run artifact ディレクトリとする。
