package com.example.ec.application.export

import com.example.ec.domain.attribute.OrderAttributeDefinitionRepository
import com.example.ec.domain.order.OrderAttributeJoinedRow
import com.example.ec.domain.order.OrderRepository
import org.springframework.stereotype.Service
import java.io.PrintWriter
import java.time.LocalDateTime

/**
 * 注文属性付きCSV出力のサービス（横展開）。
 * strategy は現状すべて同一実装だが、切替可能なようインターフェースを確保。
 */
@Service
class OrderAttributeExportService(
    private val orderRepository: OrderRepository,
    private val definitionRepository: OrderAttributeDefinitionRepository
) {

    fun writeCsv(
        from: LocalDateTime?,
        to: LocalDateTime?,
        strategy: String,
        writer: PrintWriter
    ) {
        val definitions = definitionRepository.findAll()
        writeHeader(definitions.map { it.label }, writer)

        // 1クエリ JOIN ストリームを利用
        val rows = orderRepository.streamOrdersWithAttributes(from, to)
        rows.use { stream ->
            when (strategy) {
                "multiset",
                "sequence-window",
                "stream-window",
                "spliterator-window",
                "join" -> writeBody(definitions.map { it.id.value }, stream, writer)
                else -> writeBody(definitions.map { it.id.value }, stream, writer)
            }
        }
    }

    private fun writeHeader(attributeLabels: List<String>, writer: PrintWriter) {
        val base = listOf("order_id", "customer_id", "customer_name", "customer_email", "order_date")
        val header = (base + attributeLabels).joinToString(",")
        writer.println(header)
    }

    private fun writeBody(
        definitionIds: List<Long>,
        rows: java.util.stream.Stream<OrderAttributeJoinedRow>,
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

        rows.forEach { row ->
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
}
