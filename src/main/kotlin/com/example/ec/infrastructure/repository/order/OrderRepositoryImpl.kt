package com.example.ec.infrastructure.repository.order

import com.example.ec.domain.customer.Customer
import com.example.ec.domain.order.Order
import com.example.ec.domain.order.OrderItem
import com.example.ec.domain.order.OrderRepository
import com.example.ec.domain.shared.ID
import com.example.ec.domain.shared.Page
import com.example.ec.domain.shared.Price
import com.example.ec.infrastructure.jooq.tables.records.OrderItemRecord
import com.example.ec.infrastructure.jooq.tables.records.OrderRecord
import com.example.ec.infrastructure.jooq.tables.references.ORDER
import com.example.ec.infrastructure.jooq.tables.references.ORDER_ITEM
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class OrderRepositoryImpl(
    private val dsl: DSLContext
) : OrderRepository {

    override fun findById(id: ID<Order>): Order? {
        val orderRecord = dsl.selectFrom(ORDER)
            .where(ORDER.ID.eq(id.value))
            .fetchOne()
            ?: return null

        val items = fetchItems(orderRecord.id!!)
        return convertToOrder(orderRecord, items)
    }

    override fun search(
        from: LocalDateTime?,
        to: LocalDateTime?,
        customerName: String?,
        page: Int,
        size: Int
    ): Page<Order> {
        // Phase 3 で実装予定
        TODO("Not yet implemented - will be implemented in Phase 3")
    }

    override fun count(): Long {
        return dsl.selectCount()
            .from(ORDER)
            .fetchOne(0, Long::class.java) ?: 0L
    }

    private fun fetchItems(orderId: Long): List<OrderItem> {
        return dsl.selectFrom(ORDER_ITEM)
            .where(ORDER_ITEM.ORDER_ID.eq(orderId))
            .fetch()
            .map { convertToOrderItem(it) }
    }

    private fun convertToOrder(record: OrderRecord, items: List<OrderItem>): Order {
        return Order(
            id = ID(record.id!!),
            customerId = ID(record.customerId!!),
            orderDate = record.orderDate!!,
            totalAmount = Price(record.totalAmount!!),
            createdAt = record.createdAt!!,
            items = items
        )
    }

    private fun convertToOrderItem(record: OrderItemRecord): OrderItem {
        return OrderItem(
            id = ID(record.id!!),
            productName = record.productName!!,
            quantity = record.quantity!!,
            unitPrice = Price(record.unitPrice!!),
            createdAt = record.createdAt!!
        )
    }
}
