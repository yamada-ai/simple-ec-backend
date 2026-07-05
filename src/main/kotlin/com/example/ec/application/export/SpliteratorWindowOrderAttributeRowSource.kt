package com.example.ec.application.export

import com.example.ec.domain.order.OrderRepository
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.util.stream.StreamSupport
import kotlin.streams.asSequence

@Component
class SpliteratorWindowOrderAttributeRowSource(
    private val orderRepository: OrderRepository
) : OrderAttributeRowSource {

    override val strategy: AttributeExportStrategy = AttributeExportStrategy.SPLITERATOR_WINDOW

    override fun rows(
        from: LocalDateTime?,
        to: LocalDateTime?
    ): CloseableSequence<OrderAttributeCsvRow> {
        val stream = orderRepository.streamOrdersWithAttributes(from, to)
        val windowedStream = StreamSupport.stream(OrderAttributeWindowSpliterator(stream.spliterator()), false)
        return CloseableSequence(
            delegate = windowedStream.asSequence(),
            closeAction = {
                windowedStream.close()
                stream.close()
            }
        )
    }
}
