package com.example.ec.infrastructure.io

import java.io.OutputStream

/**
 * ベンチマーク用の Null OutputStream。
 * 書き込みデータを全て破棄する（I/O コストを除外するため）。
 */
class NullOutputStream : OutputStream() {
    override fun write(b: Int) {
        // 何もしない（データを破棄）
    }

    override fun write(b: ByteArray) {
        // 何もしない（データを破棄）
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        // 何もしない（データを破棄）
    }
}
