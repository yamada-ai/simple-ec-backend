package com.example.ec.presentation.mapper

import com.example.ec.application.admin.AdminSummary
import com.example.ec.application.admin.SeedResult
import com.example.ec.application.admin.TruncateResult
import com.example.ec.presentation.model.AdminSummaryResponse
import com.example.ec.presentation.model.SeedDataResponse
import com.example.ec.presentation.model.TruncateDataResponse

fun AdminSummary.toResponse(): AdminSummaryResponse {
    return AdminSummaryResponse(
        customers = customers,
        orders = orders,
        orderItems = orderItems,
        attributeDefinitions = attributeDefinitions
    )
}

fun SeedResult.toResponse(): SeedDataResponse {
    return SeedDataResponse(
        customersCreated = customersCreated,
        ordersCreated = ordersCreated,
        orderItemsCreated = orderItemsCreated,
        attributeDefinitionsCreated = attributeDefinitionsCreated,
        attributeValuesCreated = attributeValuesCreated
    )
}

fun TruncateResult.toResponse(): TruncateDataResponse {
    return TruncateDataResponse(
        deleted = deleted,
        message = message
    )
}
