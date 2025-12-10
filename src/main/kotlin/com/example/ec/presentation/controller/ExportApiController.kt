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

        // ストリーミング用のPipedStream
        // PipedInputStreamとPipedOutputStreamで、書き込みと読み込みを並行実行
        val pipedOutputStream = PipedOutputStream()
        val pipedInputStream = PipedInputStream(pipedOutputStream)

        // 別スレッドでCSV書き込み（ストリーミング）
        // メインスレッドはInputStreamResourceを即座に返し、クライアントへの送信を開始
        thread(start = true, name = "csv-export-$strategy") {
            @Suppress("TooGenericExceptionCaught") // ストリーミング中の全ての例外をキャッチして伝播
            try {
                pipedOutputStream.use { outputStream ->
                    val writer = PrintWriter(outputStream, true, Charsets.UTF_8)

                    // CSVヘッダーを書き込み
                    writer.println(OrderCsvRow.CSV_HEADER)

                    // UseCaseからCSV行のストリームを取得
                    val csvRows = exportOrdersUseCase.execute(from, to, strategy)

                    // Stream.use{}で確実にcloseしてリソースリーク（DBコネクション等）を防ぐ
                    csvRows.use { stream ->
                        stream.forEach { row ->
                            writer.println(row.toCsvLine())
                        }
                    }

                    writer.flush()
                }
            } catch (e: Throwable) {
                // エラーハンドリング（本来はログ出力など）
                e.printStackTrace()
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
}
