package com.example.ec.application.export

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter
import org.springframework.stereotype.Component
import java.io.PrintWriter

@Component
class OrderAttributeCsvWriter {

    fun write(
        schema: OrderAttributeExportSchema,
        rows: Sequence<OrderAttributeCsvRow>,
        writer: PrintWriter
    ) {
        val csvPrinter = CSVPrinter(writer, csvFormat)
        csvPrinter.printRecord(schema.header)
        rows.forEach { row -> csvPrinter.printRecord(row.toRecord(schema)) }
        csvPrinter.flush()
    }

    companion object {
        private val csvFormat: CSVFormat = CSVFormat.DEFAULT.builder()
            .setRecordSeparator("\n")
            .build()
    }
}
