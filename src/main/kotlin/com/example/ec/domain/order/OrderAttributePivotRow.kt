package com.example.ec.domain.order

import java.time.LocalDateTime

/**
 * SQL Pivot 戦略向け: DB側で横展開済みの属性値を持つ読み取りモデル。
 */
data class OrderAttributePivotRow(
    val orderId: Long,
    val customerId: Long,
    val customerName: String,
    val customerEmail: String,
    val orderDate: LocalDateTime,
    val attributes: Map<Long, String>
)
