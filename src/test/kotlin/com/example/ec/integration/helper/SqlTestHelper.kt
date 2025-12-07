package com.example.ec.integration.helper

import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.core.JdbcTemplate

/**
 * SQL test helper for executing SQL files from resources
 */
object SqlTestHelper {

    /**
     * Execute SQL file from classpath
     *
     * @param jdbcTemplate JDBC template
     * @param sqlFilePath SQL file path relative to test/resources (e.g., "sql/rollback.sql")
     */
    fun executeSqlFile(jdbcTemplate: JdbcTemplate, sqlFilePath: String) {
        val resource = ClassPathResource(sqlFilePath)
        val sql = resource.inputStream.bufferedReader().use { it.readText() }

        // Split by semicolon and execute each statement
        sql.split(";")
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("--") }
            .forEach { statement ->
                jdbcTemplate.execute(statement)
            }
    }
}
