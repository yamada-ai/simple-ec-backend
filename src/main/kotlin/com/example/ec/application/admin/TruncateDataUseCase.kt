package com.example.ec.application.admin

import com.example.ec.domain.attribute.OrderAttributeDefinitionRepository
import com.example.ec.domain.customer.CustomerRepository
import com.example.ec.domain.order.OrderRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 全データ削除ユースケース（開発/テスト用）
 */
@Service
class TruncateDataUseCase(
    private val customerRepository: CustomerRepository,
    private val orderRepository: OrderRepository,
    private val attributeDefinitionRepository: OrderAttributeDefinitionRepository
) {
    /**
     * 全データを削除する
     *
     * 注意: この操作は元に戻せません
     */
    @Transactional
    fun execute(): TruncateResult {
        // 外部キー制約があるため、子テーブルから順に削除
        // orderRepository.truncate() が order_attribute_value と order_item を削除
        orderRepository.truncate()
        customerRepository.truncate()
        // 属性値が削除された後に属性定義を削除
        attributeDefinitionRepository.truncate()

        return TruncateResult(
            deleted = true,
            message = "All data truncated successfully"
        )
    }
}

/**
 * 削除結果
 */
data class TruncateResult(
    val deleted: Boolean,
    val message: String
)
