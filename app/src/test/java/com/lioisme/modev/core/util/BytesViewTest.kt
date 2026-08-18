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
        assertNull(view.text)
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
        // 硬保险丝:调用方传无限上限也不得超限物化(防 OOM)
        val bytes = ByteArray(9 * 1024 * 1024) { 0x41 } // hex 18M 字符 > 16M 硬顶
        val view = BytesView.of(bytes, maxHexChars = Int.MAX_VALUE)
        assertTrue(view!!.truncated)
        assertEquals(BytesView.HARD_MAX_HEX_CHARS, view.hex.length)
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