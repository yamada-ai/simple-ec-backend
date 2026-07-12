# Talk Outline: Kotlin Fest 2026

Working title:

```text
2つのStreamは結合できない：1対多データを横持ちCSVにする設計と実測
```

(旧working title「巨大な1対多データをCSVに横展開する: Kotlin SequenceとJVMメモリで考えるエクスポート設計」は、実際の投稿群と温度感を比較した結果、生真面目すぎると判断し差し替え。経緯は会話ログ参照。)

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

## Proposal Draft (fortee 提出用清書版, 2026-07-12)

実務での原体験（楽楽請求でのpreload採用の経緯）を核に据えた版。旧版（数式モデル先行のプロポーザル）は「問題のモデル化ができておらず何も伝わらない」として本人に却下され、下記に差し替えた。差し替えの経緯・却下理由・引用の裏取り結果は会話ログ参照。

### セッション概要（400字以内・公開）

386字。codexレビューで「原理的に不可能」（自己矛盾。sequence-window/spliterator-windowで実際に実装できている）と「そのまま社内標準になっています」（社内の意思決定を公開する形になり、handoff.mdで既に合意した「issueクローズの経緯は書かない」方針と矛盾する）の2点を指摘され修正。さらに2回目のレビューで、フォーム側の文字数カウント差に備えて末尾を短縮し、395字→386字に調整。

```text
注文はStreamで流れてくるのに、紐づく属性値だけ全部メモリに載せていいのか。CSVは1注文=1行、列は属性の数だけ固定で、値の無い属性は空セルになります。
属性側もStreamで取得し、注文とキーで結合しながら流せばいいはずだと思っていました。ところが、2つのStreamをキーで突き合わせながら流す標準APIはなく、順序を前提にwindowingを自分で書く必要があると分かりました。
結局、属性値を先に全部メモリのMapへ読み込んでから注文をループする形に倒しました。負荷テストでも問題にならず、実務ではこの形に倒れがちです。本当にそれでよかったのか。
Sequence、自作Spliterator、jOOQ MULTISET、SQL横展開まで7通りを実測し、直感を裏切る結果が出ました。CSV出力のコストをDB・JVM・出力のどこで払うべきか、実測から考えます。
```

不採用案1（392字、「原理的に不可能」「社内標準」の表現がcodexレビューで指摘され不採用）:

```text
注文はStreamで流れてくるのに、紐づく属性値だけ全部メモリに載せていいのでしょうか。CSVは1注文=1行、列は属性の数だけ固定で、値の無い属性は空セルになります。
属性側もStreamで取得し、注文とキーで結合しながら流せばいいはずだと思っていました。ところが、2つのStreamをキーで結合しながら流す標準APIは存在せず、これは原理的にできないと分かりました。
結局、属性値を先に全部メモリのMapへ読み込んでから注文をループする形に倒しました。負荷テストでも問題にならず、そのまま社内標準になっています。本当にそれでよかったのでしょうか。
Sequence、自作Spliterator、jOOQ MULTISET、SQL側の横展開まで7通りを実測し、直感を裏切る結果が出ました。CSV出力のコストをDB・JVM・出力のどこで払うべきか、実測に基づいた設計判断が持ち帰れます。
```

不採用案2（353字、一段落でトーンが硬すぎたため不採用）:

```text
注文はStreamで流れる一方、紐づくユーザー定義の属性値は1対多です。CSVは1注文=1行、列は属性の数だけ固定で、値の無い属性は空セルになります。属性側もStreamで取り、注文とキーで結合しながら流せばいいと最初は思っていましたが、2つのStreamをキーで結合しながら流す標準APIは無く、原理的にできないと分かりました。結局、属性値を先に全部メモリのMapへ読み込んでから注文をループする形に倒し、負荷テストでも問題にならなかったため、そのまま社内標準になっています。本当にそれでよかったのか。Sequence、自作Spliterator、jOOQ MULTISET、SQL側の横展開まで7通りを実測し、直感を裏切る結果をもとに、CSV出力の責務をDB・JVM・出力のどこに置くべきかを考えます。
```

### セッション詳細（2,000字以内・非公開/審査のみ）

1752字。codexレビュー2回目で「Wikipediaを直接の根拠として強く出すのは危うい」「Kotlinの抽象コストは"実質ゼロ"は言い過ぎ」と指摘され、Wikipedia由来の記述を「定石として紹介されている」に弱め、"実質ゼロ"を"支配的ではなかった"に修正。3回目のレビューで(3)冒頭の段落にも同じ「Wikipediaにも定石として明記」という強い表現が残っていた点を指摘され、「この文脈でよく紹介される定石の一つ」に統一。

