package com.example.ec.domain.shared

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class PageTest : FunSpec({

    context("Page作成時のバリデーション") {
        test("正常なPageが作成できる") {
            val page = Page(
                content = listOf("A", "B", "C"),
                page = 0,
                size = 3,
                totalElements = 10L
            )

            page shouldBe Page(
                content = listOf("A", "B", "C"),
                page = 0,
                size = 3,
                totalElements = 10L
            )
        }

        test("pageが負の場合は例外が発生する") {
            val exception = shouldThrow<IllegalArgumentException> {
                Page(
                    content = emptyList<String>(),
                    page = -1,
                    size = 10,
                    totalElements = 0L
                )
            }
            exception.message shouldContain "Page number must be non-negative"
        }

        test("sizeが0の場合は例外が発生する") {
            val exception = shouldThrow<IllegalArgumentException> {
                Page(
                    content = emptyList<String>(),
                    page = 0,
                    size = 0,
                    totalElements = 0L
                )
            }
            exception.message shouldContain "Page size must be greater than 0"
        }

        test("sizeが負の場合は例外が発生する") {
            val exception = shouldThrow<IllegalArgumentException> {
                Page(
                    content = emptyList<String>(),
                    page = 0,
                    size = -10,
                    totalElements = 0L
                )
            }
            exception.message shouldContain "Page size must be greater than 0"
        }

        test("totalElementsが負の場合は例外が発生する") {
            val exception = shouldThrow<IllegalArgumentException> {
                Page(
                    content = emptyList<String>(),
                    page = 0,
                    size = 10,
                    totalElements = -1L
                )
            }
            exception.message shouldContain "Total elements must be non-negative"
        }
    }

    context("totalPages計算") {
        test("要素数がページサイズで割り切れる場合") {
            val page = Page(
                content = emptyList<String>(),
                page = 0,
                size = 10,
                totalElements = 100L
            )

            page.totalPages shouldBe 10
        }

        test("要素数がページサイズで割り切れない場合は切り上げ") {
            val page = Page(
                content = emptyList<String>(),
                page = 0,
                size = 10,
                totalElements = 95L
            )

            page.totalPages shouldBe 10
        }

        test("要素数が0の場合") {
            val page = Page(
                content = emptyList<String>(),
                page = 0,
                size = 10,
                totalElements = 0L
            )

            page.totalPages shouldBe 0
        }

        test("要素数がページサイズより少ない場合は1ページ") {
            val page = Page(
                content = listOf("A", "B"),
                page = 0,
                size = 10,
                totalElements = 2L
            )

            page.totalPages shouldBe 1
        }
    }

    context("isFirst判定") {
        test("page=0の場合はtrueを返す") {
            val page = Page(
                content = listOf("A"),
                page = 0,
                size = 10,
                totalElements = 100L
            )

            page.isFirst shouldBe true
        }

        test("page>0の場合はfalseを返す") {
            val page = Page(
                content = listOf("A"),
                page = 1,
                size = 10,
                totalElements = 100L
            )

            page.isFirst shouldBe false
        }
    }

    context("isLast判定") {
        test("最後のページの場合はtrueを返す") {
            val page = Page(
                content = listOf("A"),
                page = 9,
                size = 10,
                totalElements = 100L
            )

            page.isLast shouldBe true
        }

        test("最後でないページの場合はfalseを返す") {
            val page = Page(
                content = listOf("A"),
                page = 8,
                size = 10,
                totalElements = 100L
            )

            page.isLast shouldBe false
        }

        test("要素が1ページ分のみで最初かつ最後のページ") {
            val page = Page(
                content = listOf("A", "B"),
                page = 0,
                size = 10,
                totalElements = 2L
            )

            page.isFirst shouldBe true
            page.isLast shouldBe true
        }
    }

    context("isEmpty判定") {
        test("contentが空の場合はtrueを返す") {
            val page = Page(
                content = emptyList<String>(),
                page = 0,
                size = 10,
                totalElements = 0L
            )

            page.isEmpty shouldBe true
        }

        test("contentが空でない場合はfalseを返す") {
            val page = Page(
                content = listOf("A"),
                page = 0,
                size = 10,
                totalElements = 1L
            )

            page.isEmpty shouldBe false
        }
    }

    context("empty() ファクトリメソッド") {
        test("デフォルトパラメータで空のPageが作成される") {
            val page = Page.empty<String>()

            page shouldBe Page(
                content = emptyList(),
                page = 0,
                size = 20,
                totalElements = 0L
            )
        }

        test("カスタムパラメータで空のPageが作成される") {
            val page = Page.empty<Int>(page = 2, size = 50)

            page shouldBe Page(
                content = emptyList(),
                page = 2,
                size = 50,
                totalElements = 0L
            )
        }
    }
})
