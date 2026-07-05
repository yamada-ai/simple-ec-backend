package com.example.ec.domain.order

import java.time.LocalDateTime

/**
 * JSON aggregation 戦略向け: DB側で属性値をJSONB objectへ集約した読み取りモデル。
 */
data class OrderAttributeJsonRow(
    val orderId: Long,
    val customerId: Long,
    val customerName: String,
    val customerEmail: String,
    val orderDate: LocalDateTime,
    val attributes: Map<Long, String>
)
