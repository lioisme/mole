package com.lioisme.modev.core.util

/**
 * 惰性分块字符串视图(V30 行边界对齐):
 * 以 O(N) 预计算行边界 + O(1) 按 index 取块,取代 `String.chunked` 的全量
 * 列表物化——详情页展开态仅组合可视块,主线程零大分配、零重复物化。
 *
 * 行边界对齐:切割点 snap 到最近的换行符,保证每个 chunk 以完整行结尾
 * (除最后一个 chunk 外),消除 chunk 边界处的渲染不一致。
 *
 * 与 LazyColumn `items(count = chunks.size)` 配套使用:块内容按需取,
 * 单块 ≤ [DEFAULT_CHUNK_CHARS] 字符(行边界允许略超),布局/绘制毫秒级。
 */
class ChunkedText(
    private val text: String,
    chunkSize: Int = DEFAULT_CHUNK_CHARS,
) {
    init {
        require(chunkSize > 0) { "chunkSize must be positive" }
    }

    /** 预计算的 chunk 起始偏移列表(含 text.length 哨兵)。 */
    private val offsets: IntArray

    /** 块数(空串 = 0,与 LazyColumn items(count=0) 兼容)。 */
    val size: Int get() = offsets.size - 1

    init {
        if (text.isEmpty()) {
            offsets = intArrayOf(0)
        } else {
            val starts = mutableListOf(0)
            var pos = 0
            while (pos < text.length) {
                val rawEnd = minOf(pos + chunkSize, text.length)
                if (rawEnd >= text.length) {
                    starts.add(text.length)
                    break
                }
                // snap end 到最近的换行符(避免将一行拆成两个 chunk);
                // 若 rawEnd 附近无换行则保持原切割点(纯 hex/base64 无换行场景)。
                val nl = text.indexOf('\n', rawEnd)
                val end = if (nl != -1 && nl - rawEnd < chunkSize) nl + 1 else rawEnd
                starts.add(end)
                pos = end
            }
            offsets = starts.toIntArray()
        }
    }

    /** 第 [index] 块内容(惰性 substring;越界返回空串,防御性兜底)。 */
    operator fun get(index: Int): String {
        if (index < 0 || index >= size) return ""
        val start = offsets[index]
        val end = offsets[index + 1]
        return text.substring(start, end)
    }

    companion object {
        /** 单块字符数上限(4K ≈ 96 行等宽 hex,单块布局/绘制毫秒级)。 */
        const val DEFAULT_CHUNK_CHARS = 4096
    }
}
