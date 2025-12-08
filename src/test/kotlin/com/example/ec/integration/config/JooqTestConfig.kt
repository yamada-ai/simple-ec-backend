package com.example.ec.integration.config

import org.jooq.conf.Settings
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

/**
 * テスト時は currentSchema=test に任せるため、スキーマ名を SQL に出力しない。
 * （本番の DSLContext 設定には影響しないよう TestConfiguration として分離）
 */
@TestConfiguration
class JooqTestConfig {
    @Bean
    fun jooqTestSettings(): Settings = Settings().withRenderSchema(false)
}
