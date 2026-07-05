package com.example.ec.application.export

import com.example.ec.domain.order.OrderAttributeJoinedRow
import com.example.ec.domain.order.OrderBaseRow
import com.example.ec.domain.order.OrderAttributePivotRow
import com.example.ec.domain.order.OrderWithAttributes
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
    fun toRecord(schema: OrderAttributeExportSchema): List<String> {
        val values = schema.definitionIds.map { defId -> attributes[defId] ?: "" }
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

fun OrderBaseRow.toCsvRow(attributeValues: Map<Long, String>): OrderAttributeCsvRow {
    return OrderAttributeCsvRow(
        orderId = orderId,
        customerId = customerId,
        customerName = customerName,
        customerEmail = customerEmail,
        orderDate = orderDate,
        attributes = attributeValues
    )
}

fun OrderWithAttributes.toCsvRow(): OrderAttributeCsvRow {
    return OrderAttributeCsvRow(
        orderId = orderId,
        customerId = customerId,
        customerName = customerName,
        customerEmail = customerEmail,
        orderDate = orderDate,
        attributes = attributes.associate { it.attributeDefinitionId.value to it.value }
    )
}

fun OrderAttributePivotRow.toCsvRow(): OrderAttributeCsvRow {
    return OrderAttributeCsvRow(
        orderId = orderId,
        customerId = customerId,
        customerName = customerName,
        customerEmail = customerEmail,
        orderDate = orderDate,
        attributes = attributes
    )
}
