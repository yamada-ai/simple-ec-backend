package com.example.ec.presentation.controller

import com.example.ec.application.export.OrderAttributeExportService
import com.example.ec.infrastructure.io.NullOutputStream
import com.example.ec.presentation.model.BenchmarkResult
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
import java.security.DigestOutputStream
import java.security.MessageDigest
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
        @RequestParam(required = false, defaultValue = "sequence-window") strategy: String
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

    /**
     * ベンチマーク専用エンドポイント。
     * NullOutputStream + MD5 で I/O コストを除外した計算コストのみを測定。
     */
    @GetMapping("/api/export/orders/attributes/benchmark")
    fun benchmarkOrderAttributes(
        @RequestParam(required = false) startDate: OffsetDateTime?,
        @RequestParam(required = false) endDate: OffsetDateTime?,
        @RequestParam(required = false, defaultValue = "sequence-window") strategy: String
    ): ResponseEntity<BenchmarkResult> {
        val from = startDate?.atZoneSameInstant(ZoneId.systemDefault())?.toLocalDateTime()
        val to = endDate?.atZoneSameInstant(ZoneId.systemDefault())?.toLocalDateTime()

        val digest = MessageDigest.getInstance("MD5")
        val nullOutputStream = NullOutputStream()
        val digestOutputStream = DigestOutputStream(nullOutputStream, digest)

        val startTime = System.nanoTime()
        digestOutputStream.use { output ->
            val writer = PrintWriter(output, true, Charsets.UTF_8)
            orderAttributeExportService.writeCsv(from, to, strategy, writer)
            writer.flush()
        }
        val elapsedMs = (System.nanoTime() - startTime) / NANOS_PER_MILLI

        val md5Hash = digest.digest().joinToString("") { "%02x".format(it) }

        return ResponseEntity.ok(
            BenchmarkResult(
                strategy = strategy,
                elapsedMs = elapsedMs,
                md5 = md5Hash
            )
        )
    }

    companion object {
        private const val BUFFER_SIZE = 8 * 1024
        private const val NANOS_PER_MILLI = 1_000_000L
    }
}
