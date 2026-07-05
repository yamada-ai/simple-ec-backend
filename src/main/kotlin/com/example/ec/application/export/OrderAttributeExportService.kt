package com.example.ec.application.export

import com.example.ec.domain.attribute.OrderAttributeDefinitionRepository
import org.springframework.stereotype.Service
import java.io.PrintWriter
import java.time.LocalDateTime

/**
 * 注文属性付きCSV出力のサービス（横展開）。
 * strategy パラメータで実験的に切り替え可能にする。
 */
@Service
class OrderAttributeExportService(
    private val definitionRepository: OrderAttributeDefinitionRepository,
    rowSources: List<OrderAttributeRowSource>,
    private val csvWriter: OrderAttributeCsvWriter
) {
    private val rowSourcesByStrategy: Map<AttributeExportStrategy, OrderAttributeRowSource> =
        rowSources.associateBy { it.strategy }

    init {
        require(rowSourcesByStrategy.size == rowSources.size) {
            "Duplicate order attribute export row source strategies are registered."
        }
    }

    fun writeCsv(
        from: LocalDateTime?,
        to: LocalDateTime?,
        strategy: String,
        writer: PrintWriter
    ) {
        val schema = OrderAttributeExportSchema.from(definitionRepository.findAll())
        val exportStrategy = AttributeExportStrategy.from(strategy)
        val rowSource = rowSourceFor(exportStrategy)
        rowSource.rows(from, to).use { rows ->
            csvWriter.write(schema, rows, writer)
        }
    }

    private fun rowSourceFor(strategy: AttributeExportStrategy): OrderAttributeRowSource {
        return rowSourcesByStrategy[strategy]
            ?: error("Order attribute export row source is not registered: strategy=$strategy")
    }
}
