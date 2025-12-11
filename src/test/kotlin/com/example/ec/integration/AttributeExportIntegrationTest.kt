package com.example.ec.integration

import com.example.ec.integration.helper.SqlTestHelper
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.client.getForEntity
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate

/**
 * 注文属性付きCSVエクスポートの統合テスト。
 * すべての戦略で同じCSVが出力されることを確認する。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AttributeExportIntegrationTest(
    private val restTemplate: TestRestTemplate,
    private val jdbcTemplate: JdbcTemplate
) : IntegrationSpec({

    val expected = """
        order_id,customer_id,customer_name,customer_email,order_date,ギフト包装,配送指示,備考
        10,1,Customer 1,c1@example.com,2024-02-01T10:00,あり,置き配希望,
        11,2,Customer 2,c2@example.com,2024-02-02T12:00,なし,,要電話連絡
        12,1,Customer 1,c1@example.com,2024-02-03T15:00,,時間指定なし,
        13,2,Customer 2,c2@example.com,2024-02-04T18:00,,,
    """.trimIndent() + "\n"

    beforeEach {
        SqlTestHelper.executeSqlFile(jdbcTemplate, "sql/rollback.sql")
        SqlTestHelper.executeSqlFile(jdbcTemplate, "sql/attribute-export-data.sql")
    }

    listOf(
        "join",
        "sequence-window",
        "spliterator-window",
        "multiset",
        "preload"
    ).forEach { strategy ->
        test("GET /api/export/orders/attributes ($strategy) returns expected CSV") {
            val response = restTemplate.getForEntity<String>("/api/export/orders/attributes?strategy=$strategy")

            response.statusCode shouldBe HttpStatus.OK
            response.body shouldBe expected
        }
    }
})
