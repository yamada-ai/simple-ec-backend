package com.example.ec.application.export

import org.springframework.stereotype.Service
import java.io.PrintWriter
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.time.LocalDateTime

@Service
class OrderAttributeExportBenchmarkService(
    private val orderAttributeExportService: OrderAttributeExportService
) {
    fun measure(
        from: LocalDateTime?,
        to: LocalDateTime?,
        strategy: String
    ): OrderAttributeExportBenchmarkResult {
        val digest = MessageDigest.getInstance("MD5")
        val digestOutputStream = DigestOutputStream(NullOutputStream(), digest)

        val startTime = System.nanoTime()
        digestOutputStream.use { output ->
            val writer = PrintWriter(output, true, Charsets.UTF_8)
            orderAttributeExportService.writeCsv(from, to, strategy, writer)
            writer.flush()
        }
        val elapsedMs = (System.nanoTime() - startTime) / NANOS_PER_MILLI

        return OrderAttributeExportBenchmarkResult(
            strategy = strategy,
            elapsedMs = elapsedMs,
            md5 = digest.digest().joinToString("") { "%02x".format(it) }
        )
    }

    companion object {
        private const val NANOS_PER_MILLI = 1_000_000L
    }
}

data class OrderAttributeExportBenchmarkResult(
    val strategy: String,
    val elapsedMs: Long,
    val md5: String
)
