package com.example.ec.application.export

import com.example.ec.domain.order.OrderRepository
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import kotlin.streams.asSequence

@Component
class MultisetOrderAttributeRowSource(
    private val orderRepository: OrderRepository
) : OrderAttributeRowSource {

    override val strategy: AttributeExportStrategy = AttributeExportStrategy.MULTISET

    override fun rows(
        from: LocalDateTime?,
        to: LocalDateTime?
    ): CloseableSequence<OrderAttributeCsvRow> {
        val stream = orderRepository.fetchOrdersWithAttributesMultiset(from, to)
        return CloseableSequence(
            delegate = stream.asSequence().map { order -> order.toCsvRow() },
            closeAction = stream::close
        )
    }
}
