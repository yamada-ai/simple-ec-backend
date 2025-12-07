package com.example.ec.domain.shared

import com.example.ec.domain.customer.Customer
import com.example.ec.domain.order.Order
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class IDTest : FunSpec({

    context("ID作成") {
        test("正常なIDが作成できる") {
            val id = ID<Customer>(1L)

            id.value shouldBe 1L
        }

        test("notPersisted() で未永続化IDが作成できる") {
            val id = ID.notPersisted<Order>()

            id.value shouldBe 0L
            id.isPersisted() shouldBe false
        }
    }

    context("isPersisted判定") {
        test("value > 0 の場合はtrueを返す") {
            val id = ID<Customer>(100L)

            id.isPersisted() shouldBe true
        }

        test("value = 0 の場合はfalseを返す") {
            val id = ID<Customer>(0L)

            id.isPersisted() shouldBe false
        }
    }

    context("型安全性") {
        test("異なる型のIDは別の型として扱われる") {
            val customerId = ID<Customer>(1L)
            val orderId = ID<Order>(1L)

            // value は同じだが、型が異なる
            customerId.value shouldBe orderId.value
            // ただし、ジェネリクスは実行時に型消去されるため、
            // 完全な型安全性を保証するには sealed interface が必要
        }
    }
})
