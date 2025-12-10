package com.example.ec.application.export

import com.example.ec.domain.attribute.OrderAttributeDefinitionRepository
import com.example.ec.domain.order.OrderAttributeJoinedRow
import com.example.ec.domain.order.OrderRepository
import org.springframework.stereotype.Service
import java.io.PrintWriter
import java.time.LocalDateTime
import java.util.Locale

/**
 * 注文属性付きCSV出力のサービス（横展開）。
 * strategy パラメータで実験的に切り替え可能にする。
 */
@Service
class OrderAttributeExportService(
    private val orderRepository: OrderRepository,
    private val definitionRepository: OrderAttributeDefinitionRepository
) {

    enum class AttributeExportStrategy {
        JOIN,
        MULTISET,
        SEQUENCE_WINDOW,
        STREAM_WINDOW,
        SPLITERATOR_WINDOW;

        companion object {
            fun from(value: String): AttributeExportStrategy =
                entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: JOIN
        }
    }

    fun writeCsv(
        from: LocalDateTime?,
        to: LocalDateTime?,
        strategy: String,
        writer: PrintWriter
    ) {
        val definitions = definitionRepository.findAll()
        val definitionIds = definitions.map { it.id.value }
        val definitionLabels = definitions.map { it.label }
        val exportStrategy = AttributeExportStrategy.from(strategy)

        writeHeader(definitionLabels, writer)

        when (exportStrategy) {
            AttributeExportStrategy.JOIN,
            AttributeExportStrategy.STREAM_WINDOW,
            AttributeExportStrategy.SPLITERATOR_WINDOW -> {
                orderRepository.streamOrdersWithAttributes(from, to).use { stream ->
                    writeWindowed(definitionIds, stream.iterator(), writer)
                }
            }

            AttributeExportStrategy.SEQUENCE_WINDOW -> {
                orderRepository.streamOrdersWithAttributes(from, to).use { stream ->
                    writeWindowed(definitionIds, stream.iterator().asSequence().iterator(), writer)
                }
            }

            AttributeExportStrategy.MULTISET -> {
                orderRepository.streamOrdersWithAttributes(from, to).use { stream ->
                    writeMultiset(definitionIds, stream, writer)
                }
            }
        }
    }

    private fun writeHeader(attributeLabels: List<String>, writer: PrintWriter) {
        val base = listOf("order_id", "customer_id", "customer_name", "customer_email", "order_date")
        val header = (base + attributeLabels).joinToString(",")
        writer.println(header)
    }

    private fun writeWindowed(
        definitionIds: List<Long>,
        iterator: Iterator<OrderAttributeJoinedRow>,
        writer: PrintWriter
    ) {
        var currentOrderId: Long? = null
        var currentRow: OrderAttributeJoinedRow? = null
        val valueMap = mutableMapOf<Long, String>()

        fun flush() {
            val base = currentRow ?: return
            val values = definitionIds.map { defId -> valueMap[defId] ?: "" }
            writer.println(
                listOf(
                    base.orderId.toString(),
                    base.customerId.toString(),
                    base.customerName,
                    base.customerEmail,
                    base.orderDate.toString()
                ).plus(values).joinToString(",")
            )
            valueMap.clear()
        }

        iterator.forEach { row ->
            if (currentOrderId == null) {
                currentOrderId = row.orderId
                currentRow = row
            } else if (currentOrderId != row.orderId) {
                flush()
                currentOrderId = row.orderId
                currentRow = row
            }

            if (row.definitionId != null && row.value != null) {
                valueMap[row.definitionId] = row.value
            }
        }

        flush()
    }

    private fun writeMultiset(
        definitionIds: List<Long>,
        stream: java.util.stream.Stream<OrderAttributeJoinedRow>,
        writer: PrintWriter
    ) {
        val grouped = stream.toList().groupBy { it.orderId }
        grouped.toSortedMap().forEach { (_, rows) ->
            if (rows.isEmpty()) return@forEach
            val base = rows.first()
            val valueMap = rows
                .filter { it.definitionId != null && it.value != null }
                .associate { it.definitionId!! to it.value!! }
            val values = definitionIds.map { valueMap[it] ?: "" }
            writer.println(
                listOf(
                    base.orderId.toString(),
                    base.customerId.toString(),
                    base.customerName,
                    base.customerEmail,
                    base.orderDate.toString()
                ).plus(values).joinToString(",")
            )
        }
    }
}
