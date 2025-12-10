package com.example.ec.application.admin

import com.example.ec.domain.customer.Customer
import com.example.ec.domain.customer.CustomerRepository
import com.example.ec.domain.order.Order
import com.example.ec.domain.order.OrderItem
import com.example.ec.domain.order.OrderRepository
import com.example.ec.domain.shared.Email
import com.example.ec.domain.shared.ID
import com.example.ec.domain.shared.Price
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDateTime
import kotlin.random.Random

private const val MAX_DAYS_AGO = 30L
private const val HOURS_IN_DAY = 24L
private const val MINUTES_IN_HOUR = 60L
private const val MIN_ITEMS_PER_ORDER = 3
private const val MAX_ITEMS_PER_ORDER = 8
private const val MIN_PRODUCT_INDEX = 1
private const val MAX_PRODUCT_INDEX = 101
private const val MIN_QUANTITY = 1
private const val MAX_QUANTITY = 6
private const val MIN_UNIT_PRICE_MULTIPLIER = 1
private const val MAX_UNIT_PRICE_MULTIPLIER = 21
private const val UNIT_PRICE_STEP = 500

/**
 * テストデータ生成ユースケース（開発/テスト用）
 */
@Service
class SeedDataUseCase(
    private val customerRepository: CustomerRepository,
    private val orderRepository: OrderRepository
) {
    /**
     * テストデータを生成する
     *
     * @param customersCount 生成する顧客数
     * @param ordersCount 生成する注文数
     * @param seed ランダムシード（nullの場合はランダム、指定すると再現可能）
     * @return 生成結果
     */
    @Transactional
    fun execute(customersCount: Int, ordersCount: Int, seed: Long? = null): SeedResult {
        val random = seed?.let { Random(it) } ?: Random
        // 顧客を生成
        val customers = generateCustomers(customersCount)
        val savedCustomers = customerRepository.saveAll(customers)

        // 注文を生成
        val orders = generateOrders(ordersCount, savedCustomers, random)
        val savedOrders = orderRepository.saveAll(orders)

        // 生成された明細の総数を計算
        val totalItems = savedOrders.sumOf { it.items.size }

        return SeedResult(
            customersCreated = savedCustomers.size,
            ordersCreated = savedOrders.size,
            orderItemsCreated = totalItems
        )
    }

    private fun generateCustomers(count: Int): List<Customer> {
        val now = LocalDateTime.now()
        return (1..count).map { index ->
            Customer(
                id = ID(0L), // 未永続化
                name = "顧客$index",
                email = Email("customer$index@example.com"),
                createdAt = now
            )
        }
    }

    private fun generateOrders(count: Int, customers: List<Customer>, random: Random): List<Order> {
        val now = LocalDateTime.now()

        return (1..count).map { _ ->
            // ランダムな顧客を選択
            val customer = customers.random(random)

            // 過去30日以内のランダムな日時
            val randomDaysAgo = random.nextLong(0, MAX_DAYS_AGO)
            val randomHours = random.nextLong(0, HOURS_IN_DAY)
            val randomMinutes = random.nextLong(0, MINUTES_IN_HOUR)
            val orderDate = now
                .minusDays(randomDaysAgo)
                .withHour(randomHours.toInt())
                .withMinute(randomMinutes.toInt())
                .withSecond(0)
                .withNano(0)

            // 注文明細を生成（3〜7個）
            val itemsCount = random.nextInt(MIN_ITEMS_PER_ORDER, MAX_ITEMS_PER_ORDER)
            val items = generateOrderItems(itemsCount, orderDate, random)

            // 合計金額を計算
            val totalAmount = items.fold(Price.ZERO) { acc, item ->
                acc + (item.unitPrice * item.quantity.toBigDecimal())
            }

            Order(
                id = ID(0L), // 未永続化
                customerId = customer.id,
                orderDate = orderDate,
                totalAmount = totalAmount,
                createdAt = orderDate,
                items = items,
                attributes = emptyList()
            )
        }
    }

    private fun generateOrderItems(count: Int, createdAt: LocalDateTime, random: Random): List<OrderItem> {
        return (1..count).map { _ ->
            // 商品名: "商品{1..100}" からランダム
            val productIndex = random.nextInt(MIN_PRODUCT_INDEX, MAX_PRODUCT_INDEX)
            val productName = "商品$productIndex"

            // 数量: 1〜5
            val quantity = random.nextInt(MIN_QUANTITY, MAX_QUANTITY)

            // 単価: 500〜10000 の500円単位
            val unitPriceInt = random.nextInt(MIN_UNIT_PRICE_MULTIPLIER, MAX_UNIT_PRICE_MULTIPLIER) * UNIT_PRICE_STEP
            val unitPrice = Price(BigDecimal(unitPriceInt))

            OrderItem(
                id = ID(0L), // 未永続化
                productName = productName,
                quantity = quantity,
                unitPrice = unitPrice,
                createdAt = createdAt
            )
        }
    }
}

/**
 * データ生成結果
 */
data class SeedResult(
    val customersCreated: Int,
    val ordersCreated: Int,
    val orderItemsCreated: Int
)
