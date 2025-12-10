package com.example.ec.application.export

import com.example.ec.domain.customer.CustomerRepository
import com.example.ec.domain.order.Order
import com.example.ec.domain.order.OrderRepository
import com.example.ec.domain.shared.ID
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.stream.Stream

/**
 * 注文データをCSV形式でエクスポートするサービス
 *
 * Phase 1: SQL JOIN で Order + OrderItem を取得し、Stream で展開
 * （単純なケースでは戦略比較不要）
 */
@Service
class OrderExportService(
    private val orderRepository: OrderRepository,
    private val customerRepository: CustomerRepository
) {
    /**
     * 注文データをCSV行としてエクスポートする
     *
     * @param from 注文日の開始日時（inclusive）
     * @param to 注文日の終了日時（inclusive）
     * @return CSV行のストリーム
     */
    fun exportOrders(
        from: LocalDateTime?,
        to: LocalDateTime?
    ): Stream<OrderCsvRow> {
        // OrderRepositoryからOrderのストリームを取得（SQL JOINで取得済み）
        val orders = orderRepository.streamOrdersForExport(from, to)

        // Customer情報を取得する関数（キャッシュ付き）
        // Note: ここでN+1が発生するが、ストリーミング処理なのでメモリは節約される
        // 実運用では、顧客情報もJOINするか、キャッシュ層を挟む等の最適化が必要
        val customerCache = mutableMapOf<Long, Pair<String, String>>()
        val getCustomer: (Long) -> Pair<String, String> = { customerId ->
            customerCache.getOrPut(customerId) {
                val customer = customerRepository.findById(ID(customerId))
                (customer?.name ?: "Unknown") to (customer?.email?.value ?: "unknown@example.com")
            }
        }

        // Java Stream の flatMap で Order → OrderItems を展開
        return orders.flatMap { order ->
            val customerId = order.customerId.value
            val (customerName, customerEmail) = getCustomer(customerId)

            // 1つのOrderから複数のOrderItemsへ展開
            order.items.stream().map { item ->
                OrderCsvRow(
                    orderId = order.id.value,
                    customerId = customerId,
                    customerName = customerName,
                    customerEmail = customerEmail,
                    orderDate = order.orderDate,
                    productName = item.productName,
                    quantity = item.quantity,
                    unitPrice = item.unitPrice.value
                )
            }
        }
    }
}
