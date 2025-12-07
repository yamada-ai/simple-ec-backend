package com.example.ec.domain.shared

import java.math.BigDecimal

/**
 * 金額を表す値オブジェクト
 *
 * @property value 金額（非負の値）
 */
@JvmInline
value class Price(val value: BigDecimal) : Comparable<Price> {
    init {
        require(value >= BigDecimal.ZERO) { "Price must be non-negative, but was: $value" }
        require(value.scale() <= 2) { "Price scale must be at most 2, but was: ${value.scale()}" }
    }

    /**
     * 金額を加算する
     */
    operator fun plus(other: Price): Price = Price(value.add(other.value))

    /**
     * 金額を減算する
     */
    operator fun minus(other: Price): Price = Price(value.subtract(other.value))

    /**
     * 数量を乗算する
     */
    operator fun times(quantity: Int): Price = Price(value.multiply(BigDecimal(quantity)))

    /**
     * BigDecimal を乗算する
     */
    operator fun times(multiplier: BigDecimal): Price = Price(value.multiply(multiplier))

    override fun compareTo(other: Price): Int = value.compareTo(other.value)

    companion object {
        /**
         * 0円
         */
        val ZERO = Price(BigDecimal.ZERO)

        /**
         * 文字列から Price を作成する
         */
        fun of(value: String): Price = Price(BigDecimal(value))

        /**
         * Long から Price を作成する
         */
        fun of(value: Long): Price = Price(BigDecimal(value))

        /**
         * Double から Price を作成する（テスト用）
         */
        fun of(value: Double): Price = Price(BigDecimal.valueOf(value))
    }
}
