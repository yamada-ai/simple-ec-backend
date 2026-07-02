package com.example.ec.application.export

import java.io.OutputStream

/**
 * ベンチマーク用の Null OutputStream。
 * 書き込みデータを全て破棄し、I/O コストを除外する。
 */
class NullOutputStream : OutputStream() {
    override fun write(b: Int) {
        // データを破棄する。
    }

    override fun write(b: ByteArray) {
        // データを破棄する。
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        // データを破棄する。
    }
}
