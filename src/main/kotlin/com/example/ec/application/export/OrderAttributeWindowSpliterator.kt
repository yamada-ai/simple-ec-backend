package com.example.ec.application.export

import com.example.ec.domain.order.OrderAttributeJoinedRow
import java.util.Spliterator
import java.util.Spliterators
import java.util.function.Consumer

/**
 * JOIN 縦持ちストリームを 1注文=1要素 にまとめる Spliterator（spliterator-window 戦略用）。
 */
class OrderAttributeWindowSpliterator(
    private val iterator: Iterator<OrderAttributeJoinedRow>,
    private val definitionIds: List<Long>
) : Spliterators.AbstractSpliterator<List<String>>(Long.MAX_VALUE, Spliterator.ORDERED or Spliterator.NONNULL) {

    private var pendingRow: OrderAttributeJoinedRow? = null

    @Suppress("ReturnCount") // Spliterator の状態遷移を明示するため
    override fun tryAdvance(action: Consumer<in List<String>>): Boolean {
        var current = pendingRow
        if (current == null) {
            if (!iterator.hasNext()) return false
            current = iterator.next()
        }

        val orderId = current.orderId
        val valueMap = mutableMapOf<Long, String>()
        if (current.definitionId != null && current.value != null) {
            valueMap[current.definitionId] = current.value
        }

        while (iterator.hasNext()) {
            val row = iterator.next()
            if (row.orderId != orderId) {
                // 次の注文に到達したので、現在の注文を出力して次回用に保存
                pendingRow = row
                return emitCurrent(action, current, valueMap)
            }
            if (row.definitionId != null && row.value != null) {
                valueMap[row.definitionId] = row.value
            }
        }

        // 末尾の注文を出力
        pendingRow = null
        return emitCurrent(action, current, valueMap)
    }

    private fun emitCurrent(
        action: Consumer<in List<String>>,
        current: OrderAttributeJoinedRow,
        valueMap: Map<Long, String>
    ): Boolean {
        val values = definitionIds.map { defId -> valueMap[defId] ?: "" }
        action.accept(buildCsvRow(current, values))
        return true
    }
}
