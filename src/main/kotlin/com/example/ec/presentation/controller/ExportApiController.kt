package com.example.ec.presentation.controller

import com.example.ec.application.export.OrderExportService
import com.example.ec.application.export.OrderCsvRow
import com.example.ec.presentation.api.ExportApi
import org.slf4j.LoggerFactory
import org.springframework.core.io.Resource
import org.springframework.core.io.InputStreamResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.io.PrintWriter
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlin.concurrent.thread

/**
 * CSV出力APIコントローラ
 */
@RestController
class ExportApiController(
    private val orderExportService: OrderExportService
) : ExportApi {

    private val logger = LoggerFactory.getLogger(ExportApiController::class.java)

    override fun exportOrders(
        startDate: OffsetDateTime?,
        endDate: OffsetDateTime?,
        strategy: String
    ): ResponseEntity<Resource> {
        // OffsetDateTimeをLocalDateTimeに変換
        val from = startDate?.atZoneSameInstant(ZoneId.systemDefault())?.toLocalDateTime()
        val to = endDate?.atZoneSameInstant(ZoneId.systemDefault())?.toLocalDateTime()

        // strategy パラメータは Phase 1 では未使用（Phase 2 で使用予定）
        logger.info("Starting CSV export: from=$from, to=$to, strategy=$strategy (ignored in Phase 1)")

        // ストリーミング用のPipedStream
        // PipedInputStreamとPipedOutputStreamで、書き込みと読み込みを並行実行
        // バッファサイズを 8KB に設定（デフォルトの 1KB では詰まる可能性あり）
        val pipedOutputStream = PipedOutputStream()
        val pipedInputStream = PipedInputStream(pipedOutputStream, BUFFER_SIZE)

        // 別スレッドでCSV書き込み（ストリーミング）
        // メインスレッドはInputStreamResourceを即座に返し、クライアントへの送信を開始
        thread(start = true, name = "csv-export") {
            @Suppress("TooGenericExceptionCaught") // ストリーミング中の全ての例外をキャッチして伝播
            try {
                pipedOutputStream.use { outputStream ->
                    val writer = PrintWriter(outputStream, true, Charsets.UTF_8)

                    // CSVヘッダーを書き込み
                    writer.println(OrderCsvRow.CSV_HEADER)

                    // サービスからCSV行のストリームを取得
                    val csvRows = orderExportService.exportOrders(from, to)

                    // Stream.use{}で確実にcloseしてリソースリーク（DBコネクション等）を防ぐ
                    var rowCount = 0
                    csvRows.use { stream ->
                        stream.forEach { row ->
                            writer.println(row.toCsvLine())
                            rowCount++
                        }
                    }

                    writer.flush()
                    logger.info("CSV export completed: rows=$rowCount")
                }
            } catch (e: Throwable) {
                // エラーログを出力
                logger.error("CSV export failed: error=${e.message}", e)

                // PipedOutputStream を確実にcloseして、クライアント側にストリーム終了を通知
                // (use{} で既にcloseされるが、例外が発生した場合も確実にcloseされることを明示)
                try {
                    pipedOutputStream.close()
                } catch (closeException: Exception) {
                    logger.error("Failed to close PipedOutputStream", closeException)
                }

                // 例外を再スローして、スレッドのUncaughtExceptionHandlerに通知
                throw e
            }
        }

        // InputStreamResourceとしてラップ
        // この時点でまだCSV生成は完了していないが、ストリーミングで逐次読み取られる
        val resource = InputStreamResource(pipedInputStream)

        // ファイル名を生成
        val filename = "orders_export_${System.currentTimeMillis()}.csv"

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$filename\"")
            .contentType(MediaType.parseMediaType("text/csv"))
            .body(resource)
    }

    companion object {
        /**
         * PipedInputStream のバッファサイズ（8KB）
         * デフォルトの 1KB では行サイズや書き込み速度によって詰まる可能性があるため拡大
         */
        private const val BUFFER_SIZE = 8 * 1024 // 8KB
    }
}
