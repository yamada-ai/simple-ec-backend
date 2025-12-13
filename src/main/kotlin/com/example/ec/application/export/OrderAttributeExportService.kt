package com.example.ec.application.export

import com.example.ec.domain.attribute.OrderAttributeDefinitionRepository
import com.example.ec.domain.order.OrderRepository
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter
import java.util.stream.StreamSupport
import kotlin.streams.asSequence
import org.springframework.stereotype.Service
import java.io.PrintWriter
import java.time.LocalDateTime

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
        MULTISET,
        SEQUENCE_WINDOW,
        SPLITERATOR_WINDOW,
        PRELOAD;

        companion object {
            fun from(value: String): AttributeExportStrategy {
                val normalized = value.replace("-", "_").uppercase()
                return entries.firstOrNull { it.name == normalized } ?: SEQUENCE_WINDOW
            }
        }
    }

    @Suppress("NestedBlockDepth", "CyclomaticComplexMethod") // 戦略分岐を1箇所に集約しているため抑制
    fun writeCsv(
        from: LocalDateTime?,
        to: LocalDateTime?,
        strategy: String,
        writer: PrintWriter
    ) {
        val definitions = definitionRepository.findAll().sortedBy { it.id.value }
        val definitionIds = definitions.map { it.id.value }
        val definitionLabels = definitions.map { it.label }
        val exportStrategy = AttributeExportStrategy.from(strategy)
        val csvPrinter = CSVPrinter(writer, csvFormat)

        writeHeader(definitionLabels, csvPrinter)

        when (exportStrategy) {
            // SPLITERATOR_WINDOW: Streamの世界のまま「1要素=1注文」に昇格させる（低レイヤ寄り）
            AttributeExportStrategy.SPLITERATOR_WINDOW -> {
                orderRepository.streamOrdersWithAttributes(from, to).use { stream ->
                    val spliterator = OrderAttributeWindowSpliterator(stream.spliterator())
                    StreamSupport.stream(spliterator, false)
                        .forEach { row -> csvPrinter.printRecord(row.toRecord(definitionIds)) }
                }
            }

            // SEQUENCE_WINDOW: Kotlin Sequence の lazy パイプラインとして窓処理を表現する（高レイヤ寄り）
            AttributeExportStrategy.SEQUENCE_WINDOW -> {
                orderRepository.streamOrdersWithAttributes(from, to).use { stream ->
                    stream.asSequence()
                        .windowByOrderId()
                        .forEach { row ->
                            csvPrinter.printRecord(row.toRecord(definitionIds))
                        }
                }
            }

            AttributeExportStrategy.MULTISET -> {
                orderRepository.fetchOrdersWithAttributesMultiset(from, to).use { stream ->
                    writeMultiset(definitionIds, stream, csvPrinter)
                }
            }

            AttributeExportStrategy.PRELOAD -> {
                val attrMap = orderRepository.loadAttributeValueMap(from, to)
                orderRepository.streamOrdersBase(from, to).use { stream ->
                    stream.forEach { base ->
                        val row = OrderAttributeCsvRow(
                            orderId = base.orderId,
                            customerId = base.customerId,
                            customerName = base.customerName,
                            customerEmail = base.customerEmail,
                            orderDate = base.orderDate,
                            attributes = attrMap[base.orderId] ?: emptyMap()
                        )
                        csvPrinter.printRecord(row.toRecord(definitionIds))
                    }
                }
            }
        }

        csvPrinter.flush()
    }

    private fun writeHeader(attributeLabels: List<String>, printer: CSVPrinter) {
        val base = listOf("order_id", "customer_id", "customer_name", "customer_email", "order_date")
        printer.printRecord(base + attributeLabels)
    }

    private fun writeMultiset(
        definitionIds: List<Long>,
        stream: java.util.stream.Stream<com.example.ec.domain.order.OrderWithAttributes>,
        printer: CSVPrinter
    ) {
        stream.forEach { order ->
            val valueMap = order.attributes.associate { it.attributeDefinitionId.value to it.value }
            val row = OrderAttributeCsvRow(
                orderId = order.orderId,
                customerId = order.customerId,
                customerName = order.customerName,
                customerEmail = order.customerEmail,
                orderDate = order.orderDate,
                attributes = valueMap
            )
            printer.printRecord(row.toRecord(definitionIds))
        }
    }

    companion object {
        private val csvFormat: CSVFormat = CSVFormat.DEFAULT.builder()
            .setRecordSeparator("\n")
            .build()
    }
}
