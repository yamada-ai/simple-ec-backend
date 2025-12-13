package com.example.ec.application.export

import com.example.ec.domain.order.OrderAttributeJoinedRow
import java.util.Spliterator
import java.util.Spliterators
import java.util.function.Consumer

/**
 * JOIN縦持ちの行（OrderAttributeJoinedRow）を、orderId境界でグルーピングして
 * 「1注文=1要素（OrderAttributeCsvRow）」として流す Spliterator。
 *
 * 前提:
 * - 入力sourceは orderId 昇順（＋definitionId昇順）で並んでいること。
 *   これが崩れると「境界でまとめる」ことができず、行が壊れる。
 *
 * Spliteratorとしての方針:
 * - ORDERED は source が ORDERED のときだけ引き継ぐ（嘘を付かない）
 * - SIZED/SUBSIZED は「縦持ち→横持ち」で要素数が変わるので付与しない
 * - trySplit は実装しない（= 並列化しない）
 *   → 途中で split すると orderId の境界が壊れる可能性があるため、安全側で null を返す
 */
class OrderAttributeWindowSpliterator(
    private val source: Spliterator<OrderAttributeJoinedRow>,
) : Spliterators.AbstractSpliterator<OrderAttributeCsvRow>(
    Long.MAX_VALUE,
    // 「引き継げる特性だけ」引き継ぐ（特に ORDERED）
    (source.characteristics() and Spliterator.ORDERED) or Spliterator.NONNULL
) {
    /**
     * source.tryAdvance から 1行取り出すための一時バッファ。
     * Consumer を使うAPIなので、受け取った値をここに詰めて返す。
     */
    private var buffer: OrderAttributeJoinedRow? = null

    /**
     * 次の注文の先頭行を持ち越すためのバッファ。
     * orderId境界を超えた瞬間に「次回のtryAdvanceで使う先頭行」として保存する。
     */
    private var pendingRow: OrderAttributeJoinedRow? = null

    override fun trySplit(): Spliterator<OrderAttributeCsvRow>? {
        // orderId単位でまとまりを作る以上、安全に分割するのが難しい。
        // 無理に split すると「1注文が2つのスプリットに跨る」事故が起きうるため、並列化は諦める。
        return null
    }

    /**
     * source から1行だけ取り出す。
     * - 取り出せたらその行を返す
     * - 末尾なら null を返す
     */
    private fun nextRowOrNull(): OrderAttributeJoinedRow? {
        val advanced = source.tryAdvance { row -> buffer = row }
        return if (advanced) buffer.also { buffer = null } else null
    }

    @Suppress("ReturnCount") // 状態遷移を明示するため
    override fun tryAdvance(action: Consumer<in OrderAttributeCsvRow>): Boolean {
        // まず「今回処理する注文の先頭行」を確定させる（ここでは non-null を保証する）
        val first: OrderAttributeJoinedRow =
            pendingRow ?: nextRowOrNull() ?: return false

        pendingRow = null // 使い切ったのでクリア

        val orderId = first.orderId
        val attrMap = linkedMapOf<Long, String>() // 定義ID→値（最後に出た値で上書きでもOKならこれで十分）

        // 先頭行も属性を持っている場合がある
        first.definitionId?.let { defId ->
            first.value?.let { v -> attrMap[defId] = v }
        }

        // 3) 同じ orderId の間は読み続ける。境界を跨いだら pendingRow に保存して終了。
        var next: OrderAttributeJoinedRow? = nextRowOrNull()
        while (next != null && next.orderId == orderId) {
            next.definitionId?.let { defId ->
                next!!.value?.let { v -> attrMap[defId] = v }
            }
            next = nextRowOrNull()
        }
        // ループを抜けた next は「null(末尾)」か「次の注文の先頭」
        pendingRow = next

        // この orderId の 1行（1要素）を確定して emit する
        action.accept(
            OrderAttributeCsvRow(
                orderId = first.orderId,
                customerId = first.customerId,
                customerName = first.customerName,
                customerEmail = first.customerEmail,
                orderDate = first.orderDate,
                attributes = attrMap
            )
        )
        return true
    }
}
