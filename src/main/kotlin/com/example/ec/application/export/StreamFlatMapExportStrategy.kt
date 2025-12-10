package com.example.ec.application.export

import com.example.ec.domain.order.Order
import org.springframework.stereotype.Component
import java.util.stream.Stream

/**
 * Java Stream + flatMapを使ったCSV出力戦略
 *
 * StreamのflatMapで1→多展開を行う（最も標準的なアプローチ）
 */
@Component
class StreamFlatMapExportStrategy : ExportStrategy {
    override fun export(
        orders: Stream<Order>,
        getCustomerName: (Long) -> String,
        getCustomerEmail: (Long) -> String
    ): Stream<OrderCsvRow> {
        // Java StreamのflatMapで Order → OrderItems を展開
        return orders.flatMap { order ->
            val customerId = order.customerId.value
            val customerName = getCustomerName(customerId)
            val customerEmail = getCustomerEmail(customerId)

            // 1つのOrderから複数のOrderItemsへ展開
            order.items.stream().map { item ->
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
    }
}
