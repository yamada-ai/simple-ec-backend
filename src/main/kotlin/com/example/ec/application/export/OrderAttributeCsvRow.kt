package com.example.ec.application.export

import com.example.ec.domain.order.OrderAttributeJoinedRow
import java.time.LocalDateTime

/**
 * CSV出力用の行モデル。I/Oと変換を分離するため、CSVPrinterにはこの型を介して渡す。
 */
data class OrderAttributeCsvRow(
    val orderId: Long,
    val customerId: Long,
    val customerName: String,
    val customerEmail: String,
    val orderDate: LocalDateTime,
    val attributes: Map<Long, String>
) {
    fun toRecord(definitionIds: List<Long>): List<String> {
        val values = definitionIds.map { defId -> attributes[defId] ?: "" }
        return listOf(
            orderId.toString(),
            customerId.toString(),
            customerName,
            customerEmail,
            orderDate.toString()
        ) + values
    }

    companion object {
        fun from(base: OrderAttributeJoinedRow, attributeValues: Map<Long, String>): OrderAttributeCsvRow =
            OrderAttributeCsvRow(
                orderId = base.orderId,
                customerId = base.customerId,
                customerName = base.customerName,
                customerEmail = base.customerEmail,
                orderDate = base.orderDate,
                attributes = attributeValues
            )
    }
}
