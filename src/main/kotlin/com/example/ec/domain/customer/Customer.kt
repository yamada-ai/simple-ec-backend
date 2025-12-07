package com.example.ec.domain.customer

import com.example.ec.domain.shared.ID
import java.time.LocalDateTime

/**
 * 顧客エンティティ
 *
 * @property id 顧客ID
 * @property name 顧客名
 * @property email メールアドレス（ユニーク）
 * @property createdAt 作成日時
 */
data class Customer(
    val id: ID<Customer>,
    val name: String,
    val email: String,
    val createdAt: LocalDateTime
) {
    init {
        require(name.isNotBlank()) { "Customer name must not be blank" }
        require(email.isNotBlank()) { "Customer email must not be blank" }
        require(email.contains("@")) { "Customer email must be valid" }
    }
}
