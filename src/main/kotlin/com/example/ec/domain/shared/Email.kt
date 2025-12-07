package com.example.ec.domain.shared

/**
 * メールアドレスを表す値オブジェクト
 *
 * @property value メールアドレス
 */
@JvmInline
value class Email(val value: String) {
    init {
        require(value.isNotBlank()) { "Email must not be blank" }
        require(isValidFormat(value)) { "Email format is invalid: $value" }
    }

    companion object {
        // シンプルなメールアドレス形式の検証
        // RFC 5322 完全準拠ではないが、実用上十分な検証
        private val EMAIL_REGEX = Regex(
            "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}\$"
        )

        private fun isValidFormat(email: String): Boolean =
            EMAIL_REGEX.matches(email)

        /**
         * 文字列から Email を作成する
         */
        fun of(value: String): Email = Email(value)
    }
}