```text
(1) 導入：CSVに横展開するときの壁 [5分]
注文はStreamで流れてくる一方、紐づくユーザー定義の属性値は1対多です。CSVは1注文=1行、列は属性定義の数だけ固定で、値の無い属性は空セルになります。属性側もStreamで取得し、注文とキーで結合しながら流せばよいと最初は思っていましたが、これは標準APIだけでは表現できず、順序を前提にしたwindowingを自分で書く必要があると分かりました。

・Stream.zipは1対1専用で、キー突き合わせには使えない
・groupingBy/associateはキー単位の全件materializeが前提
・SQLのJOINはDB側の話で、JVM側でStreamを維持したまま結合する標準APIではない

結局、属性値を先に全部メモリのMapへ読み込んでから注文をループする形に倒し、負荷テストでも問題にならなかったため、実務ではこの形に倒れがちです。

(2) 問題のモデル化 [5分]
注文数N、属性定義数A、属性値総数V、1注文あたり最大属性数a_maxを定義します。正しいCSVを出す限り、出力サイズはΩ(N・A)になります。入力がどれだけ疎（Vが小さい）でも、固定ヘッダCSVは密な出力になるという主張の根拠を示します。

・N: 注文数（実務では上限なし）
・A: 属性定義数（実務では15固定）
・V: 属性値総数（疎なら小さいが、Nに上限がなければ実質上限なし）

(3) 定石との対話：EAVパターンという先行研究 [5分]
この問題はEntity-Attribute-Value (EAV) パターンとして20年以上議論されており、preload（in-memoryでpivotする手法）も、この文脈でよく紹介される定石の一つです。Baseline実測でもpreloadは最速で、定説と一致します。

・EAVでは、in-memory pivot（preloadと同じ発想）が定石として紹介されている
・その紹介では「メモリに収まらない場合はスライス処理でスマートにできる」と一文だけ触れているが、閾値も定量的な議論も、それを実装・比較した実測もない
・出力下界Ω(N・A)の明示、疎性を軸にした実測比較も見当たらなかった
・jOOQのLukas Ederの計測も、FILM 1000件・ACTOR 200件という小規模データセットが前提

定石は正しい。その上で、なぜ自分はそれを問題だと感じたのかを次で話します。

(4) 7戦略の実装と実測 [15分]
preload、jOOQ MULTISET、Kotlin Sequence windowing、自作Spliterator windowing、SQL側条件付き集約(pivot)、JDBC手続きbaseline、jsonb_object_aggの7実装を、Baseline/Sparse/Wideの3条件で比較します。

・Baselineでは理論上最も危ういpreloadが最速(325ms)
・Sparseでは逆転し、window系がpreloadを上回る
・Kotlin SequenceとSpliterator手書きに有意差なし
・jOOQ MULTISETは条件によって2.6〜4.3倍遅く、PostgreSQLのJSONB系エミュレーションの影響が見える
・SQL PivotはWideでPostgreSQLのJITまで巻き込むほど式評価が重くなる
・Wideではpreloadがheap上限に張り付き測定から除外、Θ(V)の危険性を実演

(5) まとめ：Kotlinへの着地 [5分]
SequenceとSpliterator手書きに性能差がないことは、Kotlinの抽象化コストがこのワークロードで支配的ではなかったことを示します。だから「どう書くか」を性能と引き換えにせず、DB・JVM・出力のどこに責務とコストを置くかという設計判断に集中できます。

・正しいCSVを出す限りΩ(N・A)の出力コストは消えない
・問われるのは、消せないコストをどこで払うかという責務配置の判断
・Kotlinの抽象コストはこのワークロードで支配的ではなかった
```

### 未反映・要検討

- Threats to Validity（single-machine Docker同居、imperative-result-setの外れ値、Wide sql-pivotのEXPLAIN逆転、multisetの遅さの原因）は§9に列挙済みだが、上記詳細セクションにはまだ明示的に入れていない。文字数残り335字（2000-1665）の使い道候補。
- 「社内初のKotlin採用商材」「sealed interface導入者本人」という個人カードは、セッション概要/詳細ではなく、別途スピーカープロフィール欄がある場合はそちらに書く方が適切かもしれない（fortee側の入力欄構成を要確認）。
- issueがクローズされた経緯は本文に含めない方針（本人の判断）。

## Remaining Work Before Submission

P0:

- [x] 4戦略のコード構造を整理
- [x] benchmark artifact runner を追加
- [x] smoke run で MD5 一致を確認
- [x] 問題モデルを文書化
- [x] 戦略比較を文書化
- [x] README を「1対多データの横持ち CSV エクスポート」導線に寄せる
- [x] プロポーザル本文を応募フォーマットに合わせて圧縮する（`## Proposal Draft` 参照。未反映事項は同節末尾に記載）

P1:

- [x] SQL Pivot 戦略
- [x] Imperative ResultSet baseline
- [x] chart generation
- [x] EXPLAIN artifacts（`collect_explain.py` + Cost Split分析 + JIT検証、`docs/benchmark/results.md` 参照）
- [ ] JFR allocation profile（Wide条件でsequence-window/json-aggregation/sql-pivotの3戦略分は取得済み。allocation profileの分析が未了）

P2:

- [ ] Direct Writer comparison
- [x] JSON aggregation strategy
- [ ] isolated JVM run
- [x] OOM / heap pressure reproduction
