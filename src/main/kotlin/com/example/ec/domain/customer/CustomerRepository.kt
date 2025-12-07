package com.example.ec.domain.customer

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
    fun findByEmail(email: String): Customer?

    /**
     * 全件数を取得する
     *
     * @return 顧客の総数
     */
    fun count(): Long
}
