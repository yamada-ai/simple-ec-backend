package com.example.ec.application.order

import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * 注文一覧表示用のビューデータ
 *
 * N+1問題を避けるため、リポジトリ層で顧客名を含めて取得する
 */
data class OrderListItemView(
    val id: Long,
    val customerName: String,
    val orderDate: LocalDateTime,
    val totalAmount: BigDecimal,
    val itemCount: Int
)
