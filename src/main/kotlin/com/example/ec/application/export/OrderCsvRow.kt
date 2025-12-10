package com.example.ec.application.export

import org.apache.commons.csv.CSVFormat
import java.io.StringWriter
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * CSV出力用の注文データ（1注文明細 = 1行）
 *
 * Order（親）とOrderItem（子）を1→多で展開したデータ構造
 * Phase 1 仕様: order_id,customer_id,customer_name,customer_email,order_date,product_name,quantity,unit_price
 */
data class OrderCsvRow(
    // Order情報
    val orderId: Long,

    // Customer情報
    val customerId: Long,
    val customerName: String,
    val customerEmail: String,

    // Order情報
    val orderDate: LocalDateTime,

    // OrderItem情報
    val productName: String,
    val quantity: Int,
    val unitPrice: BigDecimal
) {
    companion object {
        private val CSV_FORMAT = CSVFormat.DEFAULT

        /**
         * CSVヘッダー行
         */
        const val CSV_HEADER =
            "order_id,customer_id,customer_name,customer_email,order_date,product_name,quantity,unit_price"
    }

    /**
     * CSV行として出力する（Apache Commons CSV でエスケープ処理）
     */
    fun toCsvLine(): String {
        val writer = StringWriter()
        CSV_FORMAT.print(writer).use { printer ->
            printer.printRecord(
                orderId,
                customerId,
                customerName,
                customerEmail,
                orderDate,
                productName,
                quantity,
                unitPrice
            )
        }
        return writer.toString().trim()
    }
}
