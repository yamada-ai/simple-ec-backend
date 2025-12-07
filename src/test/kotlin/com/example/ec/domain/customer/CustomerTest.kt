package com.example.ec.domain.customer

import com.example.ec.domain.shared.Email
import com.example.ec.domain.shared.ID
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.time.LocalDateTime

class CustomerTest : FunSpec({

    context("Customer作成時のバリデーション") {
        test("正常なCustomerが作成できる") {
            val now = LocalDateTime.now()
            val customer = Customer(
                id = ID(1L),
                name = "山田太郎",
                email = Email("yamada@example.com"),
                createdAt = now
            )

            customer shouldBe Customer(
                id = ID(1L),
                name = "山田太郎",
                email = Email("yamada@example.com"),
                createdAt = now
            )
        }

        test("nameが空文字の場合は例外が発生する") {
            val exception = shouldThrow<IllegalArgumentException> {
                Customer(
                    id = ID(1L),
                    name = "",
                    email = Email("test@example.com"),
                    createdAt = LocalDateTime.now()
                )
            }
            exception.message shouldContain "Customer name must not be blank"
        }

        test("nameが空白のみの場合は例外が発生する") {
            val exception = shouldThrow<IllegalArgumentException> {
                Customer(
                    id = ID(1L),
                    name = "   ",
                    email = Email("test@example.com"),
                    createdAt = LocalDateTime.now()
                )
            }
            exception.message shouldContain "Customer name must not be blank"
        }

        test("emailが空文字の場合は例外が発生する") {
            val exception = shouldThrow<IllegalArgumentException> {
                Customer(
                    id = ID(1L),
                    name = "山田太郎",
                    email = Email(""),
                    createdAt = LocalDateTime.now()
                )
            }
            exception.message shouldContain "Email must not be blank"
        }

        test("emailが不正な形式の場合は例外が発生する") {
            val exception = shouldThrow<IllegalArgumentException> {
                Customer(
                    id = ID(1L),
                    name = "山田太郎",
                    email = Email("invalid-email"),
                    createdAt = LocalDateTime.now()
                )
            }
            exception.message shouldContain "Email format is invalid"
        }
    }

    context("data class としての機能") {
        test("同じ値を持つCustomerは等価である") {
            val now = LocalDateTime.now()
            val customer1 = Customer(ID(1L), "山田太郎", Email("yamada@example.com"), now)
            val customer2 = Customer(ID(1L), "山田太郎", Email("yamada@example.com"), now)

            customer1 shouldBe customer2
        }

        test("copyで一部の値を変更できる") {
            val now = LocalDateTime.now()
            val original = Customer(ID(1L), "山田太郎", Email("yamada@example.com"), now)
            val copied = original.copy(name = "山田次郎")

            copied shouldBe Customer(ID(1L), "山田次郎", Email("yamada@example.com"), now)
        }
    }
})
