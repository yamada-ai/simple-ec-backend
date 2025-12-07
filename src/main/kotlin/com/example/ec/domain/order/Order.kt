package com.example.ec.domain.order

import com.example.ec.domain.customer.Customer
import com.example.ec.domain.shared.ID
import com.example.ec.domain.shared.Price
import java.time.LocalDateTime

/**
 * 注文エンティティ（集約ルート）
 *
 * @property id 注文ID
 * @property customerId 顧客ID
 * @property orderDate 注文日時
 * @property totalAmount 合計金額
 * @property createdAt 作成日時
 * @property items 注文明細リスト（集約内に保持）
 */
data class Order(
    val id: ID<Order>,
    val customerId: ID<Customer>,
    val orderDate: LocalDateTime,
    val totalAmount: Price,
    val createdAt: LocalDateTime,
    val items: List<OrderItem> = emptyList()
) {
    /**
     * 注文明細リストから合計金額を計算する
     */
    fun calculateTotalAmount(): Price =
        items.fold(Price.ZERO) { acc, item -> acc + item.subtotal() }

    companion object {
        fun calculateTotalAmount(items: List<OrderItem>): Price =
            items.fold(Price.ZERO) { acc, item -> acc + item.subtotal() }
    }
}
