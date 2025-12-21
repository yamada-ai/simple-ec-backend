package com.example.ec.presentation.model

/**
 * ベンチマーク測定結果。
 *
 * @property strategy 戦略名
 * @property elapsedMs 処理時間（ミリ秒）
 * @property md5 出力データのMD5ハッシュ（検証用）
 */
data class BenchmarkResult(
    val strategy: String,
    val elapsedMs: Long,
    val md5: String
)
