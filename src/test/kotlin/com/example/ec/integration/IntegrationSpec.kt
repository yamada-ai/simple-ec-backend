package com.example.ec.integration

import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName

/**
 * Kotest 用の統合テスト共通ベース。
 * - SpringExtension 登録
 * - Testcontainers PostgreSQL を1つだけ共有
 * - Flyway/DB接続のプロパティを動的注入（テスト用スキーマ: test）
 */
@Testcontainers(disabledWithoutDocker = true)
abstract class IntegrationSpec(body: FunSpec.() -> Unit = {}) : FunSpec(body) {

    override fun extensions() = listOf(SpringExtension)

    companion object {
        init {
            // 以前のデバッグ用に差し込んでいた環境依存の上書きは全て撤廃する
            // Docker 29.x (API 1.45) に更新済みなので、ここでは何も強制しない
        }

        @Container
        private val postgres: PostgreSQLContainer<*> =
            PostgreSQLContainer(DockerImageName.parse("postgres:17"))
                .apply {
                    withDatabaseName("simple_ec_test")
                    withUsername("test")
                    withPassword("test")
                }

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            // DynamicPropertySource が呼ばれる時点で必ず起動させ、ポートを確保する
            postgres.start()
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("spring.flyway.url", postgres::getJdbcUrl)
            registry.add("spring.flyway.user", postgres::getUsername)
            registry.add("spring.flyway.password", postgres::getPassword)
        }
    }
}
