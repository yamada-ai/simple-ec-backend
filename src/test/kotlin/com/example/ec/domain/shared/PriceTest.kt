package com.example.ec.domain.shared

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.math.BigDecimal

class PriceTest : FunSpec({

    context("Price作成時のバリデーション") {
        test("正常なPriceが作成できる") {
            val price = Price(BigDecimal("1000.00"))

            price.value shouldBe BigDecimal("1000.00")
        }

        test("0円のPriceが作成できる") {
            val price = Price(BigDecimal.ZERO)

            price shouldBe Price.ZERO
        }

        test("負の値の場合は例外が発生する") {
            val exception = shouldThrow<IllegalArgumentException> {
                Price(BigDecimal("-100"))
            }
            exception.message shouldContain "Price must be non-negative"
        }

        test("小数点以下3桁以上の場合は例外が発生する") {
            val exception = shouldThrow<IllegalArgumentException> {
                Price(BigDecimal("100.123"))
            }
            exception.message shouldContain "Price scale must be at most 2"
        }
    }

    context("Priceの演算") {
        test("加算が正しく計算される") {
            val price1 = Price.of("1000.50")
            val price2 = Price.of("500.25")

            val result = price1 + price2

            result shouldBe Price.of("1500.75")
        }

        test("減算が正しく計算される") {
            val price1 = Price.of("1000.00")
            val price2 = Price.of("300.00")

            val result = price1 - price2

            result shouldBe Price.of("700.00")
        }

        test("乗算（数量）が正しく計算される") {
            val price = Price.of("1500.50")

            val result = price * 3

            result shouldBe Price.of("4501.50")
        }

        test("0円の加算") {
            val price = Price.of("1000.00")

            val result = price + Price.ZERO

            result shouldBe price
        }
    }

    context("Priceの比較") {
        test("同じ金額のPriceは等価") {
            val price1 = Price.of("1000.00")
            val price2 = Price.of("1000.00")

            price1 shouldBe price2
        }

        test("compareTo で比較できる") {
            val price1 = Price.of("1000.00")
            val price2 = Price.of("2000.00")

            (price1 < price2) shouldBe true
            (price2 > price1) shouldBe true
        }
    }

    context("ファクトリメソッド") {
        test("of(String) で作成できる") {
            val price = Price.of("1234.56")

            price shouldBe Price(BigDecimal("1234.56"))
        }

        test("of(Long) で作成できる") {
            val price = Price.of(1000L)

            price shouldBe Price(BigDecimal("1000"))
        }

        test("of(Double) で作成できる") {
            val price = Price.of(999.99)

            price shouldBe Price(BigDecimal.valueOf(999.99))
        }
    }
})
