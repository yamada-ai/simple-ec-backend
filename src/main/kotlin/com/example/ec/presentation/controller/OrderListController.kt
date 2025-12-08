package com.example.ec.presentation.controller

import com.example.ec.application.order.GetOrdersUseCase
import com.example.ec.application.order.OrderListItemView
import com.example.ec.presentation.model.OrderListResponse
import com.example.ec.presentation.model.PagedOrderListResponse
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * 注文一覧取得コントローラ
 *
 * OpenAPI生成のOrdersApiインターフェースは仕様参照のみに使用し、
 * Spring MVCのルーティングは自前で定義することで、
 * APIごとにControllerを分割できる設計としている。
 */
@RestController
@RequestMapping("/api/orders")
class OrderListController(
    private val getOrdersUseCase: GetOrdersUseCase
) {

    @GetMapping
    fun getOrders(
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        from: LocalDateTime?,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        to: LocalDateTime?,
        @RequestParam(required = false)
        customerName: String?,
        @RequestParam(defaultValue = "0")
        page: Int,
        @RequestParam(defaultValue = "20")
        size: Int
    ): ResponseEntity<PagedOrderListResponse> {
        val ordersPage = getOrdersUseCase.execute(from, to, customerName, page, size)

        val response = PagedOrderListResponse(
            content = ordersPage.content.map { it.toResponse() },
            page = ordersPage.page,
            propertySize = ordersPage.size,
            totalPages = ordersPage.totalPages,
            totalElements = ordersPage.totalElements
        )

        return ResponseEntity.ok(response)
    }
}

/**
 * OrderListItemView を OrderListResponse に変換する
 */
private fun OrderListItemView.toResponse(): OrderListResponse {
    return OrderListResponse(
        id = id,
        customerName = customerName,
        orderDate = orderDate.atOffset(ZoneOffset.UTC),
        totalAmount = totalAmount.toDouble(),
        itemCount = itemCount
    )
}
