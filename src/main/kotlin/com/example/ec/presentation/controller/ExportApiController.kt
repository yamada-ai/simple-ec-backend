package com.example.ec.presentation.controller

import com.example.ec.application.export.OrderExportService
import com.example.ec.presentation.api.ExportApi
import com.example.ec.presentation.streaming.CsvStreamResponseFactory
import com.example.ec.presentation.support.toSystemLocalDateTime
import org.slf4j.LoggerFactory
import org.springframework.core.io.Resource
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime

/**
 * CSV出力APIコントローラ
 */
@RestController
class ExportApiController(
    private val orderExportService: OrderExportService,
    private val csvStreamResponseFactory: CsvStreamResponseFactory
) : ExportApi {

    private val logger = LoggerFactory.getLogger(ExportApiController::class.java)

    override fun exportOrders(
        startDate: OffsetDateTime?,
        endDate: OffsetDateTime?,
        strategy: String
    ): ResponseEntity<Resource> {
        val from = startDate.toSystemLocalDateTime()
        val to = endDate.toSystemLocalDateTime()

        // strategy パラメータは Phase 1 では未使用（Phase 2 で使用予定）
        logger.info("Starting CSV export: from=$from, to=$to, strategy=$strategy (ignored in Phase 1)")

        return csvStreamResponseFactory.streamCsv("orders_export", "csv-export") { writer ->
            val rowCount = orderExportService.writeCsv(from, to, writer)
            logger.info("CSV export completed: rows=$rowCount")
        }
    }
}
