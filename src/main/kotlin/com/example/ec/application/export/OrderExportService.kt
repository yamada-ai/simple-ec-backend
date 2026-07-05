package com.example.ec.application.export

import com.example.ec.domain.order.OrderRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.PrintWriter
import java.time.LocalDateTime
import java.util.stream.Stream

/**
 * 注文データをCSV形式でエクスポートするサービス
 *
 * Phase 1: SQL JOIN で Order + OrderItem を取得し、Stream で展開
 * （単純なケースでは戦略比較不要）
 */
@Service
class OrderExportService(
    private val orderRepository: OrderRepository
) {
    /**
     * 注文データをCSV行としてエクスポートする
     *
     * @param from 注文日の開始日時（inclusive）
     * @param to 注文日の終了日時（inclusive）
     * @return CSV行のストリーム
     */
    private fun exportOrders(
        from: LocalDateTime?,
        to: LocalDateTime?
    ): Stream<OrderCsvRow> {
        // OrderRepositoryからフラットなCSV行データを1クエリで取得（N+1解消済み）
        return orderRepository.streamOrdersForExport(from, to)
            .map { row ->
                OrderCsvRow(
                    orderId = row.orderId,
                    customerId = row.customerId,
                    customerName = row.customerName,
                    customerEmail = row.customerEmail,
                    orderDate = row.orderDate,
                    productName = row.productName,
                    quantity = row.quantity,
                    unitPrice = row.unitPrice
                )
            }
    }

    /**
     * CSVへの書き込みまでトランザクション内で完結させる。
     *
     * PostgreSQL JDBC の cursor は transaction 境界内で stream を消費する必要があるため、
     * Streamを返して呼び出し元で消費する形にはしない。
     */
    @Transactional(readOnly = true)
    fun writeCsv(
        from: LocalDateTime?,
        to: LocalDateTime?,
        writer: PrintWriter
    ): Int {
        writer.println(OrderCsvRow.CSV_HEADER)

        var rowCount = 0
        exportOrders(from, to).use { stream ->
            stream.forEach { row ->
                writer.println(row.toCsvLine())
                rowCount++
            }
        }

        return rowCount
    }
}
