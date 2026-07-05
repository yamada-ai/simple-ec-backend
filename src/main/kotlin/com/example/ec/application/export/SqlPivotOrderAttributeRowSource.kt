package com.example.ec.application.export

import com.example.ec.domain.attribute.OrderAttributeDefinitionRepository
import com.example.ec.domain.order.OrderRepository
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import kotlin.streams.asSequence

@Component
class SqlPivotOrderAttributeRowSource(
    private val definitionRepository: OrderAttributeDefinitionRepository,
    private val orderRepository: OrderRepository
) : OrderAttributeRowSource {

    override val strategy: AttributeExportStrategy = AttributeExportStrategy.SQL_PIVOT

    override fun rows(
        from: LocalDateTime?,
        to: LocalDateTime?
    ): CloseableSequence<OrderAttributeCsvRow> {
        val definitionIds = definitionRepository.findAll()
            .sortedBy { it.id.value }
            .map { it.id.value }
        val stream = orderRepository.streamOrdersWithAttributesSqlPivot(from, to, definitionIds)
        return CloseableSequence(
            delegate = stream.asSequence().map { row -> row.toCsvRow() },
            closeAction = stream::close
        )
    }
}
