# Talk Outline: Kotlin Fest 2026

Working title:

```text
巨大な1対多データをCSVに横展開する:
Kotlin SequenceとJVMメモリで考えるエクスポート設計
```

本文では「動的列 CSV」よりも、より一般的な言い方として **1対多データの横持ち CSV エクスポート** を主語にする。

## Core Message

CSV 出力は簡単に見える。

しかし、1対多データを「1親レコード=1行」の固定ヘッダ CSV に横展開すると、入力が sparse でも出力は dense になる。

そのため、Kotlin `Sequence` を使えば自動的に省メモリになる、という話ではない。

重要なのは、DB / JVM / I/O のどこに責務とコストを置くかである。

## Audience

Target:

- Kotlin/JVM で業務システムやバッチを書く中級者
- CSV export、帳票、データ連携、管理画面の実装経験がある人
- `Sequence` / Stream / jOOQ / PostgreSQL の性能特性に関心がある人

Not target:

- Kotlin 入門
- CSV ライブラリ紹介
- JMH の細かい使い方だけの話

## Story

1. CSV 出力なんて簡単、と思っていた
2. 実務では 1対多データを 1行に横展開したいことがある
3. 例: 注文ごとのユーザ定義属性を固定ヘッダ CSV に出す
4. RDB では属性値は縦持ち、CSV では横持ち
5. 入力は sparse でも、固定ヘッダ CSV は dense output になる
6. $N$, $A$, $V$, $a_{\text{max}}$, $B$, $S$ で問題を定式化する
7. 正しい CSV を出す限り $\Omega(N \cdot A)$ の出力下界は避けられない
8. PRELOAD: 速い可能性はあるが $\Theta(V)$ の heap を使う
9. SEQUENCE_WINDOW: orderId 順序を前提に 1パスで畳む
10. SPLITERATOR_WINDOW: split できない処理を Spliterator として表現する
11. MULTISET: コードは自然だが PostgreSQL では JSONB 系 emulation になりうる
12. SQL Pivot: JVM 側の横展開を避けるが、DB 側の条件付き集約と動的 SQL に寄る
13. 実測: Baseline / Sparse / Wide
14. Baseline と Sparse を比較し、$V$ 依存と $N \cdot A$ 依存の違いを見る
15. Wide で $A$ 増加時の SQL Pivot / PRELOAD / window 系の振る舞いを見る
16. elapsed だけでなく heap / allocation / GC / DB plan を見る
17. まとめ: Sequence は銀の弾丸ではない。出力形式とデータ形状から責務配置を選ぶ

## Demo / Figures

Required figures:

- Sparse input table -> dense CSV output
- Strategy responsibility map: DB / JVM / I/O
- $N$, $A$, $V$, $S$ model
- elapsed median + IQR chart
- Baseline -> Sparse ratio table: $V$ dependency spectrum
- heap / GC comparison chart
- MULTISET generated SQL or EXPLAIN excerpt
- SQL Pivot wide SELECT example

## Key Result To Show

### Baseline / Sparse: $V$ 依存 vs $N \cdot A$ 依存

Baseline dense と Sparse は、$N=30{,}000, A=15, S=600{,}000$ を固定し、$V$ だけを 450,000 から 53,985 へ減らした比較である。

| Strategy | Baseline | Sparse | Baseline / Sparse |
| --- | ---: | ---: | ---: |
| sql-pivot | 389 ms | 338 ms | 1.15x |
| preload | 325 ms | 148 ms | 2.20x |
| multiset | 1414 ms | 392 ms | 3.61x |
| json-aggregation | 505 ms | 159 ms | 3.18x |
| imperative-result-set | 490 ms | 115 ms | 4.26x |
| spliterator-window | 571 ms | 125 ms | 4.57x |
| sequence-window | 586 ms | 126 ms | 4.65x |

この表で言いたいこと:

