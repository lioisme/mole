package com.lioisme.modev.ui.logs

import com.lioisme.modev.core.model.CryptoKind
import com.lioisme.modev.core.model.CryptoOperation
import com.lioisme.modev.core.model.CryptoRecord
import com.lioisme.modev.core.util.BytesView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 文本护栏与导出预检纯函数契约(V16 R2 空/大数据健壮性):
 * formatParamsText:单键值 256 字符截断(「…」标记)、200 条上限、计数可渲染省略标记;
 * estimateJsonChars:×2 保守倍率、各字节区块/字符串字段计入、超大记录必然超限。
 */
class RecordDetailTextFormatTest {

    @Test
    fun params_passesThroughShortValues() {
        val text = formatParamsText(linkedMapOf("iterations" to "1000", "keysize" to "256"))
        assertEquals(listOf("iterations = 1000", "keysize = 256"), text.lines)
        assertEquals(0, text.omittedEntries)
        assertEquals(0, text.truncatedValues)
    }

    @Test
    fun params_truncatesLongValuesAt256WithEllipsis() {
        val longValue = "a".repeat(300)
        val text = formatParamsText(mapOf("k" to longValue))
        assertEquals(1, text.lines.size)
        val line = text.lines.single()
        assertEquals(PARAMS_MAX_VALUE_CHARS + 1, line.length - "k = ".length)
        assertTrue(line.endsWith("…"))
        assertTrue(line.startsWith("k = " + "a".repeat(PARAMS_MAX_VALUE_CHARS)))
        assertEquals(1, text.truncatedValues)
        assertEquals(0, text.omittedEntries)
    }

    @Test
    fun params_capsEntriesAt200_andCountsOmitted() {
        val params = (1..250).associate { "k$it" to "v$it" }
        val text = formatParamsText(params)
        assertEquals(PARAMS_MAX_ENTRIES, text.lines.size)
        assertEquals(50, text.omittedEntries)
        assertEquals(0, text.truncatedValues)
        // 保留条目按插入序(近因即首个声明的键,不重排)
        assertEquals("k1 = v1", text.lines.first())
    }

    @Test
    fun estimateJsonChars_emptyRecordHasBaseOverhead() {
        val record = recordWith()
        assertTrue(estimateJsonChars(record) >= 2 * (record.packageName.length + 128))
    }

    @Test
    fun estimateJsonChars_scalesWithByteSections() {
        val hex = "ab".repeat(1_000_000) // 2M 字符 hex(1MB 数据)
        val record = recordWith(iv = BytesView(hex = hex, text = null, totalLength = 1_000_000, truncated = false))
        val estimate = estimateJsonChars(record)
        // hex 全量计入且 ×2:估算必然远超数据本身
        assertTrue(estimate > hex.length)
    }

    @Test
    fun estimateJsonChars_hugeRecordExceedsExportLimit() {
        val hex = "ab".repeat(10_000_000) // 10M 字符 → ×2 后 20M+,含其他字段后超 32M
        val big = recordWith(
            input = BytesView(hex = hex, text = null, totalLength = 10_000_000, truncated = false),
            output = BytesView(hex = hex, text = null, totalLength = 10_000_000, truncated = false),
        )
        assertTrue(estimateJsonChars(big) > EXPORT_MAX_CHARS)
    }

    private fun recordWith(
        iv: BytesView? = null,
        input: BytesView? = null,
        output: BytesView? = null,
    ): CryptoRecord = CryptoRecord(
        packageName = "com.example.app",
        kind = CryptoKind.CIPHER,
        operation = CryptoOperation.ENCRYPT,
        algorithm = "AES/GCM/NoPadding",
        provider = null,
        direction = 1,
        iv = iv,
        key = null,
        keyAlias = null,
        input = input,
        output = output,
        timestamp = 1_700_000_000_000,
        threadName = "main",
    )
}