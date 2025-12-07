package com.example.ec.domain.order

import com.example.ec.domain.shared.ID
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * 注文明細エンティティ
 *
 * @property id 注文明細ID
 * @property orderId 注文ID
 * @property productName 商品名
 * @property quantity 数量
 * @property unitPrice 単価
 * @property createdAt 作成日時
 */
data class OrderItem(
    val id: ID<OrderItem>,
    val orderId: ID<Order>,
    val productName: String,
    val quantity: Int,
    val unitPrice: BigDecimal,
    val createdAt: LocalDateTime
) {
    init {
        require(productName.isNotBlank()) { "Product name must not be blank" }
        require(quantity > 0) { "Quantity must be greater than 0" }
        require(unitPrice >= BigDecimal.ZERO) { "Unit price must be non-negative" }
    }

    /**
     * 小計金額を計算する
     */
    fun subtotal(): BigDecimal = unitPrice.multiply(BigDecimal(quantity))
}
