package com.example.ec.presentation.controller

import com.example.ec.application.export.OrderAttributeExportBenchmarkService
import com.example.ec.application.export.OrderAttributeExportService
import com.example.ec.presentation.mapper.toResponse
import com.example.ec.presentation.model.BenchmarkResult
import com.example.ec.presentation.streaming.CsvStreamResponseFactory
import com.example.ec.presentation.support.toSystemLocalDateTime
import org.springframework.core.io.Resource
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime

/**
 * 動的注文属性を含むCSVエクスポート（Phase2）
 */
@RestController
class ExportAttributesController(
    private val orderAttributeExportService: OrderAttributeExportService,
    private val benchmarkService: OrderAttributeExportBenchmarkService,
    private val csvStreamResponseFactory: CsvStreamResponseFactory
) {

    @GetMapping("/api/export/orders/attributes")
    fun exportOrderAttributes(
        @RequestParam(required = false) startDate: OffsetDateTime?,
        @RequestParam(required = false) endDate: OffsetDateTime?,
        @RequestParam(required = false, defaultValue = "sequence-window") strategy: String
    ): ResponseEntity<Resource> {
        val from = startDate.toSystemLocalDateTime()
        val to = endDate.toSystemLocalDateTime()

        return csvStreamResponseFactory.streamCsv("orders_attributes", "csv-attributes-export") { writer ->
            orderAttributeExportService.writeCsv(from, to, strategy, writer)
        }
    }

    /**
     * ベンチマーク専用エンドポイント。
     * NullOutputStream + MD5 で I/O コストを除外した計算コストのみを測定。
     */
    @GetMapping("/api/export/orders/attributes/benchmark")
    fun benchmarkOrderAttributes(
        @RequestParam(required = false) startDate: OffsetDateTime?,
        @RequestParam(required = false) endDate: OffsetDateTime?,
        @RequestParam(required = false, defaultValue = "sequence-window") strategy: String,
        @RequestParam(required = false, defaultValue = "csv") mode: String
    ): ResponseEntity<BenchmarkResult> {
        val from = startDate.toSystemLocalDateTime()
        val to = endDate.toSystemLocalDateTime()
        return ResponseEntity.ok(benchmarkService.measure(from, to, strategy, mode).toResponse())
    }
}
