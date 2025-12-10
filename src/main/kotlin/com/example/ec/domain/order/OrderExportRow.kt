package com.example.ec.domain.order

import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * CSV出力用のフラットな行データ。
 * Order + Customer + OrderItem を1行に展開したもの。
 */
data class OrderExportRow(
    val orderId: Long,
    val customerId: Long,
    val customerName: String,
    val customerEmail: String,
    val orderDate: LocalDateTime,
    val productName: String,
    val quantity: Int,
    val unitPrice: BigDecimal
)
