package com.example.ec.domain.attribute

import com.example.ec.domain.order.Order
import com.example.ec.domain.shared.ID
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.time.LocalDateTime

class OrderAttributeValueTest : FunSpec({

    context("OrderAttributeValue作成時のバリデーション") {
        test("正常なOrderAttributeValueが作成できる") {
            val now = LocalDateTime.now()
            val value = OrderAttributeValue(
                id = ID(1L),
                attributeDefinitionId = ID(10L),
                value = "リボン包装",
                createdAt = now
            )

            value shouldBe OrderAttributeValue(
                id = ID(1L),
                attributeDefinitionId = ID(10L),
                value = "リボン包装",
                createdAt = now
            )
        }

        test("valueが空文字でも作成できる") {
            val now = LocalDateTime.now()
            val value = OrderAttributeValue(
                id = ID(1L),
                attributeDefinitionId = ID(10L),
                value = "",
                createdAt = now
            )

            value.value shouldBe ""
        }

        test("valueが255文字を超える場合は例外が発生する") {
            val exception = shouldThrow<IllegalArgumentException> {
                OrderAttributeValue(
                    id = ID(1L),
                    attributeDefinitionId = ID(10L),
                    value = "a".repeat(256),
                    createdAt = LocalDateTime.now()
                )
            }
            exception.message shouldContain "Attribute value must not exceed 255 characters"
        }

        test("valueが255文字ちょうどの場合は作成できる") {
            val now = LocalDateTime.now()
            val value = OrderAttributeValue(
                id = ID(1L),
                attributeDefinitionId = ID(10L),
                value = "a".repeat(255),
                createdAt = now
            )

            value.value.length shouldBe 255
        }
    }

    context("data class としての機能") {
        test("同じ値を持つOrderAttributeValueは等価である") {
            val now = LocalDateTime.now()
            val val1 = OrderAttributeValue(ID(1L), ID(10L), "value", now)
            val val2 = OrderAttributeValue(ID(1L), ID(10L), "value", now)

            val1 shouldBe val2
        }

        test("copyで一部の値を変更できる") {
            val now = LocalDateTime.now()
            val original = OrderAttributeValue(ID(1L), ID(10L), "value", now)
            val copied = original.copy(value = "new value")

            copied shouldBe OrderAttributeValue(ID(1L), ID(10L), "new value", now)
        }
    }
})
