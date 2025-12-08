package com.example.ec.domain.customer

import com.example.ec.domain.shared.Email
import com.example.ec.domain.shared.ID

/**
 * 顧客リポジトリインターフェース
 */
interface CustomerRepository {
    /**
     * 顧客IDで顧客を取得する
     *
     * @param id 顧客ID
     * @return 顧客エンティティ、存在しない場合はnull
     */
    fun findById(id: ID<Customer>): Customer?

    /**
     * メールアドレスで顧客を取得する
     *
     * @param email メールアドレス
     * @return 顧客エンティティ、存在しない場合はnull
     */
    fun findByEmail(email: Email): Customer?

    /**
     * 全件数を取得する
     *
     * @return 顧客の総数
     */
    fun count(): Long

    /**
     * 全顧客を削除する（開発/テスト用）
     */
    fun truncate()

    /**
     * 複数の顧客を一括保存する
     *
     * @param customers 保存する顧客のリスト
     * @return 保存された顧客のリスト（IDが割り当てられている）
     */
    fun saveAll(customers: List<Customer>): List<Customer>
}
