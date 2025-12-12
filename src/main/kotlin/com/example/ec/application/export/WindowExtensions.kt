package com.example.ec.application.export

import com.example.ec.domain.order.OrderAttributeJoinedRow

/**
 * orderId が切り替わるタイミングで横展開した列リストを emit する Sequence 拡張。
 */
internal fun buildCsvRow(
    base: OrderAttributeJoinedRow,
    values: List<String>
): List<String> =
    listOf(
        base.orderId.toString(),
        base.customerId.toString(),
        base.customerName,
        base.customerEmail,
        base.orderDate.toString()
    ).plus(values)

fun Sequence<OrderAttributeJoinedRow>.windowByOrderId(
    definitionIds: List<Long>
): Sequence<List<String>> = sequence {
    // orderId が同じ間は valueMap に属性値を貯め、orderId が変わるタイミングで横展開行を yield する
    var currentOrderId: Long? = null
    var currentRow: OrderAttributeJoinedRow? = null
    val valueMap = mutableMapOf<Long, String>()

    for (row in this@windowByOrderId) {
        when {
            currentOrderId == null -> {
                currentOrderId = row.orderId
                currentRow = row
            }
            
            currentOrderId != row.orderId -> {
                val base = currentRow
                if (base != null) {
                    val values = definitionIds.map { defId -> valueMap[defId] ?: "" }
                    yield(buildCsvRow(base, values))
                    valueMap.clear()
                }
                currentOrderId = row.orderId
                currentRow = row
            }
        }

        if (row.definitionId != null && row.value != null) {
            valueMap[row.definitionId] = row.value
        }
    }

    val base = currentRow
    if (base != null) {
        val values = definitionIds.map { defId -> valueMap[defId] ?: "" }
        yield(buildCsvRow(base, values))
    }
}
