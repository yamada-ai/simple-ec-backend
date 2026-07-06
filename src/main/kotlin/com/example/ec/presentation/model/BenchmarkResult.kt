package com.example.ec.presentation.model

/**
 * ベンチマーク測定結果。
 *
 * @property strategy 戦略名
 * @property mode 測定モード
 * @property elapsedMs 処理時間（ミリ秒）
 * @property md5 出力データのMD5ハッシュ（検証用）
 * @property rowCount rowsモードで消費したCSV行モデル数
 * @property attributeValueCount rowsモードで消費した属性値数
 * @property orderIdChecksum rowsモードで消費を確認するための軽量checksum
 */
data class BenchmarkResult(
    val strategy: String,
    val mode: String,
    val elapsedMs: Long,
    val md5: String?,
    val rowCount: Long?,
    val attributeValueCount: Long?,
    val orderIdChecksum: Long?
)