- `sql-pivot` は $V$ が減ってもほぼ速くならない。DB側の conditional aggregation と JVM側の全pivot列走査が $N \cdot A$ に近い固定コストになる。
- window系は $V$ が減ると大きく速くなる。DBから読む縦持ち行数 $R \approx N + V$ に依存するモデルと整合する。
- `preload` / `multiset` / `json-aggregation` は中間に位置する。$V$ 駆動の部分と $N$ 駆動の固定コストが混ざっている。
- 単一条件の速度ランキングだけでは誤読する。データ形状を変えて初めて、どの戦略が何に依存しているかが見える。

### Wide: $A$ 増加で見えること

Wide full は $N=30{,}000, A=120, V=3{,}600{,}000$。

`preload` はWide-lite時点でheap上限に近づいたため、Wide fullでは他戦略を巻き込まないよう除外した。

`sql-pivot` は $A=120$ で window 系より遅くなった。これは、DB側の `MAX(CASE WHEN ...)` と、JVM側の `mapToOrderAttributePivotRow()` が全pivot列を読むコストの両方が効くという事前仮説と整合する。

`imperative-result-set` は最速寄りだが、focused runでも外れ値が出るため、「最速戦略」ではなく jOOQ `Record` mapping と抽象コストを避けた下限比較baselineとして扱う。

## What Makes The Talk Strong

- 実務でよくある CSV export を題材にしている
- 「Sequence の話」に閉じず、DB/JVM/I/O の責務配置まで広げる
- 数式モデルと実装コードが対応している
- MD5 で出力同一性を検証する
- Grafana スクショではなく、再生成可能な benchmark artifacts を残す
- PostgreSQL / jOOQ / JVM memory / GC の具体的な失敗ポイントを扱う

## Proposal Draft

業務システムでは、注文と注文明細、顧客と複数属性、申請と承認履歴のような 1対多データを、CSV では「1親レコード=1行」の横持ち形式で出力したい場面がある。

一見すると単純な CSV 出力だが、入力が sparse な 1対多データであっても、固定ヘッダ CSV は dense な出力になる。つまり、存在しない値も空セルとして出力する必要があり、正しい CSV を出す限り $N \cdot A$ に比例する出力下界は避けられない。

本発表では、注文とユーザ定義属性を題材に、Kotlin/JVM でこの問題をどう実装・計測するかを比較する。PRELOAD、Kotlin Sequence による windowing、Java Spliterator による windowing、jOOQ MULTISET、SQL Pivot といった戦略を、DB CPU、JVM heap、allocation、GC、I/O のどこにコストを置くかという観点で整理する。

特に、Kotlin Sequence は有用だが銀の弾丸ではない。遅延評価で全件保持を避けられても、固定ヘッダ CSV の dense output は消えない。一方で、入力が sparse になると window 系は縦持ち行数の減少を素直に受けられる。SQL Pivot はBaselineでは速く見えるが、$V$ が減っても速くなりにくく、$A$ が増えると条件付き集約と全列mappingのコストが表面化する。また、jOOQ MULTISET はアプリコード上は自然なネスト取得に見える一方、PostgreSQL では JSONB 系のエミュレーションとして実行されるため、DB 側や mapping 側にコストが移る。

この発表では、実装コード、計算量モデル、再現可能な benchmark artifacts、MD5 による出力同一性検証を通して、実務の CSV export / バッチ / レポート出力に再利用できる設計判断を示す。

## Remaining Work Before Submission

P0:

- [x] 4戦略のコード構造を整理
- [x] benchmark artifact runner を追加
- [x] smoke run で MD5 一致を確認
- [x] 問題モデルを文書化
- [x] 戦略比較を文書化
- [x] README を「1対多データの横持ち CSV エクスポート」導線に寄せる
- [ ] プロポーザル本文を応募フォーマットに合わせて圧縮する

P1:

- [x] SQL Pivot 戦略
- [x] Imperative ResultSet baseline
- [x] chart generation
- [ ] EXPLAIN artifacts
- [ ] JFR allocation profile

P2:

- [ ] Direct Writer comparison
- [x] JSON aggregation strategy
- [ ] isolated JVM run
- [x] OOM / heap pressure reproduction
