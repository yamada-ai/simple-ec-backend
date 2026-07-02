package com.example.ec.presentation.controller

import com.example.ec.application.order.GetOrderDetailUseCase
import com.example.ec.domain.shared.ID
import com.example.ec.presentation.mapper.toResponse
import com.example.ec.presentation.model.OrderDetailResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 注文詳細取得コントローラ
 *
 * OpenAPI生成のOrdersApiインターフェースは仕様参照のみに使用し、
 * Spring MVCのルーティングは自前で定義することで、
 * APIごとにControllerを分割できる設計としている。
 */
@RestController
@RequestMapping("/api/orders")
class OrderDetailController(
    private val getOrderDetailUseCase: GetOrderDetailUseCase
) {

    @GetMapping("/{orderId}")
    fun getOrderDetail(@PathVariable orderId: Long): ResponseEntity<OrderDetailResponse> {
        val orderDetail = getOrderDetailUseCase.execute(ID(orderId))
            ?: return ResponseEntity.notFound().build()

        val response = orderDetail.toResponse()
        return ResponseEntity.ok(response)
    }
}
