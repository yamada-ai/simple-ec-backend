package com.example.ec.application.export

import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * CSV出力用の注文データ（1注文明細 = 1行）
 *
 * Order（親）とOrderItem（子）を1→多で展開したデータ構造
 */
data class OrderCsvRow(
    // Order情報
    val orderId: Long,
    val orderDate: LocalDateTime,
    val totalAmount: BigDecimal,

    // Customer情報
    val customerId: Long,
    val customerName: String,
    val customerEmail: String,

    // OrderItem情報
    val orderItemId: Long,
    val productName: String,
    val quantity: Int,
    val unitPrice: BigDecimal
) {
    /**
     * CSVヘッダー行を生成する
     */
    companion object {
        fun csvHeader(): String {
            return "order_id,order_date,total_amount,customer_id,customer_name,customer_email," +
                "order_item_id,product_name,quantity,unit_price"
        }
    }

    /**
     * CSV行として出力する
     */
    fun toCsvLine(): String {
        return "$orderId,$orderDate,$totalAmount,$customerId,$customerName,$customerEmail," +
            "$orderItemId,$productName,$quantity,$unitPrice"
    }
}
