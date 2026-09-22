package com.lioisme.modev.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BytesView 契约(2026-08-18 V15):
 * 截断不再烧入 `…(+N bytes)` 硬编码英文后缀(hex 恒纯前缀,本地化由 UI 层承担);
 * 硬保险丝 [BytesView.HARD_MAX_HEX_CHARS]/[BytesView.HARD_MAX_TEXT_CHARS] 兜底。
 */
class BytesViewTest {

    @Test
    fun of_null_and_empty() {
        assertNull(BytesView.of(null))
        assertNull(BytesView.of(byteArrayOf()))
    }

    @Test
    fun of_plain_ascii() {
        val view = BytesView.of("hello".toByteArray(Charsets.UTF_8))
        assertEquals("68656c6c6f", view!!.hex)
        assertEquals("aGVsbG8=", view.base64)
        assertEquals("hello", view.text)
        assertEquals(5, view.totalLength)
        assertFalse(view.truncated)
    }

    @Test
    fun of_utf8_text() {
        val bytes = "加密测试".toByteArray(Charsets.UTF_8)
        val view = BytesView.of(bytes)
        assertEquals("加密测试", view!!.text)
        assertEquals(bytes.size, view.totalLength)
    }

    @Test
    fun of_binary_no_text() {
        val view = BytesView.of(byteArrayOf(0x00, 0x01, 0x02, 0xFF.toByte()))
        assertEquals("000102ff", view!!.hex)
        assertEquals("AAEC/w==", view.base64)
        assertNull(view.text)
    }

    @Test
    fun of_base64_round_trips_original_bytes() {
        val bytes = byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x7F, 0x80.toByte(), 0xFF.toByte(), 0x41, 0x42)
        val view = BytesView.of(bytes)
        assertTrue(java.util.Arrays.equals(bytes, Base64Util.decode(view!!.base64!!)))
    }

    @Test
    fun of_b64_truncation_bounds_to_byte_budget() {
        val bytes = ByteArray(200) { 0x41 } // 200 字节 → base64 268 字符 > 40 上限
        val view = BytesView.of(bytes, maxHexChars = 64, maxB64Chars = 40)
        assertEquals("41".repeat(32), view!!.hex)
        // 40 字符上限 → 30 字节前缀(40/4*3),编码恰好 40 字符(≤ 上限)
        assertEquals(40, view.base64!!.length)
        assertEquals("QUFB".repeat(10), view.base64)
        assertTrue(view.truncated)
    }

    @Test
    fun of_b64_no_truncation_when_within_cap() {
        val view = BytesView.of("abc".toByteArray(), maxB64Chars = 32)
        assertEquals("YWJj", view!!.base64)
        assertFalse(view.truncated)
    }

    @Test
    fun of_hard_b64_cap_bounds_unlimited_request() {
        // V44 T2.3 保元弃内容:输入超 CONTENT_MATERIALIZE_MAX_BYTES(8MB)时,
        // 无论调用方上限多大(含 Int.MAX_VALUE),base64 一律不物化(防 OOM)。
        val bytes = ByteArray(9 * 1024 * 1024) { 0x41 }
        val view = BytesView.of(bytes, maxHexChars = Int.MAX_VALUE, maxB64Chars = Int.MAX_VALUE)
        assertTrue(view!!.truncated)
        assertNull("超预算不得物化 base64(保元弃内容)", view.base64)
        // hex 仅保留 32 字节标识前缀(64 字符),足以辨识内容形态
        assertEquals(BytesView.METADATA_HEX_PREFIX_BYTES * 2, view.hex.length)
    }

    @Test
    fun of_truncation_keeps_pure_hex_prefix() {
        val bytes = ByteArray(200) { 0x41 } // 200 个 'A',hex 400 字符 > 64 上限
        val view = BytesView.of(bytes, maxHexChars = 64)
        assertTrue(view!!.truncated)
        // V15:hex 为纯前缀,不再含硬编码英文后缀(本地化由 UI 层承载)
        assertEquals("41".repeat(32), view.hex)
        assertEquals(200, view.totalLength)
    }

    @Test
    fun of_no_truncation_when_small() {
        val view = BytesView.of("abc".toByteArray(), maxHexChars = 64)
        assertEquals("616263", view!!.hex)
        assertFalse(view.truncated)
    }

    @Test
    fun of_truncated_binary_no_text() {
        val bytes = ByteArray(1000) { 0x00 }
        val view = BytesView.of(bytes, maxHexChars = 32)
        assertTrue(view!!.truncated)
        assertNull(view.text)
    }

    @Test
    fun of_hard_hex_cap_bounds_unlimited_request() {
        // V44 T2.3 保元弃内容:输入超 CONTENT_MATERIALIZE_MAX_BYTES(8MB)时,
        // 无论调用方上限多大(含 Int.MAX_VALUE),hex 仅保留 32 字节标识前缀(防 OOM),
        // 不再物化到 16M 硬顶(旧契约)。
        val bytes = ByteArray(9 * 1024 * 1024) { 0x41 } // 9MB > 8MB 预算
        val view = BytesView.of(bytes, maxHexChars = Int.MAX_VALUE)
        assertTrue(view!!.truncated)
        assertEquals(BytesView.METADATA_HEX_PREFIX_BYTES * 2, view.hex.length)
        assertNull(view.base64)
        assertNull(view.text)
    }

    @Test
    fun of_hard_text_cap_bounds_unlimited_request() {
        val bytes = ByteArray(BytesView.HARD_MAX_TEXT_CHARS + 1) { 0x41 }
        val view = BytesView.of(bytes, maxHexChars = Int.MAX_VALUE, maxTextChars = Int.MAX_VALUE)
        assertNull(view!!.text)
    }

    @Test
    fun isPrintable_rejects_control_chars() {
        assertFalse(TextUtil.isPrintable(byteArrayOf(0x01, 0x02)))
        assertTrue(TextUtil.isPrintable(byteArrayOf(0x0A, 0x20, 0x7E)))
    }

    @Test
    fun isPrintable_rejects_invalid_utf8() {
        assertFalse(TextUtil.isPrintable(byteArrayOf(0xC2.toByte())))
        assertFalse(TextUtil.isPrintable(byteArrayOf(0xE0.toByte(), 0x80.toByte(), 0xBF.toByte())))
        assertFalse(TextUtil.isPrintable(byteArrayOf(0xF4.toByte(), 0x90.toByte(), 0x80.toByte(), 0x80.toByte())))
    }
}