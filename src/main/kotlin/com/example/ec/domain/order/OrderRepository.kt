package com.example.ec.domain.order

import com.example.ec.application.order.OrderListItemView
import com.example.ec.domain.shared.ID
import com.example.ec.domain.shared.Page
import java.time.LocalDateTime
import java.util.stream.Stream

/**
 * 注文リポジトリインターフェース
 */
@Suppress("TooManyFunctions") // 戦略ごとの取得手段を提供するため、関数数が多い
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

    /**
     * 注文明細の全件数を取得する
     *
     * @return 注文明細の総数
     */
    fun countOrderItems(): Long

    /**
     * 全注文と注文明細を削除する（開発/テスト用）
     */
    fun truncate()

    /**
     * 複数の注文を一括保存する
     *
     * @param orders 保存する注文のリスト（明細を含む）
     * @return 保存された注文のリスト（IDが割り当てられている）
     */
    fun saveAll(orders: List<Order>): List<Order>

    /**
     * CSV出力用に注文＋顧客＋注文明細を1クエリでストリーミング取得する
     */
    fun streamOrdersForExport(
        from: LocalDateTime?,
        to: LocalDateTime?
    ): Stream<OrderExportRow>

    /**
     * 属性値を含む注文のストリームを取得する（CSV動的列向け）
     */
    fun streamOrdersWithAttributes(
        from: LocalDateTime?,
        to: LocalDateTime?
    ): Stream<OrderAttributeJoinedRow>

    /**
     * preload 戦略用: 属性値を別取得する前提で、注文＋顧客のみをストリーミング取得
     */
    fun streamOrdersBase(
        from: LocalDateTime?,
        to: LocalDateTime?
    ): Stream<OrderBaseRow>

    /**
        preload 用: 属性値を一括ロードしてメモリ Map で join するための取得
     */
    fun loadAttributeValueMap(
        from: LocalDateTime?,
        to: LocalDateTime?
    ): Map<Long, Map<Long, String>>

    /**
        multiset 用: jOOQ multiset で注文ごとに属性リストをネストして取得
     */
    fun fetchOrdersWithAttributesMultiset(
        from: LocalDateTime?,
        to: LocalDateTime?
    ): Stream<OrderWithAttributes>
}
