package com.lioisme.modev.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

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
    fun of_truncation() {
        val bytes = ByteArray(200) { 0x41 } // 200 个 'A',hex 400 字符 > 64 上限
        val view = BytesView.of(bytes, maxHexChars = 64)
        assertTrue(view!!.truncated)
        assertTrue(view.hex.endsWith("…(+168 bytes)"))
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