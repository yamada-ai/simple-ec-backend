package com.example.ec.infrastructure.repository.order

import com.example.ec.application.order.OrderListItemView
import com.example.ec.domain.customer.Customer
import com.example.ec.domain.order.Order
import com.example.ec.domain.order.OrderItem
import com.example.ec.domain.order.OrderRepository
import com.example.ec.domain.shared.ID
import com.example.ec.domain.shared.Page
import com.example.ec.domain.shared.Price
import com.example.ec.infrastructure.jooq.tables.records.OrderItemRecord
import com.example.ec.infrastructure.jooq.tables.records.OrderRecord
import com.example.ec.infrastructure.jooq.tables.references.CUSTOMER
import com.example.ec.infrastructure.jooq.tables.references.ORDER
import com.example.ec.infrastructure.jooq.tables.references.ORDER_ITEM
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.stream.Stream

@Repository
@Suppress("TooManyFunctions") // Repository層は多くのメソッドを持つことが一般的
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
        val whereCondition = buildSearchCondition(from, to, customerName)

        // 総件数を取得
        val totalElements = dsl.selectCount()
            .from(ORDER)
            .join(CUSTOMER).on(ORDER.CUSTOMER_ID.eq(CUSTOMER.ID))
            .where(whereCondition)
            .fetchOne(0, Long::class.java) ?: 0L

        // データ取得
        val orderRecords = dsl.select(ORDER.fields().toList())
            .from(ORDER)
            .join(CUSTOMER).on(ORDER.CUSTOMER_ID.eq(CUSTOMER.ID))
            .where(whereCondition)
            .orderBy(ORDER.ORDER_DATE.desc(), ORDER.ID.desc())
            .limit(size)
            .offset(page * size)
            .fetch()
            .into(ORDER)

        // 全てのOrderItemsを一度に取得してグルーピング（N+1問題を回避）
        val orderIds = orderRecords.map { it.id!! }
        val itemsByOrderId = if (orderIds.isNotEmpty()) {
            dsl.selectFrom(ORDER_ITEM)
                .where(ORDER_ITEM.ORDER_ID.`in`(orderIds))
                .fetch()
                .groupBy { it.orderId!! }
                .mapValues { (_, records) -> records.map { convertToOrderItem(it) } }
        } else {
            emptyMap()
        }

        // OrderエンティティにItemsを含めて変換
        val orders = orderRecords.map { record ->
            val items = itemsByOrderId[record.id!!] ?: emptyList()
            convertToOrder(record, items)
        }

        return Page(
            content = orders,
            page = page,
            size = size,
            totalElements = totalElements
        )
    }

    override fun searchForList(
        from: LocalDateTime?,
        to: LocalDateTime?,
        customerName: String?,
        page: Int,
        size: Int
    ): Page<OrderListItemView> {
        val whereCondition = buildSearchCondition(from, to, customerName)

        // 総件数を取得
        val totalElements = dsl.selectCount()
            .from(ORDER)
            .join(CUSTOMER).on(ORDER.CUSTOMER_ID.eq(CUSTOMER.ID))
            .where(whereCondition)
            .fetchOne(0, Long::class.java) ?: 0L

        // データ取得（顧客名とアイテム数を含む）
        val itemCountSubquery = DSL.select(DSL.count())
            .from(ORDER_ITEM)
            .where(ORDER_ITEM.ORDER_ID.eq(ORDER.ID))
            .asField<Int>("item_count")

        val results = dsl.select(
            ORDER.ID,
            CUSTOMER.NAME,
            ORDER.ORDER_DATE,
            ORDER.TOTAL_AMOUNT,
            itemCountSubquery
        )
            .from(ORDER)
            .join(CUSTOMER).on(ORDER.CUSTOMER_ID.eq(CUSTOMER.ID))
            .where(whereCondition)
            .orderBy(ORDER.ORDER_DATE.desc(), ORDER.ID.desc())
            .limit(size)
            .offset(page * size)
            .fetch()

        val orderListItems = results.map { record ->
            OrderListItemView(
                id = record.get(ORDER.ID)!!,
                customerName = record.get(CUSTOMER.NAME) ?: "Unknown",
                orderDate = record.get(ORDER.ORDER_DATE)!!,
                totalAmount = record.get(ORDER.TOTAL_AMOUNT)!!,
                itemCount = record.get(itemCountSubquery) ?: 0
            )
        }

        return Page(
            content = orderListItems,
            page = page,
            size = size,
            totalElements = totalElements
        )
    }

    override fun count(): Long {
        return dsl.selectCount()
            .from(ORDER)
            .fetchOne(0, Long::class.java) ?: 0L
    }

    override fun countOrderItems(): Long {
        return dsl.selectCount()
            .from(ORDER_ITEM)
            .fetchOne(0, Long::class.java) ?: 0L
    }

    override fun truncate() {
        // 外部キー制約があるため、order_itemから先に削除
        dsl.deleteFrom(ORDER_ITEM).execute()
        dsl.deleteFrom(ORDER).execute()
    }

    override fun saveAll(orders: List<Order>): List<Order> {
        return orders.map { order ->
            // Orderレコードを保存
            val orderId = dsl.insertInto(ORDER)
                .set(ORDER.CUSTOMER_ID, order.customerId.value)
                .set(ORDER.ORDER_DATE, order.orderDate)
                .set(ORDER.TOTAL_AMOUNT, order.totalAmount.value)
                .set(ORDER.CREATED_AT, order.createdAt)
                .returningResult(ORDER.ID)
                .fetchOne()
                ?.value1()
                ?: error("Failed to insert order")

            // OrderItemレコードを保存
            val savedItems = order.items.map { item ->
                val itemId = dsl.insertInto(ORDER_ITEM)
                    .set(ORDER_ITEM.ORDER_ID, orderId)
                    .set(ORDER_ITEM.PRODUCT_NAME, item.productName)
                    .set(ORDER_ITEM.QUANTITY, item.quantity)
                    .set(ORDER_ITEM.UNIT_PRICE, item.unitPrice.value)
                    .set(ORDER_ITEM.CREATED_AT, item.createdAt)
                    .returningResult(ORDER_ITEM.ID)
                    .fetchOne()
                    ?.value1()
                    ?: error("Failed to insert order item")

                item.copy(id = ID(itemId))
            }

            order.copy(id = ID(orderId), items = savedItems)
        }
    }

    private fun buildSearchCondition(
        from: LocalDateTime?,
        to: LocalDateTime?,
        customerName: String?
    ): Condition {
        val conditions = mutableListOf<Condition>()

        from?.let {
            conditions.add(ORDER.ORDER_DATE.greaterOrEqual(it))
        }

        to?.let {
            conditions.add(ORDER.ORDER_DATE.lessOrEqual(it))
        }

        customerName?.let {
            conditions.add(CUSTOMER.NAME.containsIgnoreCase(it))
        }

        return if (conditions.isEmpty()) {
            DSL.noCondition()
        } else {
            DSL.and(conditions)
        }
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

    override fun streamOrdersForExport(
        from: LocalDateTime?,
        to: LocalDateTime?
    ): Stream<Order> {
        val conditions = mutableListOf<Condition>()

        from?.let {
            conditions.add(ORDER.ORDER_DATE.greaterOrEqual(it))
        }

        to?.let {
            conditions.add(ORDER.ORDER_DATE.lessOrEqual(it))
        }

        val whereCondition = if (conditions.isEmpty()) {
            DSL.noCondition()
        } else {
            DSL.and(conditions)
        }

        // jOOQのfetchStream()で遅延評価のStreamを取得
        val orderStream = dsl.selectFrom(ORDER)
            .where(whereCondition)
            .orderBy(ORDER.ORDER_DATE.desc(), ORDER.ID.desc())
            .fetchStream()

        // 各OrderについてOrderItemsを取得してOrderエンティティに変換
        // この時点ではN+1が発生するが、ストリーミング処理なのでメモリは節約される
        // 各Strategyで異なるアプローチを試す
        return orderStream.map { orderRecord ->
            val items = fetchItems(orderRecord.id!!)
            convertToOrder(orderRecord, items)
        }
    }
}
