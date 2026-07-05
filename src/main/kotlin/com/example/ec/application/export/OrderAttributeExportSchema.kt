package com.example.ec.application.export

import com.example.ec.domain.attribute.OrderAttributeDefinition

data class OrderAttributeExportSchema(
    val definitionIds: List<Long>,
    val attributeLabels: List<String>
) {
    val header: List<String> = BASE_HEADER + attributeLabels
    val attributeColumnCount: Int = definitionIds.size

    companion object {
        private val BASE_HEADER = listOf("order_id", "customer_id", "customer_name", "customer_email", "order_date")

        fun from(definitions: List<OrderAttributeDefinition>): OrderAttributeExportSchema {
            val sortedDefinitions = definitions.sortedBy { it.id.value }
            return OrderAttributeExportSchema(
                definitionIds = sortedDefinitions.map { it.id.value },
                attributeLabels = sortedDefinitions.map { it.label }
            )
        }
    }
}
