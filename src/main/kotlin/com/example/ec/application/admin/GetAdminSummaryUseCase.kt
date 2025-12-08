package com.example.ec.application.admin

import com.example.ec.domain.customer.CustomerRepository
import com.example.ec.domain.order.OrderRepository
import org.springframework.stereotype.Service

/**
 * Admin データサマリ取得ユースケース
 */
@Service
class GetAdminSummaryUseCase(
    private val customerRepository: CustomerRepository,
    private val orderRepository: OrderRepository
) {
    /**
     * データサマリを取得する
     *
     * @return データサマリ
     */
    fun execute(): AdminSummary {
        return AdminSummary(
            customers = customerRepository.count(),
            orders = orderRepository.count(),
            orderItems = orderRepository.countOrderItems()
        )
    }
}

/**
 * Adminデータサマリ
 */
data class AdminSummary(
    val customers: Long,
    val orders: Long,
    val orderItems: Long
)
