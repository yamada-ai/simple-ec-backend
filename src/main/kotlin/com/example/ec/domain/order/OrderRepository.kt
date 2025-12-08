package com.example.ec.domain.order

import com.example.ec.application.order.OrderListItemView
import com.example.ec.domain.shared.ID
import com.example.ec.domain.shared.Page
import java.time.LocalDateTime

/**
 * 注文リポジトリインターフェース
 */
interface OrderRepository {
    /**
     * 注文IDで注文を取得する
     *
     * @param id 注文ID
     * @return 注文エンティティ（明細を含む）、存在しない場合はnull
     */
    fun findById(id: ID<Order>): Order?

    /**
     * 注文を検索する（ページング付き）
     *
     * @param from 注文日の開始日時（inclusive、nullの場合は制限なし）
     * @param to 注文日の終了日時（inclusive、nullの場合は制限なし）
     * @param customerName 顧客名での部分一致検索（nullの場合は検索しない）
     * @param page ページ番号（0-indexed）
     * @param size ページサイズ
     * @return ページング結果
     */
    fun search(
        from: LocalDateTime?,
        to: LocalDateTime?,
        customerName: String?,
        page: Int,
        size: Int
    ): Page<Order>

    /**
     * 注文一覧を検索する（顧客名を含む、ページング付き）
     *
     * N+1問題を避けるため、顧客情報を1つのクエリで取得する
     *
     * @param from 注文日の開始日時（inclusive、nullの場合は制限なし）
     * @param to 注文日の終了日時（inclusive、nullの場合は制限なし）
     * @param customerName 顧客名での部分一致検索（nullの場合は検索しない）
     * @param page ページ番号（0-indexed）
     * @param size ページサイズ
     * @return ページング結果（注文一覧表示用のビューデータ）
     */
    fun searchForList(
        from: LocalDateTime?,
        to: LocalDateTime?,
        customerName: String?,
        page: Int,
        size: Int
    ): Page<OrderListItemView>

    /**
     * 全件数を取得する
     *
     * @return 注文の総数
     */
    fun count(): Long
}
