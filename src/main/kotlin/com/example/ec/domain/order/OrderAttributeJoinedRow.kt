package com.example.ec.domain.order

import java.time.LocalDateTime

/**
 * 注文 + 顧客 + 属性値を JOIN した1行分のデータ（属性が無い場合は definitionId/value が null）。
 */
data class OrderAttributeJoinedRow(
    val orderId: Long,
    val customerId: Long,
    val customerName: String,
    val customerEmail: String,
    val orderDate: LocalDateTime,
    val definitionId: Long?,
    val definitionName: String?,
    val definitionLabel: String?,
    val value: String?
)
