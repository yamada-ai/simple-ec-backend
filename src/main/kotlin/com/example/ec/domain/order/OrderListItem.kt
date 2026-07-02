package com.example.ec.domain.order

import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * 注文一覧表示用の読み取りモデル。
 *
 * N+1問題を避けるため、リポジトリ層で顧客名と明細数を含めて取得する。
 */
data class OrderListItem(
    val id: Long,
    val customerName: String,
    val orderDate: LocalDateTime,
    val totalAmount: BigDecimal,
    val itemCount: Int
)
