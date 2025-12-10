package com.example.ec.domain.attribute

import com.example.ec.domain.shared.ID
import java.time.LocalDateTime

/**
 * 注文属性値エンティティ
 *
 * Order集約内部のリスト要素として扱われる。
 * orderIdは持たず、Orderエンティティが属性値のリストを保持する。
 *
 * @property id 属性値ID
 * @property attributeDefinitionId 属性定義ID
 * @property value 属性値（文字列、最大255文字）
 * @property createdAt 作成日時
 */
data class OrderAttributeValue(
    val id: ID<OrderAttributeValue>,
    val attributeDefinitionId: ID<OrderAttributeDefinition>,
    val value: String,
    val createdAt: LocalDateTime
) {
    init {
        require(value.length <= MAX_VALUE_LENGTH) {
            "Attribute value must not exceed $MAX_VALUE_LENGTH characters"
        }
    }

    companion object {
        const val MAX_VALUE_LENGTH = 255
    }
}
