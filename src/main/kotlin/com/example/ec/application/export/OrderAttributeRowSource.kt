package com.example.ec.application.export

import java.time.LocalDateTime

interface OrderAttributeRowSource {
    val strategy: AttributeExportStrategy

    fun rows(
        from: LocalDateTime?,
        to: LocalDateTime?
    ): CloseableSequence<OrderAttributeCsvRow>
}
