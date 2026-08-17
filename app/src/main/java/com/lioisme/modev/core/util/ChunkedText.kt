package com.lioisme.modev.core.util

/**
 * 惰性分块字符串视图(2026-08-17 V11 新增):
 * 以 O(1) 计算块数、按 index 惰性 substring,取代 `String.chunked()` 的全量
 * 列表物化——详情页展开态仅组合可视块,主线程零大分配、零重复物化
 * (旧实现每次 LoadedContent 重组都会对所有展开区块重新 chunked,多 MB 主线程分配)。
 *
 * 与 LazyColumn `items(count = chunks.size)` 配套使用:块内容按需取,
 * 单块 ≤ [DEFAULT_CHUNK_CHARS] 字符,布局/绘制毫秒级。
 */
class ChunkedText(
    private val text: String,
    private val chunkSize: Int = DEFAULT_CHUNK_CHARS,
) {
    init {
        require(chunkSize > 0) { "chunkSize must be positive" }
    }

    /** 块数(空串 = 0,与 LazyColumn items(count=0) 兼容)。 */
    val size: Int
        get() = (text.length + chunkSize - 1) / chunkSize

    /** 第 [index] 块内容(惰性 substring;越界返回空串,防御性兜底)。 */
    operator fun get(index: Int): String {
        val start = index * chunkSize
        if (start >= text.length) return ""
        return text.substring(start, minOf(start + chunkSize, text.length))
    }

    companion object {
        /** 单块字符数上限(4K ≈ 96 行等宽 hex,单块布局/绘制毫秒级)。 */
        const val DEFAULT_CHUNK_CHARS = 4096
    }
}
