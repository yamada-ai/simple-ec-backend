package com.example.ec.application.export

import com.example.ec.domain.order.OrderRepository
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import kotlin.streams.asSequence

@Component
class JsonAggregationOrderAttributeRowSource(
    private val orderRepository: OrderRepository
) : OrderAttributeRowSource {

    override val strategy: AttributeExportStrategy = AttributeExportStrategy.JSON_AGGREGATION

    override fun rows(
        from: LocalDateTime?,
        to: LocalDateTime?
    ): CloseableSequence<OrderAttributeCsvRow> {
        val stream = orderRepository.streamOrdersWithAttributesJsonAggregation(from, to)
        return CloseableSequence(
            delegate = stream.asSequence().map { row -> row.toCsvRow() },
            closeAction = stream::close
        )
    }
}
