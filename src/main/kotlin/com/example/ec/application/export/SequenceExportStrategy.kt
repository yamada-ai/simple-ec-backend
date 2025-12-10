package com.example.ec.application.export

import com.example.ec.domain.order.Order
import org.springframework.stereotype.Component
import java.util.stream.Stream
import kotlin.streams.asSequence
import kotlin.streams.asStream

/**
 * Kotlin Sequenceを使ったCSV出力戦略
 *
 * Stream → Sequence → flatMap → Stream の流れで1→多展開を行う
 */
@Component
class SequenceExportStrategy : ExportStrategy {
    override fun export(
        orders: Stream<Order>,
        getCustomerName: (Long) -> String,
        getCustomerEmail: (Long) -> String
    ): Stream<OrderCsvRow> {
        // Java StreamをKotlin Sequenceに変換
        val orderSequence = orders.asSequence()

        // Sequenceのflat MapでOrder→OrderItemsを展開
        val csvRowSequence = orderSequence.flatMap { order ->
            val customerId = order.customerId.value
            val customerName = getCustomerName(customerId)
            val customerEmail = getCustomerEmail(customerId)

            // 1つのOrderから複数のOrderItemsへ展開
            order.items.asSequence().map { item ->
                OrderCsvRow(
                    orderId = order.id.value,
                    orderDate = order.orderDate,
                    totalAmount = order.totalAmount.value,
                    customerId = customerId,
                    customerName = customerName,
                    customerEmail = customerEmail,
                    orderItemId = item.id.value,
                    productName = item.productName,
                    quantity = item.quantity,
                    unitPrice = item.unitPrice.value
                )
            }
        }

        // Kotlin SequenceをJava Streamに戻す
        return csvRowSequence.asStream()
    }
}
