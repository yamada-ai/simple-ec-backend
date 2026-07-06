package com.example.ec.presentation.mapper

import com.example.ec.application.export.OrderAttributeExportBenchmarkResult
import com.example.ec.presentation.model.BenchmarkResult

fun OrderAttributeExportBenchmarkResult.toResponse(): BenchmarkResult {
    return BenchmarkResult(
        strategy = strategy,
        mode = mode,
        elapsedMs = elapsedMs,
        md5 = md5,
        rowCount = rowCount,
        attributeValueCount = attributeValueCount,
        orderIdChecksum = orderIdChecksum
    )
}
