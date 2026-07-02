package com.example.ec.presentation.mapper

import com.example.ec.domain.attribute.OrderAttributeDefinition
import com.example.ec.presentation.model.AttributeDefinitionResponse
import java.time.ZoneOffset

fun OrderAttributeDefinition.toResponse(): AttributeDefinitionResponse {
    return AttributeDefinitionResponse(
        id = id.value,
        name = name,
        label = label,
        description = description,
        createdAt = createdAt.atOffset(ZoneOffset.UTC)
    )
}
