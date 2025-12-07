package com.example.ec.domain.shared

/**
 * ページング結果を表すバリューオブジェクト
 *
 * @param T ページングされる要素の型
 * @property content ページ内容（要素のリスト）
 * @property page 現在のページ番号（0-indexed）
 * @property size ページサイズ（1ページあたりの要素数）
 * @property totalElements 全体の要素数
 */
data class Page<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long
) {
    init {
        require(page >= 0) { "Page number must be non-negative" }
        require(size > 0) { "Page size must be greater than 0" }
        require(totalElements >= 0) { "Total elements must be non-negative" }
    }

    /**
     * 総ページ数を計算する
     */
    val totalPages: Int
        get() = if (size == 0) 0 else ((totalElements + size - 1) / size).toInt()

    /**
     * 現在のページが最初のページかどうか
     */
    val isFirst: Boolean
        get() = page == 0

    /**
     * 現在のページが最後のページかどうか
     */
    val isLast: Boolean
        get() = page >= totalPages - 1

    /**
     * 空のページかどうか
     */
    val isEmpty: Boolean
        get() = content.isEmpty()

    companion object {
        /**
         * 空のページを作成する
         */
        fun <T> empty(page: Int = 0, size: Int = 20): Page<T> =
            Page(
                content = emptyList(),
                page = page,
                size = size,
                totalElements = 0
            )
    }
}
