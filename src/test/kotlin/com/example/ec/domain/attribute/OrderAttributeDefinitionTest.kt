package com.example.ec.domain.attribute

import com.example.ec.domain.shared.ID
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.time.LocalDateTime

class OrderAttributeDefinitionTest : FunSpec({

    context("OrderAttributeDefinition作成時のバリデーション") {
        test("正常なOrderAttributeDefinitionが作成できる") {
            val now = LocalDateTime.now()
            val definition = OrderAttributeDefinition(
                id = ID(1L),
                name = "gift_wrapping",
                label = "ギフト包装",
                description = "ギフト包装の種類を指定",
                createdAt = now
            )

            definition shouldBe OrderAttributeDefinition(
                id = ID(1L),
                name = "gift_wrapping",
                label = "ギフト包装",
                description = "ギフト包装の種類を指定",
                createdAt = now
            )
        }

        test("descriptionがnullでも作成できる") {
            val now = LocalDateTime.now()
            val definition = OrderAttributeDefinition(
                id = ID(1L),
                name = "campaign_id",
                label = "キャンペーンID",
                description = null,
                createdAt = now
            )

            definition.description shouldBe null
        }

        test("nameが空文字の場合は例外が発生する") {
            val exception = shouldThrow<IllegalArgumentException> {
                OrderAttributeDefinition(
                    id = ID(1L),
                    name = "",
                    label = "Label",
                    description = null,
                    createdAt = LocalDateTime.now()
                )
            }
            exception.message shouldContain "Attribute name must not be blank"
        }

        test("nameが空白のみの場合は例外が発生する") {
            val exception = shouldThrow<IllegalArgumentException> {
                OrderAttributeDefinition(
                    id = ID(1L),
                    name = "   ",
                    label = "Label",
                    description = null,
                    createdAt = LocalDateTime.now()
                )
            }
            exception.message shouldContain "Attribute name must not be blank"
        }

        test("labelが空文字の場合は例外が発生する") {
            val exception = shouldThrow<IllegalArgumentException> {
                OrderAttributeDefinition(
                    id = ID(1L),
                    name = "name",
                    label = "",
                    description = null,
                    createdAt = LocalDateTime.now()
                )
            }
            exception.message shouldContain "Attribute label must not be blank"
        }

        test("nameが100文字を超える場合は例外が発生する") {
            val exception = shouldThrow<IllegalArgumentException> {
                OrderAttributeDefinition(
                    id = ID(1L),
                    name = "a".repeat(101),
                    label = "Label",
                    description = null,
                    createdAt = LocalDateTime.now()
                )
            }
            exception.message shouldContain "Attribute name must not exceed 100 characters"
        }

        test("labelが100文字を超える場合は例外が発生する") {
            val exception = shouldThrow<IllegalArgumentException> {
                OrderAttributeDefinition(
                    id = ID(1L),
                    name = "name",
                    label = "a".repeat(101),
                    description = null,
                    createdAt = LocalDateTime.now()
                )
            }
            exception.message shouldContain "Attribute label must not exceed 100 characters"
        }

        test("descriptionが255文字を超える場合は例外が発生する") {
            val exception = shouldThrow<IllegalArgumentException> {
                OrderAttributeDefinition(
                    id = ID(1L),
                    name = "name",
                    label = "Label",
                    description = "a".repeat(256),
                    createdAt = LocalDateTime.now()
                )
            }
            exception.message shouldContain "Attribute description must not exceed 255 characters"
        }
    }

    context("data class としての機能") {
        test("同じ値を持つOrderAttributeDefinitionは等価である") {
            val now = LocalDateTime.now()
            val def1 = OrderAttributeDefinition(ID(1L), "name", "Label", "Desc", now)
            val def2 = OrderAttributeDefinition(ID(1L), "name", "Label", "Desc", now)

            def1 shouldBe def2
        }

        test("copyで一部の値を変更できる") {
            val now = LocalDateTime.now()
            val original = OrderAttributeDefinition(ID(1L), "name", "Label", "Desc", now)
            val copied = original.copy(label = "NewLabel")

            copied shouldBe OrderAttributeDefinition(ID(1L), "name", "NewLabel", "Desc", now)
        }
    }
})
