package com.example.ec.application.export

import com.example.ec.domain.order.OrderAttributeJoinedRow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import java.util.stream.StreamSupport

class OrderAttributeWindowingTest : FunSpec({

    context("Sequence windowing") {
        test("groups ordered joined rows into one CSV row per order") {
            val rows = listOf(
                joinedRow(orderId = 1, definitionId = 10, value = "gift"),
                joinedRow(orderId = 1, definitionId = 20, value = "morning"),
                joinedRow(orderId = 2, definitionId = null, value = null)
            )

            val result = rows.asSequence().windowByOrderId().toList()

            result.map { it.orderId } shouldContainExactly listOf(1L, 2L)
            result[0].attributes shouldBe mapOf(10L to "gift", 20L to "morning")
            result[1].attributes.shouldBeEmpty()
        }

        test("treats disconnected rows with the same orderId as separate windows") {
            val rows = listOf(
                joinedRow(orderId = 1, definitionId = 10, value = "first"),
                joinedRow(orderId = 2, definitionId = 10, value = "second"),
                joinedRow(orderId = 1, definitionId = 20, value = "disconnected")
            )

            val result = rows.asSequence().windowByOrderId().toList()

            result.map { it.orderId } shouldContainExactly listOf(1L, 2L, 1L)
            result[0].attributes shouldBe mapOf(10L to "first")
            result[2].attributes shouldBe mapOf(20L to "disconnected")
        }

        test("uses the last value when duplicate definition rows exist in the same order window") {
            val rows = listOf(
                joinedRow(orderId = 1, definitionId = 10, value = "old"),
                joinedRow(orderId = 1, definitionId = 10, value = "new")
            )

            val result = rows.asSequence().windowByOrderId().toList()

            result.single().attributes shouldBe mapOf(10L to "new")
        }

        test("skips null attribute values") {
            val rows = listOf(
                joinedRow(orderId = 1, definitionId = 10, value = null)
            )

            val result = rows.asSequence().windowByOrderId().toList()

            result.single().attributes.shouldBeEmpty()
        }
    }

    context("Spliterator windowing") {
        test("groups ordered joined rows into one CSV row per order") {
            val rows = listOf(
                joinedRow(orderId = 1, definitionId = 10, value = "gift"),
                joinedRow(orderId = 1, definitionId = 20, value = "morning"),
                joinedRow(orderId = 2, definitionId = null, value = null)
            )

            val spliterator = OrderAttributeWindowSpliterator(rows.spliterator())
            val result = StreamSupport.stream(spliterator, false).toList()

            result.map { it.orderId } shouldContainExactly listOf(1L, 2L)
            result[0].attributes shouldBe mapOf(10L to "gift", 20L to "morning")
            result[1].attributes.shouldBeEmpty()
        }

        test("does not split because order boundaries can cross arbitrary split points") {
            val rows = listOf(joinedRow(orderId = 1, definitionId = 10, value = "gift"))
            val spliterator = OrderAttributeWindowSpliterator(rows.spliterator())

            spliterator.trySplit() shouldBe null
        }
    }
})

private fun joinedRow(
    orderId: Long,
    definitionId: Long?,
    value: String?
): OrderAttributeJoinedRow {
    return OrderAttributeJoinedRow(
        orderId = orderId,
        customerId = 100L + orderId,
        customerName = "customer-$orderId",
        customerEmail = "customer-$orderId@example.com",
        orderDate = LocalDateTime.of(2026, 1, 1, 0, 0),
        definitionId = definitionId,
        definitionName = definitionId?.let { "attr_$it" },
        definitionLabel = definitionId?.let { "属性$it" },
        value = value
    )
}
