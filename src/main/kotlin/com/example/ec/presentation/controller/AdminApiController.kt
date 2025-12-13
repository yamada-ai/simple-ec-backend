package com.example.ec.presentation.controller

import com.example.ec.application.admin.GetAdminSummaryUseCase
import com.example.ec.application.admin.SeedDataUseCase
import com.example.ec.application.admin.TruncateDataUseCase
import com.example.ec.presentation.model.AdminSummaryResponse
import com.example.ec.presentation.model.SeedDataResponse
import com.example.ec.presentation.model.TruncateDataResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Admin API コントローラ（開発/テスト用）
 */
@RestController
@RequestMapping("/admin")
class AdminApiController(
    private val getAdminSummaryUseCase: GetAdminSummaryUseCase,
    private val seedDataUseCase: SeedDataUseCase,
    private val truncateDataUseCase: TruncateDataUseCase
) {

    @GetMapping("/summary")
    fun getAdminSummary(): ResponseEntity<AdminSummaryResponse> {
        val summary = getAdminSummaryUseCase.execute()

        val response = AdminSummaryResponse(
            customers = summary.customers,
            orders = summary.orders,
            orderItems = summary.orderItems
        )

        return ResponseEntity.ok(response)
    }

    @PostMapping("/seed")
    fun seedData(
        @RequestParam(defaultValue = "100") customers: Int,
        @RequestParam(defaultValue = "1000") orders: Int,
        @RequestParam(defaultValue = "0") attrs: Int,
        @RequestParam(required = false) seed: Long?
    ): ResponseEntity<SeedDataResponse> {
        val result = seedDataUseCase.execute(customers, orders, attrs, seed)

        val response = SeedDataResponse(
            customersCreated = result.customersCreated,
            ordersCreated = result.ordersCreated,
            orderItemsCreated = result.orderItemsCreated,
            attributeDefinitionsCreated = result.attributeDefinitionsCreated,
            attributeValuesCreated = result.attributeValuesCreated
        )

        return ResponseEntity.ok(response)
    }

    @DeleteMapping("/truncate")
    fun truncateData(): ResponseEntity<TruncateDataResponse> {
        val result = truncateDataUseCase.execute()

        val response = TruncateDataResponse(
            deleted = result.deleted,
            message = result.message
        )

        return ResponseEntity.ok(response)
    }
}
