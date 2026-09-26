package com.lioisme.modev.core.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Base64

/**
 * Base64Util 契约:RFC 4648 标准字母表 + 填充 + 无换行;
 * 与 JDK 基准(Base64.getEncoder/Decoder)逐字节对拍。
 */
class Base64UtilTest {

    @Test
    fun encode_matches_jdk_standard() {
        val samples = listOf(
            ByteArray(0),
            "a".toByteArray(),
            "ab".toByteArray(),
            "abc".toByteArray(),
            "hello world".toByteArray(),
            "加密测试".toByteArray(Charsets.UTF_8),
            byteArrayOf(0x00, 0x7F, 0x80.toByte(), 0xFF.toByte()),
        )
        for (bytes in samples) {
            assertEquals(Base64.getEncoder().encodeToString(bytes), Base64Util.encode(bytes))
        }
    }

    @Test
    fun encode_respects_offset_and_length() {
        val bytes = byteArrayOf(0x10, 0x41, 0x42, 0x43, 0x20) // "ABC" 位于 1..3
        assertEquals("QUJD", Base64Util.encode(bytes, 1, 3))
        assertEquals("", Base64Util.encode(bytes, 0, 0))
    }

    @Test
    fun decode_round_trips_standard_encoded() {
        val bytes = byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x7F, 0x80.toByte(), 0xFF.toByte(), 0x41)
        assertArrayEquals(bytes, Base64Util.decode(Base64Util.encode(bytes)))
    }

    @Test
    fun decode_accepts_unpadded_input() {
        // RFC 4648 §3.2:无填充输入可接受
        assertArrayEquals("abc".toByteArray(), Base64Util.decode("YWJj"))
        assertArrayEquals("a".toByteArray(), Base64Util.decode("YQ"))
        assertArrayEquals("ab".toByteArray(), Base64Util.decode("YWI"))
        assertArrayEquals(ByteArray(0), Base64Util.decode(""))
    }

    @Test
    fun decode_rejects_invalid_input() {
        assertNull(Base64Util.decode("A"))      // 长度 % 4 == 1
        assertNull(Base64Util.decode("A==="))   // 填充 > 2
        assertNull(Base64Util.decode("=AAA"))   // 填充位置非法
        assertNull(Base64Util.decode("AAAA="))  // 填充后仍有字符
        assertNull(Base64Util.decode("YQ=A"))   // 填充位错误
        assertNull(Base64Util.decode("YQ!="))   // 非法字符
        assertNull(Base64Util.decode("YWJj\n")) // 换行不允许
    }

    @Test
    fun decode_matches_jdk_for_random_round_trip() {
        val rng = java.util.Random(42)
        for (size in listOf(1, 2, 3, 4, 5, 63, 64, 65, 1024)) {
            val bytes = ByteArray(size) { rng.nextInt(256).toByte() }
            val encoded = Base64Util.encode(bytes)
            assertArrayEquals(bytes, Base64Util.decode(encoded))
        }
    }
}
