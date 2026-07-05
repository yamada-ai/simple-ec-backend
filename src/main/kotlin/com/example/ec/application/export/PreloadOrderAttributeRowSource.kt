package com.example.ec.application.export

import com.example.ec.domain.order.OrderRepository
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import kotlin.streams.asSequence

@Component
class PreloadOrderAttributeRowSource(
    private val orderRepository: OrderRepository
) : OrderAttributeRowSource {

    override val strategy: AttributeExportStrategy = AttributeExportStrategy.PRELOAD

    override fun rows(
        from: LocalDateTime?,
        to: LocalDateTime?
    ): CloseableSequence<OrderAttributeCsvRow> {
        val attrMap = orderRepository.loadAttributeValueMap(from, to)
        val stream = orderRepository.streamOrdersBase(from, to)
        return CloseableSequence(
            delegate = stream.asSequence()
                .map { base -> base.toCsvRow(attrMap[base.orderId] ?: emptyMap()) },
            closeAction = stream::close
        )
    }
}
