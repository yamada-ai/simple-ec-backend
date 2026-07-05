package com.example.ec.application.export

import com.example.ec.domain.order.OrderRepository
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import kotlin.streams.asSequence

@Component
class SequenceWindowOrderAttributeRowSource(
    private val orderRepository: OrderRepository
) : OrderAttributeRowSource {

    override val strategy: AttributeExportStrategy = AttributeExportStrategy.SEQUENCE_WINDOW

    override fun rows(
        from: LocalDateTime?,
        to: LocalDateTime?
    ): CloseableSequence<OrderAttributeCsvRow> {
        val stream = orderRepository.streamOrdersWithAttributes(from, to)
        return CloseableSequence(
            delegate = stream.asSequence().windowByOrderId(),
            closeAction = stream::close
        )
    }
}
