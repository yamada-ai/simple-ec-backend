package com.example.ec.domain.order

import com.example.ec.domain.attribute.OrderAttributeValue

/**
 * multiset 戦略向け: 1注文 + 属性値リストの読み取りモデル。
 */
data class OrderWithAttributes(
    val orderId: Long,
    val customerId: Long,
    val customerName: String,
    val customerEmail: String,
    val orderDate: java.time.LocalDateTime,
    val attributes: List<OrderAttributeValue>
)
