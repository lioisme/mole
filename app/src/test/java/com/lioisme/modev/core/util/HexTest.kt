package com.lioisme.modev.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class HexTest {

    @Test
    fun encode_basic() {
        assertEquals("", Hex.encode(byteArrayOf()))
        assertEquals("00", Hex.encode(byteArrayOf(0)))
        assertEquals("ff", Hex.encode(byteArrayOf(-1)))
        assertEquals("000102ff", Hex.encode(byteArrayOf(0, 1, 2, -1)))
        assertEquals("cafebabe", Hex.encode(byteArrayOf(0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte())))
        assertEquals("616263", Hex.encode("abc".toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun encode_range() {
        val bytes = byteArrayOf(0x11, 0x22, 0x33, 0x44)
        assertEquals("2233", Hex.encode(bytes, 1, 2))
        assertEquals("11", Hex.encode(bytes, 0, 1))
        assertEquals("", Hex.encode(bytes, 2, 0))
    }

    @Test
    fun encode_range_invalid() {
        val bytes = byteArrayOf(1, 2, 3)
        assertThrows(IllegalArgumentException::class.java) { Hex.encode(bytes, -1, 1) }
        assertThrows(IllegalArgumentException::class.java) { Hex.encode(bytes, 0, 4) }
        assertThrows(IllegalArgumentException::class.java) { Hex.encode(bytes, 2, 2) }
    }

    @Test
    fun encode_large() {
        val bytes = ByteArray(256) { it.toByte() }
        val hex = Hex.encode(bytes)
        assertEquals(512, hex.length)
        assertEquals("000102", hex.substring(0, 6))
        assertEquals("ff", hex.substring(hex.length - 2))
    }
}