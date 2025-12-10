package com.example.ec.integration

import com.example.ec.integration.helper.SqlTestHelper
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.client.exchange
import org.springframework.boot.test.web.client.getForEntity
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate

/**
 * Attribute Definitions API の統合テスト (Kotest + SpringBootTest + Testcontainers)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AttributeDefinitionsApiIntegrationTest(
    private val restTemplate: TestRestTemplate,
    private val jdbcTemplate: JdbcTemplate
) : IntegrationSpec({

    beforeEach {
        SqlTestHelper.executeSqlFile(jdbcTemplate, "sql/rollback.sql")
    }

    test("GET /api/attribute-definitions returns all definitions") {
        SqlTestHelper.executeSqlFile(jdbcTemplate, "sql/attribute-definitions-test-data.sql")

        val response = restTemplate.getForEntity<List<Map<String, Any>>>("/api/attribute-definitions")

        response.statusCode shouldBe HttpStatus.OK
        val body = response.body
        body shouldNotBe null

        body!!.size shouldBe 3

        body[0]["id"] shouldBe 1
        body[0]["name"] shouldBe "gift_wrapping"
        body[0]["label"] shouldBe "ギフト包装"
        body[0]["description"] shouldBe "ギフト包装の種類を指定"

        body[1]["id"] shouldBe 2
        body[1]["name"] shouldBe "delivery_time"
        body[1]["label"] shouldBe "配送時間指定"
        body[1]["description"] shouldBe null

        body[2]["id"] shouldBe 3
        body[2]["name"] shouldBe "campaign_code"
        body[2]["label"] shouldBe "キャンペーンコード"
        body[2]["description"] shouldBe "適用されたキャンペーンコード"
    }

    test("GET /api/attribute-definitions returns empty list when no definitions exist") {
        val response = restTemplate.getForEntity<List<Map<String, Any>>>("/api/attribute-definitions")

        response.statusCode shouldBe HttpStatus.OK
        val body = response.body
        body shouldNotBe null
        body!!.size shouldBe 0
    }

    test("GET /api/attribute-definitions/{id} returns definition by id") {
        SqlTestHelper.executeSqlFile(jdbcTemplate, "sql/attribute-definitions-test-data.sql")

        val response = restTemplate.getForEntity<Map<String, Any>>("/api/attribute-definitions/1")

        response.statusCode shouldBe HttpStatus.OK
        val body = response.body
        body shouldNotBe null

        body!!["id"] shouldBe 1
        body["name"] shouldBe "gift_wrapping"
        body["label"] shouldBe "ギフト包装"
        body["description"] shouldBe "ギフト包装の種類を指定"
    }

    test("GET /api/attribute-definitions/{id} returns 404 when definition not found") {
        val response = restTemplate.getForEntity<String>("/api/attribute-definitions/999")
        response.statusCode shouldBe HttpStatus.NOT_FOUND
    }

    test("POST /api/attribute-definitions creates new definition") {
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
        }
        val requestBody = """
            {
                "name": "new_attribute",
                "label": "新規属性",
                "description": "新しく追加された属性"
            }
        """.trimIndent()
        val request = HttpEntity(requestBody, headers)

        val response = restTemplate.exchange<Map<String, Any>>(
            "/api/attribute-definitions",
            HttpMethod.POST,
            request
        )

        response.statusCode shouldBe HttpStatus.CREATED
        val body = response.body
        body shouldNotBe null

        body!!["id"] shouldNotBe null
        body["name"] shouldBe "new_attribute"
        body["label"] shouldBe "新規属性"
        body["description"] shouldBe "新しく追加された属性"
    }

    test("POST /api/attribute-definitions with null description") {
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
        }
        val requestBody = """
            {
                "name": "simple_attribute",
                "label": "シンプル属性",
                "description": null
            }
        """.trimIndent()
        val request = HttpEntity(requestBody, headers)

        val response = restTemplate.exchange<Map<String, Any>>(
            "/api/attribute-definitions",
            HttpMethod.POST,
            request
        )

        response.statusCode shouldBe HttpStatus.CREATED
        val body = response.body
        body shouldNotBe null

        body!!["id"] shouldNotBe null
        body["name"] shouldBe "simple_attribute"
        body["label"] shouldBe "シンプル属性"
        body["description"] shouldBe null
    }

    test("POST /api/attribute-definitions returns 400 when name is blank") {
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
        }
        val requestBody = """
            {
                "name": "",
                "label": "空の名前",
                "description": null
            }
        """.trimIndent()
        val request = HttpEntity(requestBody, headers)

        val response = restTemplate.exchange<String>(
            "/api/attribute-definitions",
            HttpMethod.POST,
            request
        )

        response.statusCode shouldBe HttpStatus.BAD_REQUEST
    }

    test("POST /api/attribute-definitions returns 409 when name already exists") {
        SqlTestHelper.executeSqlFile(jdbcTemplate, "sql/attribute-definitions-test-data.sql")

        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
        }
        val requestBody = """
            {
                "name": "gift_wrapping",
                "label": "重複した名前",
                "description": null
            }
        """.trimIndent()
        val request = HttpEntity(requestBody, headers)

        val response = restTemplate.exchange<String>(
            "/api/attribute-definitions",
            HttpMethod.POST,
            request
        )

        response.statusCode shouldBe HttpStatus.CONFLICT
    }

    test("PUT /api/attribute-definitions/{id} updates definition") {
        SqlTestHelper.executeSqlFile(jdbcTemplate, "sql/attribute-definitions-test-data.sql")

        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
        }
        val requestBody = """
            {
                "name": "gift_wrapping_updated",
                "label": "ギフト包装（更新）",
                "description": "更新された説明"
            }
        """.trimIndent()
        val request = HttpEntity(requestBody, headers)

        val response = restTemplate.exchange<Map<String, Any>>(
            "/api/attribute-definitions/1",
            HttpMethod.PUT,
            request
        )

        response.statusCode shouldBe HttpStatus.OK
        val body = response.body
        body shouldNotBe null

        body!!["id"] shouldBe 1
        body["name"] shouldBe "gift_wrapping_updated"
        body["label"] shouldBe "ギフト包装（更新）"
        body["description"] shouldBe "更新された説明"
    }

    test("PUT /api/attribute-definitions/{id} returns 404 when definition not found") {
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
        }
        val requestBody = """
            {
                "name": "nonexistent",
                "label": "存在しない",
                "description": null
            }
        """.trimIndent()
        val request = HttpEntity(requestBody, headers)

        val response = restTemplate.exchange<String>(
            "/api/attribute-definitions/999",
            HttpMethod.PUT,
            request
        )

        response.statusCode shouldBe HttpStatus.NOT_FOUND
    }

    test("PUT /api/attribute-definitions/{id} returns 409 when name conflicts with another definition") {
        SqlTestHelper.executeSqlFile(jdbcTemplate, "sql/attribute-definitions-test-data.sql")

        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
        }
        val requestBody = """
            {
                "name": "delivery_time",
                "label": "重複する名前",
                "description": null
            }
        """.trimIndent()
        val request = HttpEntity(requestBody, headers)

        val response = restTemplate.exchange<String>(
            "/api/attribute-definitions/1",
            HttpMethod.PUT,
            request
        )

        response.statusCode shouldBe HttpStatus.CONFLICT
    }

    test("DELETE /api/attribute-definitions/{id} deletes definition") {
        SqlTestHelper.executeSqlFile(jdbcTemplate, "sql/attribute-definitions-test-data.sql")

        val deleteResponse = restTemplate.exchange<Unit>(
            "/api/attribute-definitions/1",
            HttpMethod.DELETE,
            null
        )

        deleteResponse.statusCode shouldBe HttpStatus.NO_CONTENT

        // Verify deletion
        val getResponse = restTemplate.getForEntity<String>("/api/attribute-definitions/1")
        getResponse.statusCode shouldBe HttpStatus.NOT_FOUND
    }

    test("DELETE /api/attribute-definitions/{id} returns 404 when definition not found") {
        val response = restTemplate.exchange<Unit>(
            "/api/attribute-definitions/999",
            HttpMethod.DELETE,
            null
        )

        response.statusCode shouldBe HttpStatus.NOT_FOUND
    }
})
