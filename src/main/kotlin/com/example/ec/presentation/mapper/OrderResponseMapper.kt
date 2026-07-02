package com.example.ec.presentation.mapper

import com.example.ec.application.order.OrderDetail
import com.example.ec.domain.order.OrderItem
import com.example.ec.domain.order.OrderListItem
import com.example.ec.presentation.model.CustomerSummary
import com.example.ec.presentation.model.OrderDetailResponse
import com.example.ec.presentation.model.OrderItemSummary
import com.example.ec.presentation.model.OrderListResponse
import java.time.ZoneOffset

fun OrderListItem.toResponse(): OrderListResponse {
    return OrderListResponse(
        id = id,
        customerName = customerName,
        orderDate = orderDate.atOffset(ZoneOffset.UTC),
        totalAmount = totalAmount.toDouble(),
        itemCount = itemCount
    )
}

fun OrderDetail.toResponse(): OrderDetailResponse {
    return OrderDetailResponse(
        id = order.id.value,
        customer = CustomerSummary(
            id = customer.id.value,
            name = customer.name,
            email = customer.email.value
        ),
        orderDate = order.orderDate.atOffset(ZoneOffset.UTC),
        totalAmount = order.totalAmount.value.toDouble(),
        items = items.map { it.toResponse() },
        createdAt = order.createdAt.atOffset(ZoneOffset.UTC)
    )
}

private fun OrderItem.toResponse(): OrderItemSummary {
    return OrderItemSummary(
        id = id.value,
        productName = productName,
        quantity = quantity,
        unitPrice = unitPrice.value.toDouble()
    )
}
