package com.example.ec.domain.shared

/**
 * 型安全なエンティティIDを表すバリューオブジェクト
 *
 * NOTE: 将来的な設計検討事項
 * - 現在は 0L を未永続化として許容しているが、sealed interface で
 *   永続化前(New)と永続化後(Persisted)を型レベルで分離する設計も検討する
 * - Issue #2 のスコープでは読み取り専用APIのため、シンプルな設計を優先
 *
 * @param T エンティティの型
 * @property value ID値（0L = 未永続化、1以上 = 永続化済み）
 */
@JvmInline
value class ID<T>(val value: Long) {
    /**
     * IDが永続化済みかどうかを判定
     */
    fun isPersisted(): Boolean = value > 0

    companion object {
        /**
         * 未永続化のIDを作成
         */
        fun <T> notPersisted(): ID<T> = ID(0L)
    }
}
