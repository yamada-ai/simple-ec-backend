package com.example.ec.application.export

import org.springframework.jdbc.datasource.DataSourceUtils
import org.springframework.stereotype.Component
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Timestamp
import java.time.LocalDateTime
import javax.sql.DataSource

@Component
class ImperativeResultSetOrderAttributeRowSource(
    private val dataSource: DataSource
) : OrderAttributeRowSource {

    override val strategy: AttributeExportStrategy = AttributeExportStrategy.IMPERATIVE_RESULT_SET

    override fun rows(
        from: LocalDateTime?,
        to: LocalDateTime?
    ): CloseableSequence<OrderAttributeCsvRow> {
        val cursor = ResultSetOrderAttributeCursor.open(dataSource, from, to)
        return CloseableSequence(
            delegate = cursor.asSequence(),
            closeAction = cursor::close
        )
    }
}

private class ResultSetOrderAttributeCursor(
    private val dataSource: DataSource,
    private val connection: Connection,
    private val statement: PreparedStatement,
    private val resultSet: ResultSet
) : Iterator<OrderAttributeCsvRow>, AutoCloseable {

    private var nextRow: JdbcOrderAttributeRow? = readNextRow()
    private var closed: Boolean = false

    override fun hasNext(): Boolean = nextRow != null

    override fun next(): OrderAttributeCsvRow {
        val first = nextRow ?: throw NoSuchElementException()
        nextRow = null

        val orderId = first.orderId
        val attributes = linkedMapOf<Long, String>()
        putAttribute(first, attributes)

        var row = readNextRow()
        while (row != null && row.orderId == orderId) {
            putAttribute(row, attributes)
            row = readNextRow()
        }
        nextRow = row

        return OrderAttributeCsvRow(
            orderId = first.orderId,
            customerId = first.customerId,
            customerName = first.customerName,
            customerEmail = first.customerEmail,
            orderDate = first.orderDate,
            attributes = attributes
        )
    }

    private fun readNextRow(): JdbcOrderAttributeRow? {
        if (closed || !resultSet.next()) {
            return null
        }

        val definitionId = resultSet.getLong("definition_id")
            .takeUnless { resultSet.wasNull() }

        return JdbcOrderAttributeRow(
            orderId = resultSet.getLong("order_id"),
            customerId = resultSet.getLong("customer_id"),
            customerName = resultSet.getString("customer_name"),
            customerEmail = resultSet.getString("customer_email"),
            orderDate = resultSet.getTimestamp("order_date").toLocalDateTime(),
            definitionId = definitionId,
            value = resultSet.getString("value")
        )
    }

    override fun close() {
        if (closed) {
            return
        }
        closed = true
        runCatching { resultSet.close() }
        runCatching { statement.close() }
        DataSourceUtils.releaseConnection(connection, dataSource)
    }

    companion object {
        private const val EXPORT_FETCH_SIZE = 1_000

        fun open(
            dataSource: DataSource,
            from: LocalDateTime?,
            to: LocalDateTime?
        ): ResultSetOrderAttributeCursor {
            val connection = DataSourceUtils.getConnection(dataSource)
            var statement: PreparedStatement? = null
            var resultSet: ResultSet? = null
            try {
                statement = connection.prepareStatement(buildSql(from, to))
                statement.fetchSize = EXPORT_FETCH_SIZE
                bindParameters(statement, from, to)
                resultSet = statement.executeQuery()
                return ResultSetOrderAttributeCursor(dataSource, connection, statement, resultSet)
            } catch (e: SQLException) {
                runCatching { resultSet?.close() }
                runCatching { statement?.close() }
                DataSourceUtils.releaseConnection(connection, dataSource)
                throw e
            }
        }

        private fun buildSql(
            from: LocalDateTime?,
            to: LocalDateTime?
        ): String {
            val predicates = buildList {
                from?.let { add("o.order_date >= ?") }
                to?.let { add("o.order_date <= ?") }
            }
            val whereClause = predicates
                .takeIf { it.isNotEmpty() }
                ?.joinToString(prefix = "where ", separator = " and ")
                ?: ""

            return """
                select
                    o.id as order_id,
                    o.customer_id as customer_id,
                    c.name as customer_name,
                    c.email as customer_email,
                    o.order_date as order_date,
                    oav.attribute_definition_id as definition_id,
                    oav.value as value
                from "order" o
                join customer c on c.id = o.customer_id
                left join order_attribute_value oav on oav.order_id = o.id
                $whereClause
                order by o.id asc, oav.attribute_definition_id asc
            """.trimIndent()
        }

        private fun bindParameters(
            statement: PreparedStatement,
            from: LocalDateTime?,
            to: LocalDateTime?
        ) {
            var index = 1
            from?.let { statement.setTimestamp(index++, Timestamp.valueOf(it)) }
            to?.let { statement.setTimestamp(index, Timestamp.valueOf(it)) }
        }
    }
}

private data class JdbcOrderAttributeRow(
    val orderId: Long,
    val customerId: Long,
    val customerName: String,
    val customerEmail: String,
    val orderDate: LocalDateTime,
    val definitionId: Long?,
    val value: String?
)

private fun putAttribute(
    row: JdbcOrderAttributeRow,
    attributes: MutableMap<Long, String>
) {
    val definitionId = row.definitionId ?: return
    val value = row.value ?: return
    attributes[definitionId] = value
}
