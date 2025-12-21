# 分析手法（Analysis Model）

本章では、動的列 CSV 出力問題を計算量モデルとして定式化し、各戦略が「どこでコストを支払うか」を比較可能な形に落とし込む。特に、本問題が純粋な CPU 計算ではなく、出力量（I/O）とデータ整形コストに支配されやすい点を明示する。

## 記号定義

以降、次の記号を用いる。

- $N$: 注文数（出力行数）
- $A$: 属性定義数（動的列の列数）
- $V$: 属性値の総数（`order_attribute_value` の行数）
- $\bar{a} = V/N$: 1注文あたり平均属性数
- $a_{\text{max}}$: 1注文あたり属性値数の最大（$a_{\text{max}} \le A$）
- $N_0$: 属性値が 0 件の注文数
- $R$: 縦持ち JOIN 結果（ストリーム）の行数
- $S$: 生成される CSV の総出力サイズ（文字数または bytes）
- $B$: 基本列数（`order_id`, `customer_id` 等の固定列、定数）

本稿の前提では、属性値を持たない注文も CSV 行として出力されるため、縦持ち取得に LEFT JOIN を用いる。このとき、JOIN 結果の行数 $R$ は

$$
R = V + N_0 \quad (0 \le N_0 \le N)
$$

を満たす。したがって

$$
V \le R \le V + N
$$

である。以後、窓処理（windowing）を用いる戦略はこの $R$ を基準に評価する。

## 出力下界（I/O による支配）

本問題の中心は「疎な属性値表現を、密な CSV 表へ再配置する」点にある。CSV は行ごとに列数が固定されるため、属性列だけでも 各注文につき $A$ セルを必ず出力する。

したがって、属性列に関して出力セル数は常に

$$
N \times A
$$

となり、少なくともこの数だけのセル生成・書き込みが必要である。よって、属性列展開に関して

$$
\Omega(N \cdot A)
$$

が下界となる。

さらに、CSV への書き込みは物理的な出力量 $S$ に比例するため、より根源的には

$$
\Omega(S)
$$

が避けられない下界である。

基本列数を $B$（定数）とすると、出力サイズは概ね

$$
S = \Theta(N(A + B))
$$

に比例する。ここでは各セルの値の平均文字数を定数とみなす近似のもとで議論する。値長そのものは出力サイズ $S$ に影響するが、戦略間の相対比較には影響しないためである。

したがって $\Omega(S)$ は $\Omega(N \cdot A)$ を包含する。

すなわち本問題は、アルゴリズム改善によって「列展開コスト」を消し去るタイプの問題ではない。設計上の論点は、不可避なコスト（特に $N \cdot A$ と $S$）を「どこで・どの形で」支払うかに集約される。

## 計測対象の分解：DB / JVM / I/O

戦略間の違いを明確にするため、総実行時間 $T$ を次の 3 成分に分解する。

$$
T = T_{\text{DB}} + T_{\text{app}} + T_{\text{IO}}
$$

- $T_{\text{DB}}$: DB 側処理（JOIN、ORDER BY、ネスト化/集約、materialization 等）
- $T_{\text{app}}$: JVM 側処理（Map 構築、窓処理、列埋め、オブジェクト生成等）
- $T_{\text{IO}}$: CSV 出力（文字列生成＋write）

メモリ使用量 $M$ はピーク使用量（peak memory usage）として扱い、特に JVM 側の保持量 $M_{\text{app}}$ を比較対象とする。

## 戦略の設計空間（分類）

本稿では、結合と列再配置の方法により戦略を次の 4 つに分類する。これらは実務上自然に現れる代表的な実装パターンを抽出し、設計空間を比較可能な形で整理したものである。本稿で扱う戦略は理論的に網羅的なものではなく、実務において実際に選択肢として現れやすい代表例に限定している。

### 1. PRELOAD
属性値を全件取得し、`Map<OrderId, Map<DefId, Value>>` に展開して参照

### 2. MULTISET
DB 側で「注文 → 属性値群」をネスト化して取得し、1注文単位で処理

### 3. SEQUENCE_WINDOW
縦持ち JOIN 結果を `Sequence` で順序前提の窓処理により 1注文=1要素へ昇格

### 4. SPLITERATOR_WINDOW
縦持ち JOIN 結果を `Spliterator` により 1注文=1要素へ昇格（Stream 世界で窓処理）

## 各戦略の理論モデル（概要）

ここではアプリ側の主要支配項を示し、戦略間の「形」を揃える。

