package com.example.ec.domain.order

import com.example.ec.domain.customer.Customer
import com.example.ec.domain.shared.ID
import com.example.ec.domain.shared.Price
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.time.LocalDateTime

class OrderTest : FunSpec({

    context("Order作成時のバリデーション") {
        test("正常なOrderが作成できる") {
            val now = LocalDateTime.now()
            val order = Order(
                id = ID(1L),
                customerId = ID<Customer>(100L),
                orderDate = now,
                totalAmount = Price.of("12345.67"),
                createdAt = now
            )

            order shouldBe Order(
                id = ID(1L),
                customerId = ID(100L),
                orderDate = now,
                totalAmount = Price.of("12345.67"),
                createdAt = now
            )
        }

        test("totalAmountが負の場合は例外が発生する") {
            val exception = shouldThrow<IllegalArgumentException> {
                Order(
                    id = ID(1L),
                    customerId = ID(100L),
                    orderDate = LocalDateTime.now(),
                    totalAmount = Price.of("-100"),
                    createdAt = LocalDateTime.now()
                )
            }
            exception.message shouldContain "Price must be non-negative"
        }

        test("totalAmountが0の場合は作成できる") {
            val order = Order(
                id = ID(1L),
                customerId = ID(100L),
                orderDate = LocalDateTime.now(),
                totalAmount = Price.ZERO,
                createdAt = LocalDateTime.now()
            )

            order.totalAmount shouldBe Price.ZERO
        }
    }

    context("calculateTotalAmount - 注文明細から合計金額を計算") {
        test("複数の注文明細から合計金額が計算される") {
            val now = LocalDateTime.now()
            val items = listOf(
                OrderItem(ID(1L), ID(100L), "商品A", 2, Price.of("1000"), now),
                OrderItem(ID(2L), ID(100L), "商品B", 3, Price.of("1500"), now),
                OrderItem(ID(3L), ID(100L), "商品C", 1, Price.of("500"), now)
            )

            val total = Order.calculateTotalAmount(items)

            // (2 * 1000) + (3 * 1500) + (1 * 500) = 2000 + 4500 + 500 = 7000
            total shouldBe Price.of("7000")
        }

        test("注文明細が空の場合は0が返される") {
            val total = Order.calculateTotalAmount(emptyList())

            total shouldBe Price.ZERO
        }

        test("単一の注文明細から合計金額が計算される") {
            val now = LocalDateTime.now()
            val items = listOf(
                OrderItem(ID(1L), ID(100L), "商品A", 5, Price.of("1234.56"), now)
            )

            val total = Order.calculateTotalAmount(items)

            total shouldBe Price.of("6172.80")
        }

        test("小数点を含む単価でも正しく計算される") {
            val now = LocalDateTime.now()
            val items = listOf(
                OrderItem(ID(1L), ID(100L), "商品A", 3, Price.of("999.99"), now),
                OrderItem(ID(2L), ID(100L), "商品B", 2, Price.of("1500.50"), now)
            )

            val total = Order.calculateTotalAmount(items)

            // (3 * 999.99) + (2 * 1500.50) = 2999.97 + 3001.00 = 6000.97
            total shouldBe Price.of("6000.97")
        }
    }
})
