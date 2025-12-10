package com.example.ec.application.export

import com.example.ec.domain.customer.CustomerRepository
import com.example.ec.domain.order.OrderRepository
import com.example.ec.domain.shared.ID
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.stream.Stream

/**
 * 注文データをCSV形式でエクスポートするユースケース
 *
 * 複数のエクスポート戦略（Sequence/Stream/Spliterator）を切り替え可能
 */
@Service
class ExportOrdersUseCase(
    private val orderRepository: OrderRepository,
    private val customerRepository: CustomerRepository,
    private val sequenceStrategy: SequenceExportStrategy,
    private val streamFlatMapStrategy: StreamFlatMapExportStrategy,
    private val streamMapMultiStrategy: StreamMapMultiExportStrategy,
    private val spliteratorStrategy: SpliteratorExportStrategy
) {
    /**
     * 注文データをCSV行としてエクスポートする
     *
     * @param from 注文日の開始日時（inclusive）
     * @param to 注文日の終了日時（inclusive）
     * @param strategyName エクスポート戦略名
     * @return CSV行のストリーム
     */
    fun execute(
        from: LocalDateTime?,
        to: LocalDateTime?,
        strategyName: String
    ): Stream<OrderCsvRow> {
        // 戦略を選択
        val strategy = selectStrategy(strategyName)

        // OrderRepositoryからOrderのストリームを取得
        val orders = orderRepository.streamOrdersForExport(from, to)

        // Customer情報を取得する関数
        // Note: ここでN+1が発生するが、ストリーミング処理なのでメモリは節約される
        // 実運用では、顧客情報をキャッシュする等の最適化が必要
        val getCustomerName: (Long) -> String = { customerId ->
            customerRepository.findById(ID(customerId))?.name ?: "Unknown"
        }

        val getCustomerEmail: (Long) -> String = { customerId ->
            customerRepository.findById(ID(customerId))?.email?.value ?: "unknown@example.com"
        }

        // 選択された戦略でエクスポート
        return strategy.export(orders, getCustomerName, getCustomerEmail)
    }

    private fun selectStrategy(strategyName: String): ExportStrategy {
        return when (strategyName) {
            "sequence" -> sequenceStrategy
            "stream-flatmap" -> streamFlatMapStrategy
            "stream-mapmulti" -> streamMapMultiStrategy
            "spliterator" -> spliteratorStrategy
            else -> sequenceStrategy // デフォルトはSequence
        }
    }
}
