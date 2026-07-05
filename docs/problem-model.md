# Problem Model: 1-to-Many Wide CSV Export

このリポジトリで扱う主題は、単なる CSV 出力や Kotlin `Sequence` の Tips ではない。

主題は、**1対多データを、固定ヘッダの横持ち CSV として出力するとき、DB / JVM / I/O のどこに責務とコストを置くか**である。

## Concrete Example

EC の注文一覧を、業務担当者向けに CSV 出力する状況を考える。

注文テーブルには、固定列がある。

```text
order_id
customer_id
customer_name
customer_email
order_date
```

一方で、注文にはユーザ定義の属性が付く。

```text
gift_wrap
delivery_note
campaign_id
invoice_required
```

RDB 上では、属性値は自然には縦持ちになる。

```text
order_id | attribute_definition_id | value
-------- | ----------------------- | ----------------
1        | gift_wrap               | yes
1        | delivery_note           | morning delivery
3        | campaign_id             | summer-2026
```

しかし、業務 CSV では「1注文=1行」で見たい。

```text
order_id,customer_name,gift_wrap,delivery_note,campaign_id,invoice_required
1,Alice,yes,morning delivery,,
2,Bob,,,,
3,Carol,,,summer-2026,
```

ここで重要なのは、注文 2 が属性を 1つも持っていなくても、CSV では属性列を省略できないことだ。

つまり、この問題は **スパースな属性値行列を、固定ヘッダの密な表へ変換する問題**である。

## Why This Is Hard

一見すると、これはただの CSV 出力に見える。

しかし実際には、以下の難しさが同時に出る。

- 出力列は属性定義から実行時に決まる。
- 入力は sparse でも、CSV 出力は dense になる。
- 正しい CSV を出す限り、存在しない属性も空セルとして出力する必要がある。
- `Sequence` や `Stream` は遅延評価できるが、標準 API に keyed join はない。
- DB 側で横展開すると SQL が大きくなり、型付き DTO に戻しにくくなる。
- JVM 側で横展開すると Map 構築、allocation、GC、CSV writer のコストが出る。
- `MULTISET` はアプリコード上はきれいだが、PostgreSQL では JSONB 系のエミュレーションになりうる。

このため、論点は「Sequence を使えば省メモリになるか」ではない。

論点は、**不可避な出力コストを、どの層で、どのデータ形状のまま支払うか**である。

## Variables

以降、このリポジトリでは次の変数を使う。

| Symbol | Meaning |
| --- | --- |
| $N$ | 親レコード数。ここでは注文数。CSV の行数に対応する。 |
| $A$ | 動的列数。ここでは属性定義数。 |
| $V$ | 実際に存在する属性値の総数。`order_attribute_value` の行数。 |
| $a_i$ | 注文 $i$ に存在する属性数。 |
| $a_{\text{max}}$ | $\max(a_i)$。1注文が持つ属性数の最大値。 |
| $B$ | 固定列数。例: `order_id`, `customer_id`, `customer_name`, `customer_email`, `order_date`。 |
| $S$ | CSV に実際に出力されるセル数。$S = N (B + A)$。 |

$V$ は sparse input の大きさを表す。

$S$ は dense output の大きさを表す。

この2つを分けて考えることが重要である。

## Dense Output Lower Bound

属性値が sparse でも、固定ヘッダ CSV は dense な出力になる。

各注文に対して、属性列 $A$ 個分のセルを必ず出力するため、属性列だけで出力セル数は常に次の下界を持つ。

$$
\Omega(N \cdot A)
$$

固定列 $B$ も含めると、CSV のセル数は次のようになる。

$$
S = N (B + A)
$$

したがって、正しい CSV を生成する限り、少なくとも $\Omega(S)$ の出力コストは避けられない。

これは重要な制約である。

アルゴリズムや遅延評価によって、存在しない属性値の DB 読み取りは減らせる。しかし、最終的な CSV 上の空セル出力は消せない。

現行実装では、この $N \cdot A$ の列埋めは `OrderAttributeCsvRow.toRecord()` に集約されている。

```kotlin
val values = schema.definitionIds.map { defId -> attributes[defId] ?: "" }
```

PRELOAD / MULTISET / SEQUENCE_WINDOW / SPLITERATOR_WINDOW は取得戦略こそ異なるが、固定ヘッダ CSV として出力する直前には同じ列順で属性セルを埋める。このため、取得戦略を変えても dense output の下界そのものは消えない。

## Cost Decomposition

総時間を大まかに次のように分ける。

$$
T = T_{\text{DB}} + T_{\text{app}} + T_{\text{IO}}
$$

| Term | Examples |
| --- | --- |
| $T_{\text{DB}}$ | JOIN, ORDER BY, GROUP BY, JSONB aggregation, conditional aggregation, cursor fetch |
| $T_{\text{app}}$ | jOOQ Record mapping, Map construction, windowing, object allocation, CSV row construction |
| $T_{\text{IO}}$ | CSV escaping, string conversion, write, flush |

メモリは特に JVM 側のピークを重視する。$M_{\text{app}}$ を application-side peak memory とする。

このリポジトリの比較は、絶対性能ランキングではなく、**どの戦略がどこにコストを移動しているか**を見るためのものにする。

## Strategy Models

各戦略が $T_{\text{app}}$ / $M_{\text{app}}$ / $T_{\text{DB}}$ のどこにコストを置くかは、[strategies.md](strategies.md) の Strategy Matrix にまとめている。ここで重複して説明はしない。

## Correctness Assumptions

現在の window 系戦略は、入力順序に依存する。

```text
order_id asc, attribute_definition_id asc
```

同じ `order_id` が分断されると、同一注文が複数 CSV row として扱われる。

これはバグというより、ordered group fold の前提条件である。

また、`order_attribute_value` には DB 制約として次がある。

```text
unique(order_id, attribute_definition_id)
```

そのため、同一注文・同一属性定義の重複は通常発生しない。テスト上は Map の上書きにより「最後勝ち」になるが、正しい入力では制約により防がれる。

CSV では null / missing は空文字として扱う。

## What Sequence Does Not Solve

`Sequence` は有用である。

しかし、`Sequence` が解くのは「全件 materialize せずに逐次処理する」部分であり、次の制約は解けない。

- 固定ヘッダ CSV の $N \cdot A$ セル出力
- DB からどの形でデータを受け取るか
- 属性定義順に空セルを埋める処理
- CSV writer の allocation / escaping
- DB 側 aggregation と JVM 側 Map 構築の責務選択

したがって、このリポジトリの結論は「Sequence が銀の弾丸」ではない。

結論は、**出力形式とデータ形状から、DB / JVM / I/O の責務配置を選ぶ**ことである。
