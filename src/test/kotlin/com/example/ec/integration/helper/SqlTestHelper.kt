package com.example.ec.integration.helper

import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator

object SqlTestHelper {
    fun executeSqlFile(jdbcTemplate: JdbcTemplate, path: String) {
        val dataSource = jdbcTemplate.dataSource
            ?: throw IllegalStateException("DataSource is not available for executing $path")
        ResourceDatabasePopulator(ClassPathResource(path)).execute(dataSource)
    }
}