### 1. PRELOAD

- 属性値 Map 構築: $O(V)$
- 注文走査＋列埋め: $O(N \cdot A)$

$$
T_{\text{app}} = O(V + N \cdot A)
$$

$$
M_{\text{app}} = O(V)
$$

全属性値を保持するためメモリ支配は $V$ になる。

### 2. WINDOW（SEQUENCE / SPLITERATOR）

窓処理の概念を擬似コードで示す：

```
buffer = empty map
previousOrderId = null

for row in joinedRowsSortedByOrderId:
    if row.orderId != previousOrderId and previousOrderId != null:
        emit(previousOrderId, buffer, allDefinitionIds)
        buffer.clear()

    if row.definitionId is not null:  // LEFT JOIN により属性の無い注文行をスキップ
        buffer[row.definitionId] = row.value
    previousOrderId = row.orderId

emit(previousOrderId, buffer, allDefinitionIds)  // 最後の注文
```

計算量：

- 縦持ち JOIN 結果を 1行ずつ処理: $O(R)$
- 注文確定時の列埋め: $O(N \cdot A)$

縦持ち行の逐次走査（$R$）と、行確定時の列埋め（$N \cdot A$）は独立に発生するため、時間計算量は足し合わせで表される。

$$
T_{\text{app}} = O(R + N \cdot A)
$$

$$
M_{\text{app}} = O(a_{\text{max}})
$$

平均は $\bar{a}$ だが、ピークは最大ウィンドウサイズ $a_{\text{max}}$ に支配される。

ここで $R = V + N_0$ であるため、JOIN 結果を逐次処理しつつも、最終的な列展開 $N \cdot A$ からは逃れられないことが明確になる。

### 3. MULTISET（アプリ側）

MULTISET では 1注文ごとに「属性値リスト（平均 $\bar{a}$）」を受け取り、列展開 $A$ と合わせて処理するため

$$
T_{\text{app}} = O(N \cdot (A + \bar{a}))
$$

である。ただし $\bar{a} = V/N$ を代入すると

$$
N \cdot (A + \bar{a}) = N \cdot A + V
$$

よって

$$
T_{\text{app}} = O(N \cdot A + V)
$$

$$
M_{\text{app}} = O(a_{\text{max}})
$$

となり、アプリ側の漸近形は PRELOAD と同型である。

ただし `MULTISET` は $T_{\text{DB}}$ の構造が異なり、結果の materialization やネスト集約によりストリーミング性が失われ得る点に注意が必要である。

## 理論モデルの統一と実測差の帰着

ここまでから、アプリ側に限れば多くの戦略は

$$
O(N \cdot A + V) \quad \text{または} \quad O(N \cdot A + R)
$$

という同一の支配形に収束する。

この意味で、WINDOW 系と MULTISET はアプリ側計算量の観点では同型であり、実測差は主として $T_{\text{DB}}$ と $M_{\text{app}}$ に帰着される。

したがって実測差は Big-O ではなく、主に次の要因に帰着される：

1. $T_{\text{DB}}$ の性質の違い（ORDER BY / ネスト化 / materialization の有無）
2. $M_{\text{app}}$ のピーク差（全件保持 $O(V)$ vs 逐次処理 $O(a_{\text{max}})$）
3. MULTISET における $T_{\text{DB}}$ の別種性（ネスト化に伴う materialization の可能性）
4. 定数項（オブジェクト生成、GC、変換、ライブラリのオーバーヘッド）
5. 不可避な $T_{\text{IO}} = \Omega(S)$

本章のモデルは、各戦略の漸近的優劣を示すものではなく、不可避なコストがどの層に帰属するかを比較するための枠組みである。

## 前編のまとめと次稿への問い

本稿（前編）では、動的列 CSV 出力問題を計算量モデルの観点から整理し、 `PRELOAD` / `MULTISET` / `WINDOW` 系戦略がどのような設計判断の差として現れるかを定式化した。

特に、各戦略の漸近的な計算量は概ね同型に収束する一方で、不可避なコストが **どの層（DB / JVM / I/O）に帰属するか** によって、実装上・運用上の特性が大きく異なり得ることを示した。

重要なのは、本問題が「どの戦略が理論的に速いか」ではなく、「不可避なコストをどこで支払うか」という設計問題である点である。

では、同じ Big-O に収束する戦略同士は、実装・実行環境・データ分布の違いによって、実際にはどのような差として現れるのか。

次稿（後編）では、Kotlin / Java による実装と実測を通して、この問いに答える。
