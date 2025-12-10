package com.example.ec.presentation.controller

import com.example.ec.application.export.OrderAttributeExportService
import org.springframework.core.io.InputStreamResource
import org.springframework.core.io.Resource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.io.PrintWriter
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlin.concurrent.thread

/**
 * 動的注文属性を含むCSVエクスポート（Phase2）
 */
@RestController
class ExportAttributesController(
    private val orderAttributeExportService: OrderAttributeExportService
) {

    @GetMapping("/api/export/orders/attributes")
    fun exportOrderAttributes(
        @RequestParam(required = false) startDate: OffsetDateTime?,
        @RequestParam(required = false) endDate: OffsetDateTime?,
        @RequestParam(required = false, defaultValue = "join") strategy: String
    ): ResponseEntity<Resource> {
        val from = startDate?.atZoneSameInstant(ZoneId.systemDefault())?.toLocalDateTime()
        val to = endDate?.atZoneSameInstant(ZoneId.systemDefault())?.toLocalDateTime()

        val pipedOutputStream = PipedOutputStream()
        val pipedInputStream = PipedInputStream(pipedOutputStream, BUFFER_SIZE)

        thread(start = true, name = "csv-attributes-export") {
            pipedOutputStream.use { output ->
                val writer = PrintWriter(output, true, Charsets.UTF_8)
                orderAttributeExportService.writeCsv(from, to, strategy, writer)
                writer.flush()
            }
        }

        val resource = InputStreamResource(pipedInputStream)
        val filename = "orders_attributes_${System.currentTimeMillis()}.csv"

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$filename\"")
            .contentType(MediaType.parseMediaType("text/csv"))
            .body(resource)
    }

    companion object {
        private const val BUFFER_SIZE = 8 * 1024
    }
}
