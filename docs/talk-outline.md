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
14. elapsed だけでなく heap / allocation / GC / DB plan を見る
15. まとめ: Sequence は銀の弾丸ではない。出力形式とデータ形状から責務配置を選ぶ

## Demo / Figures

Required figures:

- Sparse input table -> dense CSV output
- Strategy responsibility map: DB / JVM / I/O
- $N$, $A$, $V$, $S$ model
- elapsed median + IQR chart
- heap / GC comparison chart
- MULTISET generated SQL or EXPLAIN excerpt
- SQL Pivot wide SELECT example

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

特に、Kotlin Sequence は有用だが銀の弾丸ではない。遅延評価で全件保持を避けられても、固定ヘッダ CSV の dense output は消えない。また、jOOQ MULTISET はアプリコード上は自然なネスト取得に見える一方、PostgreSQL では JSONB 系のエミュレーションとして実行されるため、DB 側や mapping 側にコストが移る。

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

- [ ] SQL Pivot 戦略
- [ ] Imperative ResultSet baseline
- [ ] chart generation
- [ ] EXPLAIN artifacts
- [ ] JFR allocation profile

P2:

- [ ] Direct Writer comparison
- [ ] JSON aggregation strategy
- [ ] isolated JVM run
- [ ] OOM / heap pressure reproduction
