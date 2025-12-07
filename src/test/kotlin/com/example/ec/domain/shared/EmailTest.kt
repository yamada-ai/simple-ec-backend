package com.example.ec.domain.shared

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class EmailTest : FunSpec({

    context("Email作成時のバリデーション") {
        test("正常なEmailが作成できる") {
            val email = Email("user@example.com")

            email.value shouldBe "user@example.com"
        }

        test("様々な形式のメールアドレスが作成できる") {
            Email("test.user@example.com").value shouldBe "test.user@example.com"
            Email("user+tag@example.co.jp").value shouldBe "user+tag@example.co.jp"
            Email("user_name@sub.example.com").value shouldBe "user_name@sub.example.com"
        }

        test("空文字の場合は例外が発生する") {
            val exception = shouldThrow<IllegalArgumentException> {
                Email("")
            }
            exception.message shouldContain "Email must not be blank"
        }

        test("空白のみの場合は例外が発生する") {
            val exception = shouldThrow<IllegalArgumentException> {
                Email("   ")
            }
            exception.message shouldContain "Email must not be blank"
        }

        test("@がない場合は例外が発生する") {
            val exception = shouldThrow<IllegalArgumentException> {
                Email("invalid-email")
            }
            exception.message shouldContain "Email format is invalid"
        }

        test("ドメインがない場合は例外が発生する") {
            val exception = shouldThrow<IllegalArgumentException> {
                Email("user@")
            }
            exception.message shouldContain "Email format is invalid"
        }

        test("ローカル部がない場合は例外が発生する") {
            val exception = shouldThrow<IllegalArgumentException> {
                Email("@example.com")
            }
            exception.message shouldContain "Email format is invalid"
        }

        test("TLDがない場合は例外が発生する") {
            val exception = shouldThrow<IllegalArgumentException> {
                Email("user@example")
            }
            exception.message shouldContain "Email format is invalid"
        }
    }

    context("data class としての機能") {
        test("同じ値を持つEmailは等価である") {
            val email1 = Email("user@example.com")
            val email2 = Email("user@example.com")

            email1 shouldBe email2
        }

        test("異なる値を持つEmailは等価でない") {
            val email1 = Email("user1@example.com")
            val email2 = Email("user2@example.com")

            (email1 == email2) shouldBe false
        }
    }

    context("ファクトリメソッド") {
        test("of() で作成できる") {
            val email = Email.of("test@example.com")

            email shouldBe Email("test@example.com")
        }
    }
})
