package com.example.ec.application.export

import com.example.ec.domain.order.Order
import java.util.stream.Stream

/**
 * CSV出力戦略のインターフェース
 *
 * Order（親）とOrderItem（子）の1→多展開を、異なるアプローチで実装する
 */
interface ExportStrategy {
    /**
     * 注文データをCSV行のストリームに変換する
     *
     * @param orders 注文のストリーム
     * @param getCustomerName 顧客IDから顧客名を取得する関数
     * @param getCustomerEmail 顧客IDから顧客メールを取得する関数
     * @return CSV行のストリーム
     */
    fun export(
        orders: Stream<Order>,
        getCustomerName: (Long) -> String,
        getCustomerEmail: (Long) -> String
    ): Stream<OrderCsvRow>
}
