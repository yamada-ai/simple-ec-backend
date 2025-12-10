package com.example.ec.domain.attribute

import com.example.ec.domain.shared.ID

/**
 * 注文属性定義リポジトリインターフェース
 */
interface OrderAttributeDefinitionRepository {
    /**
     * 全ての属性定義を取得する
     *
     * @return 属性定義のリスト（ID昇順）
     */
    fun findAll(): List<OrderAttributeDefinition>

    /**
     * IDで属性定義を取得する
     *
     * @param id 属性定義ID
     * @return 属性定義、存在しない場合はnull
     */
    fun findById(id: ID<OrderAttributeDefinition>): OrderAttributeDefinition?

    /**
     * 内部名で属性定義を取得する
     *
     * @param name 内部名
     * @return 属性定義、存在しない場合はnull
     */
    fun findByName(name: String): OrderAttributeDefinition?

    /**
     * 属性定義を保存する（新規作成または更新）
     *
     * @param definition 保存する属性定義
     * @return 保存された属性定義
     */
    fun save(definition: OrderAttributeDefinition): OrderAttributeDefinition

    /**
     * 属性定義を削除する
     * 関連する属性値も削除される（ON DELETE CASCADE）
     *
     * @param id 削除する属性定義ID
     * @return 削除された件数（0または1）
     */
    fun deleteById(id: ID<OrderAttributeDefinition>): Int

    /**
     * 全件数を取得する
     *
     * @return 属性定義の総数
     */
    fun count(): Long
}
