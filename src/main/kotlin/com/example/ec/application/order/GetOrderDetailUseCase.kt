package com.example.ec.application.order

import com.example.ec.domain.customer.Customer
import com.example.ec.domain.customer.CustomerRepository
import com.example.ec.domain.order.Order
import com.example.ec.domain.order.OrderItem
import com.example.ec.domain.order.OrderRepository
import com.example.ec.domain.shared.ID
import org.springframework.stereotype.Service

/**
 * 注文詳細取得ユースケース
 *
 * 注文、顧客、注文明細を結合して返す
 */
@Service
class GetOrderDetailUseCase(
    private val orderRepository: OrderRepository,
    private val customerRepository: CustomerRepository
) {
    /**
     * 注文詳細を取得する
     *
     * @param orderId 注文ID
     * @return 注文詳細、存在しない場合はnull
     * @throws IllegalStateException 顧客が存在しない場合（データ整合性エラー）
     */
    fun execute(orderId: ID<Order>): OrderDetail? {
        // 注文明細込みで注文を取得
        val order = orderRepository.findById(orderId) ?: return null

        // 顧客を取得（存在しない場合はデータ整合性エラー）
        val customer = customerRepository.findById(order.customerId)
            ?: error("Customer not found for order ${order.id.value}. Data integrity error.")

        return OrderDetail(
            order = order,
            customer = customer,
            items = order.items
        )
    }
}

/**
 * 注文詳細
 *
 * 注文、顧客、注文明細をまとめたDTO
 */
data class OrderDetail(
    val order: Order,
    val customer: Customer,
    val items: List<OrderItem>
)
