package com.example.ec.integration

import com.example.ec.integration.helper.SqlTestHelper
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.client.getForEntity
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate

/**
 * Orders API の統合テスト (Kotest + SpringBootTest + Testcontainers)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OrdersApiIntegrationTest(
    private val restTemplate: TestRestTemplate,
    private val jdbcTemplate: JdbcTemplate
) : IntegrationSpec({

    beforeEach {
        SqlTestHelper.executeSqlFile(jdbcTemplate, "sql/rollback.sql")
    }

    test("GET /api/orders/{orderId} returns order detail with customer and items") {
        SqlTestHelper.executeSqlFile(jdbcTemplate, "sql/order-detail-test-data.sql")

        val response = restTemplate.getForEntity<Map<String, Any>>("/api/orders/1")

        response.statusCode shouldBe HttpStatus.OK
        val body = response.body
        body shouldNotBe null

        // Order
        body!!["id"] shouldBe 1
        body["totalAmount"] shouldBe 3500.0

        // Customer
        val customer = body["customer"] as Map<*, *>
        customer["id"] shouldBe 1
        customer["name"] shouldBe "Test Customer"
        customer["email"] shouldBe "test@example.com"

        // Items
        @Suppress("UNCHECKED_CAST")
        val items = body["items"] as List<Map<String, Any>>
        items.size shouldBe 2

        items[0]["productName"] shouldBe "Laptop"
        items[0]["quantity"] shouldBe 1
        items[0]["unitPrice"] shouldBe 2500.0

        items[1]["productName"] shouldBe "Mouse"
        items[1]["quantity"] shouldBe 2
        items[1]["unitPrice"] shouldBe 500.0
    }

    test("GET /api/orders/{orderId} returns 404 when order not found") {
        val response = restTemplate.getForEntity<String>("/api/orders/999")
        response.statusCode shouldBe HttpStatus.NOT_FOUND
    }
})
