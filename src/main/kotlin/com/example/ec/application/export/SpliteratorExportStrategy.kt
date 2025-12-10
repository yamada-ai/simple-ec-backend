package com.example.ec.application.export

import com.example.ec.domain.order.Order
import org.springframework.stereotype.Component
import java.util.Spliterator
import java.util.Spliterators
import java.util.function.Consumer
import java.util.stream.Stream
import java.util.stream.StreamSupport

/**
 * カスタムSpliteratorを使ったCSV出力戦略
 *
 * Spliteratorを自作することで、ストリームの生成を完全に制御する。
 * より低レベルなアプローチだが、細かい最適化が可能。
 */
@Component
class SpliteratorExportStrategy : ExportStrategy {
    override fun export(
        orders: Stream<Order>,
        getCustomerName: (Long) -> String,
        getCustomerEmail: (Long) -> String
    ): Stream<OrderCsvRow> {
        val orderIterator = orders.iterator()
        val spliterator = OrderCsvRowSpliterator(orderIterator, getCustomerName, getCustomerEmail)
        return StreamSupport.stream(spliterator, false)
    }

    /**
     * Order → OrderCsvRow への1→多展開を行うカスタムSpliterator
     */
    private class OrderCsvRowSpliterator(
        private val orderIterator: Iterator<Order>,
        private val getCustomerName: (Long) -> String,
        private val getCustomerEmail: (Long) -> String
    ) : Spliterators.AbstractSpliterator<OrderCsvRow>(
        Long.MAX_VALUE,
        Spliterator.ORDERED or Spliterator.NONNULL
    ) {
        // 現在処理中のOrderのItemsイテレータ
        private var currentItemsIterator: Iterator<Pair<Order, com.example.ec.domain.order.OrderItem>>? = null

        @Suppress("ReturnCount") // Spliteratorの実装では複数のreturnが必要
        override fun tryAdvance(action: Consumer<in OrderCsvRow>): Boolean {
            // 現在のOrderのItemsをまだ処理中の場合
            if (currentItemsIterator?.hasNext() == true) {
                val (order, item) = currentItemsIterator!!.next()
                action.accept(createCsvRow(order, item))
                return true
            }

            // 次のOrderを取得
            if (!orderIterator.hasNext()) {
                return false
            }

            val nextOrder = orderIterator.next()
            // OrderのItemsをイテレータに変換（Order情報も一緒に保持）
            currentItemsIterator = nextOrder.items.map { item -> nextOrder to item }.iterator()

            // 最初のItemを処理
            if (currentItemsIterator!!.hasNext()) {
                val (order, item) = currentItemsIterator!!.next()
                action.accept(createCsvRow(order, item))
                return true
            }

            // Itemsが空の場合は次のOrderへ（再帰的に）
            return tryAdvance(action)
        }

        private fun createCsvRow(order: Order, item: com.example.ec.domain.order.OrderItem): OrderCsvRow {
            val customerId = order.customerId.value
            return OrderCsvRow(
                orderId = order.id.value,
                orderDate = order.orderDate,
                totalAmount = order.totalAmount.value,
                customerId = customerId,
                customerName = getCustomerName(customerId),
                customerEmail = getCustomerEmail(customerId),
                orderItemId = item.id.value,
                productName = item.productName,
                quantity = item.quantity,
                unitPrice = item.unitPrice.value
            )
        }
    }
}
