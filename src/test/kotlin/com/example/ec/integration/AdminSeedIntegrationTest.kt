package com.example.ec.integration

import com.example.ec.integration.helper.SqlTestHelper
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.client.postForEntity
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate

/**
 * Admin API の seed エンドポイント統合テスト
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AdminSeedIntegrationTest(
    private val restTemplate: TestRestTemplate,
    private val jdbcTemplate: JdbcTemplate
) : IntegrationSpec({

    beforeEach {
        SqlTestHelper.executeSqlFile(jdbcTemplate, "sql/rollback.sql")
    }

    test("POST /admin/seed creates customers and orders without attributes") {
        val response = restTemplate.postForEntity<Map<String, Any>>(
            "/admin/seed?customers=10&orders=50&seed=42"
        )

        response.statusCode shouldBe HttpStatus.OK
        val body = response.body
        body shouldNotBe null

        body!!["customersCreated"] shouldBe 10
        body["ordersCreated"] shouldBe 50
        (body["orderItemsCreated"] as Int) shouldBeGreaterThan 0
        body["attributeDefinitionsCreated"] shouldBe 0
        body["attributeValuesCreated"] shouldBe 0

        // Verify data is in DB
        val customerCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM customer",
            Long::class.java
        )
        customerCount shouldBe 10L

        val orderCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM \"order\"",
            Long::class.java
        )
        orderCount shouldBe 50L
    }

    test("POST /admin/seed creates attributes when attrs > 0") {
        val response = restTemplate.postForEntity<Map<String, Any>>(
            "/admin/seed?customers=5&orders=20&attrs=15&seed=100"
        )

        response.statusCode shouldBe HttpStatus.OK
        val body = response.body
        body shouldNotBe null

        body!!["customersCreated"] shouldBe 5
        body["ordersCreated"] shouldBe 20
        (body["orderItemsCreated"] as Int) shouldBeGreaterThan 0
        body["attributeDefinitionsCreated"] shouldBe 15
        body["attributeValuesCreated"] shouldBe 300 // 20 orders × 15 attrs

        // Verify attribute definitions
        val defCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM order_attribute_definition",
            Long::class.java
        )
        defCount shouldBe 15L

        // Verify attribute values
        val valueCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM order_attribute_value",
            Long::class.java
        )
        valueCount shouldBe 300L

        // Verify deterministic value generation (v{defId}_{orderId % 10})
        val firstValue = jdbcTemplate.queryForObject(
            """
            SELECT value FROM order_attribute_value
            WHERE order_id = (SELECT MIN(id) FROM "order")
              AND attribute_definition_id = (SELECT MIN(id) FROM order_attribute_definition)
            """,
            String::class.java
        )
        // First order ID should be 1, first def ID should be 1 -> v1_1
        firstValue shouldBe "v1_1"
    }

    test("POST /admin/seed with same seed generates reproducible data") {
        // First seed
        restTemplate.postForEntity<Map<String, Any>>(
            "/admin/seed?customers=10&orders=30&attrs=5&seed=999"
        )

        val firstOrderIds = jdbcTemplate.queryForList(
            "SELECT id FROM \"order\" ORDER BY id",
            Long::class.java
        )

        val firstAttributeValues = jdbcTemplate.queryForList(
            "SELECT value FROM order_attribute_value ORDER BY order_id, attribute_definition_id",
            String::class.java
        )

        // Truncate and seed again with same seed
        SqlTestHelper.executeSqlFile(jdbcTemplate, "sql/rollback.sql")
        restTemplate.postForEntity<Map<String, Any>>(
            "/admin/seed?customers=10&orders=30&attrs=5&seed=999"
        )

        val secondOrderIds = jdbcTemplate.queryForList(
            "SELECT id FROM \"order\" ORDER BY id",
            Long::class.java
        )

        val secondAttributeValues = jdbcTemplate.queryForList(
            "SELECT value FROM order_attribute_value ORDER BY order_id, attribute_definition_id",
            String::class.java
        )

        // IDs should be the same
        secondOrderIds shouldBe firstOrderIds

        // Attribute values should be deterministic (same for same order IDs)
        secondAttributeValues shouldBe firstAttributeValues
    }

    test("POST /admin/seed uses existing attribute definitions if present") {
        // Create initial definitions
        restTemplate.postForEntity<Map<String, Any>>(
            "/admin/seed?customers=5&orders=10&attrs=10&seed=1"
        )

        val initialDefCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM order_attribute_definition",
            Long::class.java
        )
        initialDefCount shouldBe 10L

        // Truncate orders/customers but keep definitions
        jdbcTemplate.execute("DELETE FROM order_attribute_value")
        jdbcTemplate.execute("DELETE FROM order_item")
        jdbcTemplate.execute("DELETE FROM \"order\"")
        jdbcTemplate.execute("DELETE FROM customer")
        jdbcTemplate.execute("SELECT setval('customer_id_seq', 1, false)")
        jdbcTemplate.execute("SELECT setval('order_id_seq', 1, false)")

        // Seed again (should reuse existing definitions)
        val response = restTemplate.postForEntity<Map<String, Any>>(
            "/admin/seed?customers=5&orders=20&attrs=10&seed=2"
        )

        response.statusCode shouldBe HttpStatus.OK
        val body = response.body
        body shouldNotBe null

        // Should not create new definitions (reused existing 10)
        body!!["attributeDefinitionsCreated"] shouldBe 10 // Returns count of reused definitions
        body["attributeValuesCreated"] shouldBe 200 // 20 orders × 10 attrs

        val finalDefCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM order_attribute_definition",
            Long::class.java
        )
        finalDefCount shouldBe 10L // Still 10, not 20
    }

    test("POST /admin/seed with attrs=0 does not create attributes (backward compatibility)") {
        val response = restTemplate.postForEntity<Map<String, Any>>(
            "/admin/seed?customers=3&orders=15&attrs=0&seed=50"
        )

        response.statusCode shouldBe HttpStatus.OK
        val body = response.body
        body shouldNotBe null

        body!!["customersCreated"] shouldBe 3
        body["ordersCreated"] shouldBe 15
        body["attributeDefinitionsCreated"] shouldBe 0
        body["attributeValuesCreated"] shouldBe 0

        val defCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM order_attribute_definition",
            Long::class.java
        )
        defCount shouldBe 0L

        val valueCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM order_attribute_value",
            Long::class.java
        )
        valueCount shouldBe 0L
    }
})
