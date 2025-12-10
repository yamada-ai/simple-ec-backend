package com.example.ec.infrastructure.repository.order

import com.example.ec.application.order.OrderListItemView
import com.example.ec.domain.attribute.OrderAttributeDefinition
import com.example.ec.domain.attribute.OrderAttributeValue
import com.example.ec.domain.customer.Customer
import com.example.ec.domain.order.Order
import com.example.ec.domain.order.OrderAttributeJoinedRow
import com.example.ec.domain.order.OrderExportRow
import com.example.ec.domain.order.OrderItem
import com.example.ec.domain.order.OrderRepository
import com.example.ec.domain.shared.ID
import com.example.ec.domain.shared.Page
import com.example.ec.domain.shared.Price
import com.example.ec.infrastructure.jooq.tables.records.OrderAttributeValueRecord
import com.example.ec.infrastructure.jooq.tables.records.OrderItemRecord
import com.example.ec.infrastructure.jooq.tables.records.OrderRecord
import com.example.ec.infrastructure.jooq.tables.references.CUSTOMER
import com.example.ec.infrastructure.jooq.tables.references.ORDER
import com.example.ec.infrastructure.jooq.tables.references.ORDER_ATTRIBUTE_DEFINITION
import com.example.ec.infrastructure.jooq.tables.references.ORDER_ATTRIBUTE_VALUE
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
        val attributes = fetchAttributes(orderRecord.id!!)
        return convertToOrder(orderRecord, items, attributes)
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

        // 全てのOrderAttributeValuesを一度に取得してグルーピング（N+1問題を回避）
        val attributesByOrderId = if (orderIds.isNotEmpty()) {
            dsl.selectFrom(ORDER_ATTRIBUTE_VALUE)
                .where(ORDER_ATTRIBUTE_VALUE.ORDER_ID.`in`(orderIds))
                .fetch()
                .groupBy { it.orderId!! }
                .mapValues { (_, records) -> records.map { convertToOrderAttributeValue(it) } }
        } else {
            emptyMap()
        }

        // OrderエンティティにItemsとAttributesを含めて変換
        val orders = orderRecords.map { record ->
            val items = itemsByOrderId[record.id!!] ?: emptyList()
            val attributes = attributesByOrderId[record.id!!] ?: emptyList()
            convertToOrder(record, items, attributes)
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
        // 外部キー制約があるため、子テーブルから先に削除
        dsl.deleteFrom(ORDER_ATTRIBUTE_VALUE).execute()
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

            // OrderAttributeValueレコードを保存
            val savedAttributes = order.attributes.map { attr ->
                val attrId = dsl.insertInto(ORDER_ATTRIBUTE_VALUE)
                    .set(ORDER_ATTRIBUTE_VALUE.ORDER_ID, orderId)
                    .set(ORDER_ATTRIBUTE_VALUE.ATTRIBUTE_DEFINITION_ID, attr.attributeDefinitionId.value)
                    .set(ORDER_ATTRIBUTE_VALUE.VALUE, attr.value)
                    .set(ORDER_ATTRIBUTE_VALUE.CREATED_AT, attr.createdAt)
                    .returningResult(ORDER_ATTRIBUTE_VALUE.ID)
                    .fetchOne()
                    ?.value1()
                    ?: error("Failed to insert order attribute value")

                attr.copy(id = ID(attrId))
            }

            order.copy(id = ID(orderId), items = savedItems, attributes = savedAttributes)
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

    private fun fetchAttributes(orderId: Long): List<OrderAttributeValue> {
        return dsl.selectFrom(ORDER_ATTRIBUTE_VALUE)
            .where(ORDER_ATTRIBUTE_VALUE.ORDER_ID.eq(orderId))
            .fetch()
            .map { convertToOrderAttributeValue(it) }
    }

    private fun convertToOrder(
        record: OrderRecord,
        items: List<OrderItem>,
        attributes: List<OrderAttributeValue> = emptyList()
    ): Order {
        return Order(
            id = ID(record.id!!),
            customerId = ID(record.customerId!!),
            orderDate = record.orderDate!!,
            totalAmount = Price(record.totalAmount!!),
            createdAt = record.createdAt!!,
            items = items,
            attributes = attributes
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

    private fun convertToOrderAttributeValue(record: OrderAttributeValueRecord): OrderAttributeValue {
        return OrderAttributeValue(
            id = ID(record.id!!),
            attributeDefinitionId = ID(record.attributeDefinitionId!!),
            value = record.value!!,
            createdAt = record.createdAt!!
        )
    }

    override fun streamOrdersForExport(
        from: LocalDateTime?,
        to: LocalDateTime?
    ): Stream<OrderExportRow> {
        val whereCondition = buildSearchCondition(from, to, null)

        // Order + Customer + OrderItem を1クエリで取得し、N+1を解消
        return dsl.select(
            ORDER.ID,
            ORDER.CUSTOMER_ID,
            CUSTOMER.NAME,
            CUSTOMER.EMAIL,
            ORDER.ORDER_DATE,
            ORDER_ITEM.PRODUCT_NAME,
            ORDER_ITEM.QUANTITY,
            ORDER_ITEM.UNIT_PRICE
        )
            .from(ORDER)
            .join(CUSTOMER).on(CUSTOMER.ID.eq(ORDER.CUSTOMER_ID))
            .join(ORDER_ITEM).on(ORDER_ITEM.ORDER_ID.eq(ORDER.ID))
            .where(whereCondition)
            .orderBy(ORDER.ORDER_DATE.desc(), ORDER.ID.desc(), ORDER_ITEM.ID.desc())
            .fetchStream()
            .map { record ->
                OrderExportRow(
                    orderId = record.get(ORDER.ID)!!,
                    customerId = record.get(ORDER.CUSTOMER_ID)!!,
                    customerName = record.get(CUSTOMER.NAME)!!,
                    customerEmail = record.get(CUSTOMER.EMAIL)!!,
                    orderDate = record.get(ORDER.ORDER_DATE)!!,
                    productName = record.get(ORDER_ITEM.PRODUCT_NAME)!!,
                    quantity = record.get(ORDER_ITEM.QUANTITY)!!,
                    unitPrice = record.get(ORDER_ITEM.UNIT_PRICE)!!
                )
            }
    }

    override fun streamOrdersWithAttributes(
        from: LocalDateTime?,
        to: LocalDateTime?
    ): Stream<OrderAttributeJoinedRow> {
        val whereCondition = buildSearchCondition(from, to, null)

        return dsl.select(
            ORDER.ID,
            ORDER.CUSTOMER_ID,
            CUSTOMER.NAME,
            CUSTOMER.EMAIL,
            ORDER.ORDER_DATE,
            ORDER_ATTRIBUTE_VALUE.ATTRIBUTE_DEFINITION_ID,
            ORDER_ATTRIBUTE_DEFINITION.NAME,
            ORDER_ATTRIBUTE_DEFINITION.LABEL,
            ORDER_ATTRIBUTE_VALUE.VALUE
        )
            .from(ORDER)
            .join(CUSTOMER).on(CUSTOMER.ID.eq(ORDER.CUSTOMER_ID))
            .leftJoin(ORDER_ATTRIBUTE_VALUE).on(ORDER_ATTRIBUTE_VALUE.ORDER_ID.eq(ORDER.ID))
            .leftJoin(ORDER_ATTRIBUTE_DEFINITION)
            .on(ORDER_ATTRIBUTE_DEFINITION.ID.eq(ORDER_ATTRIBUTE_VALUE.ATTRIBUTE_DEFINITION_ID))
            .where(whereCondition)
            .orderBy(ORDER.ID.asc(), ORDER_ATTRIBUTE_DEFINITION.ID.asc())
            .fetchStream()
            .map { record ->
                OrderAttributeJoinedRow(
                    orderId = record.get(ORDER.ID)!!,
                    customerId = record.get(ORDER.CUSTOMER_ID)!!,
                    customerName = record.get(CUSTOMER.NAME)!!,
                    customerEmail = record.get(CUSTOMER.EMAIL)!!,
                    orderDate = record.get(ORDER.ORDER_DATE)!!,
                    definitionId = record.get(ORDER_ATTRIBUTE_VALUE.ATTRIBUTE_DEFINITION_ID),
                    definitionName = record.get(ORDER_ATTRIBUTE_DEFINITION.NAME),
                    definitionLabel = record.get(ORDER_ATTRIBUTE_DEFINITION.LABEL),
                    value = record.get(ORDER_ATTRIBUTE_VALUE.VALUE)
                )
            }
    }
}
