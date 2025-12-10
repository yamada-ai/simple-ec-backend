package com.example.ec.application.export

import com.example.ec.domain.order.Order
import org.springframework.stereotype.Component
import java.util.stream.Stream

/**
 * Java Stream + mapMultiを使ったCSV出力戦略
 *
 * Java 16+で導入されたmapMultiを使用。
 * flatMapより効率的な場合がある（中間Streamの生成を避けられる）
 */
@Component
class StreamMapMultiExportStrategy : ExportStrategy {
    override fun export(
        orders: Stream<Order>,
        getCustomerName: (Long) -> String,
        getCustomerEmail: (Long) -> String
    ): Stream<OrderCsvRow> {
        // Java StreamのmapMultiで Order → OrderItems を展開
        return orders.mapMulti { order, downstream ->
            val customerId = order.customerId.value
            val customerName = getCustomerName(customerId)
            val customerEmail = getCustomerEmail(customerId)

            // 1つのOrderから複数のOrderItemsへ展開
            // Consumer（downstream）に直接要素を送り込む
            order.items.forEach { item ->
                val csvRow = OrderCsvRow(
                    orderId = order.id.value,
                    customerId = customerId,
                    customerName = customerName,
                    customerEmail = customerEmail,
                    orderDate = order.orderDate,
                    productName = item.productName,
                    quantity = item.quantity,
                    unitPrice = item.unitPrice.value
                )
                downstream.accept(csvRow)
            }
        }
    }
}
