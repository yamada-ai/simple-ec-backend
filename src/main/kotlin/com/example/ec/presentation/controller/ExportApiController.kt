package com.example.ec.presentation.controller

import com.example.ec.application.export.ExportOrdersUseCase
import com.example.ec.application.export.OrderCsvRow
import com.example.ec.presentation.api.ExportApi
import org.springframework.core.io.Resource
import org.springframework.core.io.InputStreamResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.PrintWriter
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * CSV出力APIコントローラ
 */
@RestController
class ExportApiController(
    private val exportOrdersUseCase: ExportOrdersUseCase
) : ExportApi {

    override fun exportOrders(
        startDate: OffsetDateTime?,
        endDate: OffsetDateTime?,
        strategy: String
    ): ResponseEntity<Resource> {
        // OffsetDateTimeをLocalDateTimeに変換
        val from = startDate?.atZoneSameInstant(ZoneId.systemDefault())?.toLocalDateTime()
        val to = endDate?.atZoneSameInstant(ZoneId.systemDefault())?.toLocalDateTime()

        // CSV生成
        val csvStream = generateCsvStream(from, to, strategy)

        // InputStreamResourceとしてラップ
        val resource = InputStreamResource(csvStream)

        // ファイル名を生成
        val filename = "orders_export_${System.currentTimeMillis()}.csv"

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$filename\"")
            .contentType(MediaType.parseMediaType("text/csv"))
            .body(resource)
    }

    private fun generateCsvStream(
        from: LocalDateTime?,
        to: LocalDateTime?,
        strategyName: String
    ): InputStream {
        // ByteArrayOutputStreamに一旦書き込む
        // 本来はStreamingResponseBodyを使うべきだが、OpenAPI生成コードがResourceを返すため、この方法を採用
        val byteArrayOutputStream = ByteArrayOutputStream()
        val writer = PrintWriter(byteArrayOutputStream, true, Charsets.UTF_8)

        try {
            // CSVヘッダーを書き込み
            writer.println(OrderCsvRow.csvHeader())

            // UseCaseからCSV行のストリームを取得して書き込み
            val csvRows = exportOrdersUseCase.execute(from, to, strategyName)
            csvRows.forEach { row ->
                writer.println(row.toCsvLine())
            }

            writer.flush()
        } finally {
            writer.close()
        }

        // ByteArrayInputStreamに変換して返す
        return ByteArrayInputStream(byteArrayOutputStream.toByteArray())
    }
}
