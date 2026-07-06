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
        strategy: String,
        mode: String
    ): OrderAttributeExportBenchmarkResult {
        return when (BenchmarkMode.from(mode)) {
            BenchmarkMode.CSV -> measureCsv(from, to, strategy)
            BenchmarkMode.ROWS -> measureRows(from, to, strategy)
        }
    }

    private fun measureCsv(
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
            mode = BenchmarkMode.CSV.wireName,
            elapsedMs = elapsedMs,
            md5 = digest.digest().joinToString("") { "%02x".format(it) },
            rowCount = null,
            attributeValueCount = null,
            orderIdChecksum = null
        )
    }

    private fun measureRows(
        from: LocalDateTime?,
        to: LocalDateTime?,
        strategy: String
    ): OrderAttributeExportBenchmarkResult {
        val startTime = System.nanoTime()
        val drainResult = orderAttributeExportService.drainRows(from, to, strategy)
        val elapsedMs = (System.nanoTime() - startTime) / NANOS_PER_MILLI

        return OrderAttributeExportBenchmarkResult(
            strategy = strategy,
            mode = BenchmarkMode.ROWS.wireName,
            elapsedMs = elapsedMs,
            md5 = null,
            rowCount = drainResult.rowCount,
            attributeValueCount = drainResult.attributeValueCount,
            orderIdChecksum = drainResult.orderIdChecksum
        )
    }

    companion object {
        private const val NANOS_PER_MILLI = 1_000_000L
    }
}

data class OrderAttributeExportBenchmarkResult(
    val strategy: String,
    val mode: String,
    val elapsedMs: Long,
    val md5: String?,
    val rowCount: Long?,
    val attributeValueCount: Long?,
    val orderIdChecksum: Long?
)

enum class BenchmarkMode(val wireName: String) {
    CSV("csv"),
    ROWS("rows");

    companion object {
        fun from(value: String): BenchmarkMode {
            return entries.firstOrNull { it.wireName == value.lowercase() } ?: CSV
        }
    }
}
