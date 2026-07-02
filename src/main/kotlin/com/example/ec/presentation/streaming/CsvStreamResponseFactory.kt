package com.example.ec.presentation.streaming

import org.slf4j.LoggerFactory
import org.springframework.core.io.InputStreamResource
import org.springframework.core.io.Resource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import java.io.IOException
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.io.PrintWriter
import kotlin.concurrent.thread

@Component
class CsvStreamResponseFactory {

    private val logger = LoggerFactory.getLogger(CsvStreamResponseFactory::class.java)

    fun streamCsv(
        filenamePrefix: String,
        threadName: String,
        writeCsv: (PrintWriter) -> Unit
    ): ResponseEntity<Resource> {
        val pipedOutputStream = PipedOutputStream()
        val pipedInputStream = PipedInputStream(pipedOutputStream, BUFFER_SIZE)

        thread(start = true, name = threadName) {
            @Suppress("TooGenericExceptionCaught")
            try {
                pipedOutputStream.use { output ->
                    val writer = PrintWriter(output, true, Charsets.UTF_8)
                    writeCsv(writer)
                    writer.flush()
                }
            } catch (e: Throwable) {
                logger.error("CSV streaming failed: thread=$threadName, error=${e.message}", e)
                closeQuietly(pipedOutputStream)
                throw e
            }
        }

        val resource = InputStreamResource(pipedInputStream)
        val filename = "${filenamePrefix}_${System.currentTimeMillis()}.csv"

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$filename\"")
            .contentType(MediaType.parseMediaType("text/csv"))
            .body(resource)
    }

    private fun closeQuietly(outputStream: PipedOutputStream) {
        try {
            outputStream.close()
        } catch (closeException: IOException) {
            logger.error("Failed to close CSV output stream", closeException)
        }
    }

    companion object {
        private const val BUFFER_SIZE = 8 * 1024
    }
}
