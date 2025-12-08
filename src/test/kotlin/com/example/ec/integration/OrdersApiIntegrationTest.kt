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

    test("GET /api/orders returns paginated order list") {
        SqlTestHelper.executeSqlFile(jdbcTemplate, "sql/orders-list-test-data.sql")

        val response = restTemplate.getForEntity<Map<String, Any>>("/api/orders?page=0&size=3")

        response.statusCode shouldBe HttpStatus.OK
        val body = response.body
        body shouldNotBe null

        body!!["page"] shouldBe 0
        body["size"] shouldBe 3
        body["totalPages"] shouldBe 2
        body["totalElements"] shouldBe 5

        @Suppress("UNCHECKED_CAST")
        val content = body["content"] as List<Map<String, Any>>
        content.size shouldBe 3

        // Orders should be sorted by orderDate DESC, id DESC
        content[0]["id"] shouldBe 5
        content[0]["customerName"] shouldBe "Bob Johnson"
        content[0]["totalAmount"] shouldBe 1500.0
        content[0]["itemCount"] shouldBe 2

        content[1]["id"] shouldBe 4
        content[1]["customerName"] shouldBe "Charlie Brown"
        content[1]["totalAmount"] shouldBe 3000.0
        content[1]["itemCount"] shouldBe 1

        content[2]["id"] shouldBe 3
        content[2]["customerName"] shouldBe "Alice Smith"
        content[2]["totalAmount"] shouldBe 500.0
        content[2]["itemCount"] shouldBe 1
    }

    test("GET /api/orders filters by date range") {
        SqlTestHelper.executeSqlFile(jdbcTemplate, "sql/orders-list-test-data.sql")

        val response = restTemplate.getForEntity<Map<String, Any>>(
            "/api/orders?from=2024-01-15T00:00:00&to=2024-01-31T23:59:59&page=0&size=10"
        )

        response.statusCode shouldBe HttpStatus.OK
        val body = response.body
        body shouldNotBe null

        body!!["totalElements"] shouldBe 3

        @Suppress("UNCHECKED_CAST")
        val content = body["content"] as List<Map<String, Any>>
        content.size shouldBe 3

        // Should include orders 2, 3, 4
        content[0]["id"] shouldBe 4
        content[1]["id"] shouldBe 3
        content[2]["id"] shouldBe 2
    }

    test("GET /api/orders filters by customer name") {
        SqlTestHelper.executeSqlFile(jdbcTemplate, "sql/orders-list-test-data.sql")

        val response = restTemplate.getForEntity<Map<String, Any>>(
            "/api/orders?customerName=Alice&page=0&size=10"
        )

        response.statusCode shouldBe HttpStatus.OK
        val body = response.body
        body shouldNotBe null

        body!!["totalElements"] shouldBe 2

        @Suppress("UNCHECKED_CAST")
        val content = body["content"] as List<Map<String, Any>>
        content.size shouldBe 2

        // Should include orders from Alice Smith (orders 1, 3)
        content[0]["id"] shouldBe 3
        content[0]["customerName"] shouldBe "Alice Smith"

        content[1]["id"] shouldBe 1
        content[1]["customerName"] shouldBe "Alice Smith"
    }

    test("GET /api/orders returns empty list when no orders match") {
        SqlTestHelper.executeSqlFile(jdbcTemplate, "sql/orders-list-test-data.sql")

        val response = restTemplate.getForEntity<Map<String, Any>>(
            "/api/orders?customerName=NonExistent&page=0&size=10"
        )

        response.statusCode shouldBe HttpStatus.OK
        val body = response.body
        body shouldNotBe null

        body!!["totalElements"] shouldBe 0
        body["totalPages"] shouldBe 0

        @Suppress("UNCHECKED_CAST")
        val content = body["content"] as List<Map<String, Any>>
        content.size shouldBe 0
    }
})
