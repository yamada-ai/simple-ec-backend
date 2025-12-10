package com.example.ec.domain.attribute

import com.example.ec.domain.shared.ID
import java.time.LocalDateTime

/**
 * 注文属性定義エンティティ
 *
 * ユーザが定義できるカスタム項目の定義を管理する独立集約。
 * 例：ギフト包装種別、配送指示、キャンペーンID など
 *
 * @property id 属性定義ID
 * @property name 内部名（一意、例: gift_wrapping_type）
 * @property label 表示名（例: ギフト包装種別）
 * @property description 説明
 * @property createdAt 作成日時
 */
data class OrderAttributeDefinition(
    val id: ID<OrderAttributeDefinition>,
    val name: String,
    val label: String,
    val description: String?,
    val createdAt: LocalDateTime
) {
    init {
        require(name.isNotBlank()) { "Attribute name must not be blank" }
        require(name.length <= MAX_NAME_LENGTH) { "Attribute name must not exceed $MAX_NAME_LENGTH characters" }
        require(label.isNotBlank()) { "Attribute label must not be blank" }
        require(label.length <= MAX_LABEL_LENGTH) { "Attribute label must not exceed $MAX_LABEL_LENGTH characters" }
        require(description == null || description.length <= MAX_DESCRIPTION_LENGTH) {
            "Attribute description must not exceed $MAX_DESCRIPTION_LENGTH characters"
        }
    }

    companion object {
        const val MAX_NAME_LENGTH = 100
        const val MAX_LABEL_LENGTH = 100
        const val MAX_DESCRIPTION_LENGTH = 255
    }
}
