package com.example.ec.application.export

import com.example.ec.domain.order.OrderAttributeJoinedRow

/**
 * orderId 境界で窓を切り、「1注文=1要素」にまとめて OrderAttributeCsvRow を yield する。
 *
 * 前提: 入力は orderId 昇順（できれば definitionId 昇順）。
 * - この前提が崩れると、同じ注文が分断されて誤った集約になる。
 *
 * 特性:
 * - 1注文あたり O(定義数) ではなく、実際は「その注文に存在する属性数」だけを保持する。
 * - 各注文の属性Mapは *コピーせず*、注文境界で新しいMapに差し替える（無駄な allocation を避ける）。
 */
fun Sequence<OrderAttributeJoinedRow>.windowByOrderId(): Sequence<OrderAttributeCsvRow> = sequence {
    var currentOrderId: Long? = null
    var currentBase: OrderAttributeJoinedRow? = null

    // 注文ごとに差し替える。clear しない（= yield 済みの Map を汚さない & コピーしない）
    var attrMap: MutableMap<Long, String> = linkedMapOf()

    suspend fun SequenceScope<OrderAttributeCsvRow>.flush() {
        val base = currentBase ?: return
        yield(OrderAttributeCsvRow.from(base, attrMap))
        attrMap = linkedMapOf()
    }

    for (row in this@windowByOrderId) {
        if (currentOrderId == null) {
            currentOrderId = row.orderId
            currentBase = row
        } else if (currentOrderId != row.orderId) {
            flush()
            currentOrderId = row.orderId
            currentBase = row
        }

        row.definitionId?.let { defId ->
            row.value?.let { v -> attrMap[defId] = v }
        }
    }

    flush()
}
