package com.example.ec.presentation.controller

import com.example.ec.application.order.GetOrderDetailUseCase
import com.example.ec.application.order.OrderDetail
import com.example.ec.domain.order.Order
import com.example.ec.domain.order.OrderItem
import com.example.ec.domain.shared.ID
import com.example.ec.presentation.api.OrdersApi
import com.example.ec.presentation.model.CustomerSummary
import com.example.ec.presentation.model.OrderDetailResponse
import com.example.ec.presentation.model.OrderItemSummary
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime
import java.time.ZoneOffset

@RestController
class OrdersApiController(
    private val getOrderDetailUseCase: GetOrderDetailUseCase
) : OrdersApi {

    override fun getOrderDetail(orderId: Long): ResponseEntity<OrderDetailResponse> {
        val orderDetail = getOrderDetailUseCase.execute(ID(orderId))
            ?: return ResponseEntity.notFound().build()

        val response = orderDetail.toResponse()
        return ResponseEntity.ok(response)
    }
}

/**
 * OrderDetail を OrderDetailResponse に変換する
 */
private fun OrderDetail.toResponse(): OrderDetailResponse {
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

/**
 * OrderItem を OrderItemSummary に変換する
 */
private fun OrderItem.toResponse(): OrderItemSummary {
    return OrderItemSummary(
        id = id.value,
        productName = productName,
        quantity = quantity,
        unitPrice = unitPrice.value.toDouble()
    )
}
