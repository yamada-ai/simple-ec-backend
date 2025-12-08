package com.example.ec.application.order

import com.example.ec.domain.order.OrderRepository
import com.example.ec.domain.shared.Page
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * 注文一覧取得ユースケース
 */
@Service
class GetOrdersUseCase(
    private val orderRepository: OrderRepository
) {
    /**
     * 注文一覧を検索する
     *
     * @param from 注文日の開始日時
     * @param to 注文日の終了日時
     * @param customerName 顧客名（部分一致）
     * @param page ページ番号（0-indexed）
     * @param size ページサイズ
     * @return ページング結果
     */
    fun execute(
        from: LocalDateTime?,
        to: LocalDateTime?,
        customerName: String?,
        page: Int,
        size: Int
    ): Page<OrderListItemView> {
        return orderRepository.searchForList(from, to, customerName, page, size)
    }
}
