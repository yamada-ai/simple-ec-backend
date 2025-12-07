package com.example.ec.domain.order

import com.example.ec.domain.shared.ID
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.math.BigDecimal
import java.time.LocalDateTime

class OrderItemTest : FunSpec({

    context("OrderItem作成時のバリデーション") {
        test("正常なOrderItemが作成できる") {
            val now = LocalDateTime.now()
            val item = OrderItem(
                id = ID(1L),
                orderId = ID(100L),
                productName = "サンプル商品",
                quantity = 2,
                unitPrice = BigDecimal("1000.00"),
                createdAt = now
            )

            item shouldBe OrderItem(
                id = ID(1L),
                orderId = ID(100L),
                productName = "サンプル商品",
                quantity = 2,
                unitPrice = BigDecimal("1000.00"),
                createdAt = now
            )
        }

        test("productNameが空文字の場合は例外が発生する") {
            val exception = shouldThrow<IllegalArgumentException> {
                OrderItem(
                    id = ID(1L),
                    orderId = ID(100L),
                    productName = "",
                    quantity = 1,
                    unitPrice = BigDecimal("1000"),
                    createdAt = LocalDateTime.now()
                )
            }
            exception.message shouldContain "Product name must not be blank"
        }

        test("quantityが0の場合は例外が発生する") {
            val exception = shouldThrow<IllegalArgumentException> {
                OrderItem(
                    id = ID(1L),
                    orderId = ID(100L),
                    productName = "商品A",
                    quantity = 0,
                    unitPrice = BigDecimal("1000"),
                    createdAt = LocalDateTime.now()
                )
            }
            exception.message shouldContain "Quantity must be greater than 0"
        }

        test("quantityが負の場合は例外が発生する") {
            val exception = shouldThrow<IllegalArgumentException> {
                OrderItem(
                    id = ID(1L),
                    orderId = ID(100L),
                    productName = "商品A",
                    quantity = -1,
                    unitPrice = BigDecimal("1000"),
                    createdAt = LocalDateTime.now()
                )
            }
            exception.message shouldContain "Quantity must be greater than 0"
        }

        test("unitPriceが負の場合は例外が発生する") {
            val exception = shouldThrow<IllegalArgumentException> {
                OrderItem(
                    id = ID(1L),
                    orderId = ID(100L),
                    productName = "商品A",
                    quantity = 1,
                    unitPrice = BigDecimal("-100"),
                    createdAt = LocalDateTime.now()
                )
            }
            exception.message shouldContain "Unit price must be non-negative"
        }
    }

    context("subtotal計算") {
        test("正しく小計金額が計算される") {
            val item = OrderItem(
                id = ID(1L),
                orderId = ID(100L),
                productName = "商品A",
                quantity = 3,
                unitPrice = BigDecimal("1500.50"),
                createdAt = LocalDateTime.now()
            )

            item.subtotal() shouldBe BigDecimal("4501.50")
        }

        test("quantity=1の場合、小計はunitPriceと同じ") {
            val item = OrderItem(
                id = ID(1L),
                orderId = ID(100L),
                productName = "商品A",
                quantity = 1,
                unitPrice = BigDecimal("999.99"),
                createdAt = LocalDateTime.now()
            )

            item.subtotal() shouldBe BigDecimal("999.99")
        }

        test("unitPrice=0の場合、小計も0") {
            val item = OrderItem(
                id = ID(1L),
                orderId = ID(100L),
                productName = "無料サンプル",
                quantity = 5,
                unitPrice = BigDecimal.ZERO,
                createdAt = LocalDateTime.now()
            )

            item.subtotal() shouldBe BigDecimal.ZERO
        }
    }
})
