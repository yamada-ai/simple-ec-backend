package com.example.ec.presentation.mapper

import com.example.ec.application.export.OrderAttributeExportBenchmarkResult
import com.example.ec.presentation.model.BenchmarkResult

fun OrderAttributeExportBenchmarkResult.toResponse(): BenchmarkResult {
    return BenchmarkResult(
        strategy = strategy,
        elapsedMs = elapsedMs,
        md5 = md5
    )
}
