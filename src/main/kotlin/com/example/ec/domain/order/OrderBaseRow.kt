package com.example.ec.domain.order

import java.time.LocalDateTime

/**
 * 属性値を含めない、注文＋顧客のベース行（preload戦略用）。
 */
data class OrderBaseRow(
    val orderId: Long,
    val customerId: Long,
    val customerName: String,
    val customerEmail: String,
    val orderDate: LocalDateTime
)
