package com.lioisme.modev.core.codec

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** 认证握手编解码:hello 令牌携带 / 缺省 / 非法行解析。 */
class WireCodecAuthTest {

    @Test
    fun `hello with token encodes token in payload`() {
        val line = WireCodec.hello("com.example.app", "com.example.app", "abc123")
        val msg = WireCodec.parse(line)
        assertEquals(WireProtocol.TYPE_HELLO, msg?.type)
        assertEquals("abc123", msg?.payload?.get(WireProtocol.KEY_TOKEN)?.asString)
    }

    @Test
    fun `hello without token omits token key`() {
        val line = WireCodec.hello("com.example.app", "com.example.app")
        val msg = WireCodec.parse(line)
        assertEquals(WireProtocol.TYPE_HELLO, msg?.type)
        assertFalse(msg?.payload?.has(WireProtocol.KEY_TOKEN) ?: true)
        assertEquals("MDEV", msg?.payload?.get("magic")?.asString)
        assertEquals(WireProtocol.VERSION, msg?.payload?.get("version")?.asInt)
    }

    @Test
    fun `hello carries package and process names`() {
        val msg = WireCodec.parse(WireCodec.hello("com.a.b", "com.a.b:sub"))
        assertEquals("com.a.b", msg?.payload?.get("packageName")?.asString)
        assertEquals("com.a.b:sub", msg?.payload?.get("processName")?.asString)
    }

    @Test
    fun `parse returns null for garbage`() {
        assertNull(WireCodec.parse("not json"))
        assertNull(WireCodec.parse(""))
        assertNull(WireCodec.parse("{\"noType\":true}"))
    }
}