package com.lioisme.modev.core.codec

import com.google.gson.JsonObject
import com.lioisme.modev.core.model.CryptoKind
import com.lioisme.modev.core.model.CryptoOperation
import com.lioisme.modev.core.model.CryptoRecord
import com.lioisme.modev.core.model.HookSwitch
import com.lioisme.modev.core.util.BytesView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WireCodecTest {

    @Test
    fun parse_invalid_lines() {
        assertNull(WireCodec.parse(""))
        assertNull(WireCodec.parse("not json"))
        assertNull(WireCodec.parse("{\"type\": 123}"))
    }

    @Test
    fun hello_roundtrip() {
        val line = WireCodec.hello("com.example.app", "com.example.app:push")
        val msg = WireCodec.parse(line)
        assertEquals(WireProtocol.TYPE_HELLO, msg!!.type)
        assertEquals("com.example.app", WireCodec.packageNameOf(msg.payload))
        assertEquals("com.example.app:push", msg.payload?.get("processName")?.asString)
        assertEquals(WireProtocol.MAGIC, msg.payload?.get("magic")?.asString)
        assertEquals(WireProtocol.VERSION, msg.payload?.get("version")?.asInt)
    }

    @Test
    fun config_roundtrip() {
        val switch = HookSwitch(messageDigest = false, base64 = true, maxStackDepth = 8)
        val line = WireCodec.config("com.example.app", switch)
        val msg = WireCodec.parse(line)
        assertEquals(WireProtocol.TYPE_CONFIG, msg!!.type)
        assertEquals("com.example.app", WireCodec.packageNameOf(msg.payload))
        val decoded = WireCodec.configOf(msg.payload)
        assertNotNull(decoded)
        assertEquals(false, decoded!!.messageDigest)
        assertEquals(true, decoded.base64)
        assertEquals(8, decoded.maxStackDepth)
    }

    @Test
    fun record_roundtrip() {
        val record = CryptoRecord(
            id = 0L,
            packageName = "com.example.app",
            timestamp = 123456789L,
            kind = CryptoKind.CIPHER,
            algorithm = "AES/GCM/NoPadding",
            operation = CryptoOperation.DO_FINAL,
            direction = 1,
            threadName = "main",
            key = BytesView.of(byteArrayOf(1, 2, 3)),
            iv = null,
            input = BytesView.of("hello".toByteArray(Charsets.UTF_8)),
            output = BytesView.of(byteArrayOf(9, 9)),
            keyAlias = "key0",
            keyExportable = true,
            provider = "AndroidOpenSSL",
            stack = listOf("com.example.app.Main:1"),
        )
        val line = WireCodec.record(record)
        val msg = WireCodec.parse(line)
        assertEquals(WireProtocol.TYPE_RECORD, msg!!.type)
        val decoded = WireCodec.recordOf(msg.payload)
        assertNotNull(decoded)
        assertEquals("com.example.app", decoded!!.packageName)
        assertEquals(CryptoKind.CIPHER, decoded.kind)
        assertEquals(CryptoOperation.DO_FINAL, decoded.operation)
        assertEquals(1, decoded.direction)
        assertEquals("AES/GCM/NoPadding", decoded.algorithm)
        assertEquals("010203", decoded.key!!.hex)
        assertNull(decoded.iv)
        assertEquals("hello", decoded.input!!.text)
        assertEquals("0909", decoded.output!!.hex)
        assertEquals("key0", decoded.keyAlias)
        assertEquals(true, decoded.keyExportable)
        assertEquals(listOf("com.example.app.Main:1"), decoded.stack)
    }

    @Test
    fun ping_pong() {
        val ping = WireCodec.parse(WireCodec.ping())
        assertEquals(WireProtocol.TYPE_PING, ping!!.type)
        assertNull(ping.payload)
        val pong = WireCodec.parse(WireCodec.pong())
        assertEquals(WireProtocol.TYPE_PONG, pong!!.type)
        assertNull(pong.payload)
    }

    @Test
    fun recordOf_invalid_payload() {
        assertNull(WireCodec.recordOf(null))
        assertNull(WireCodec.recordOf(JsonObject().apply { addProperty("foo", "bar") }))
    }

    @Test
    fun configOf_invalid_payload() {
        assertNull(WireCodec.configOf(null))
        assertNull(WireCodec.configOf(JsonObject().apply { addProperty("foo", "bar") }))
    }
}