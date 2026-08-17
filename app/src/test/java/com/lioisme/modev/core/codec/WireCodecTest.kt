package com.lioisme.modev.core.codec

import com.google.gson.JsonObject
import com.lioisme.modev.core.model.CryptoKind
import com.lioisme.modev.core.model.CryptoOperation
import com.lioisme.modev.core.model.CryptoRecord
import com.lioisme.modev.core.model.HookSwitch
import com.lioisme.modev.core.util.BytesView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun ack_roundtrip() {
        val line = WireCodec.ack(42L)
        val msg = WireCodec.parse(line)
        assertEquals(WireProtocol.TYPE_ACK, msg!!.type)
        assertEquals(42L, WireCodec.seqOf(msg.payload))
    }

    @Test
    fun seqOf_invalid_payload_returns_null() {
        assertNull(WireCodec.seqOf(null))
        assertNull(WireCodec.seqOf(JsonObject().apply { addProperty("other", 1) }))
        assertNull(WireCodec.seqOf(JsonObject().apply { addProperty(WireProtocol.KEY_SEQ, "not-a-number") }))
    }

    @Test
    fun record_roundtrip_carries_seq() {
        val record = CryptoRecord(
            id = 0L,
            packageName = "com.example.app",
            timestamp = 1L,
            kind = CryptoKind.CIPHER,
            operation = CryptoOperation.DO_FINAL,
            algorithm = "AES",
            threadName = "main",
            processName = "com.example.app",
            seq = 7L,
        )
        val msg = WireCodec.parse(WireCodec.record(record))!!
        val decoded = WireCodec.recordOf(msg.payload)
        assertNotNull(decoded)
        assertEquals(7L, decoded!!.seq)
    }

    @Test
    fun recordOf_invalid_payload() {
        assertNull(WireCodec.recordOf(null))
        assertNull(WireCodec.recordOf(JsonObject().apply { addProperty("foo", "bar") }))
    }

    @Test
    fun recordOf_accepts_empty_algorithm_random_records() {
        val record = CryptoRecord(
            id = 0,
            packageName = "com.example.app",
            timestamp = 1L,
            kind = CryptoKind.RANDOM,
            operation = CryptoOperation.GENERATE,
            algorithm = "",
            threadName = "main",
            processName = "com.example.app",
            seq = 9L,
        )
        val msg = WireCodec.parse(WireCodec.record(record))!!
        val decoded = WireCodec.recordOf(msg.payload)
        assertNotNull(decoded)
        assertEquals("", decoded!!.algorithm)
        assertEquals(9L, decoded.seq)
    }

    @Test
    fun configOf_invalid_payload() {
        assertNull(WireCodec.configOf(null))
        assertNull(WireCodec.configOf(JsonObject().apply { addProperty("foo", "bar") }))
    }

    @Test
    fun config_push_carries_magic_and_version_for_dual_end_validation() {
        val line = WireCodec.config("com.example.app", HookSwitch.DEFAULT)
        val msg = WireCodec.parse(line)
        assertEquals(WireProtocol.TYPE_CONFIG, msg!!.type)
        // 注入侧凭此做双端强校验
        assertTrue(WireCodec.isHelloCompatible(msg.payload))
    }

    @Test
    fun is_hello_compatible_checks_magic_and_version() {
        assertTrue(WireCodec.isHelloCompatible(WireCodec.parse(WireCodec.hello("pkg", "pkg"))!!.payload))
        val bad = JsonObject().apply {
            addProperty("magic", "WRONG")
            addProperty("version", WireProtocol.VERSION)
        }
        assertFalse(WireCodec.isHelloCompatible(bad))
        val badVersion = JsonObject().apply {
            addProperty("magic", WireProtocol.MAGIC)
            addProperty("version", WireProtocol.VERSION + 1)
        }
        assertFalse(WireCodec.isHelloCompatible(badVersion))
        assertFalse(WireCodec.isHelloCompatible(JsonObject()))
        assertFalse(WireCodec.isHelloCompatible(null))
    }

    @Test
    fun record_roundtrip_full_capture_fields() {
        val record = CryptoRecord(
            id = 0L,
            packageName = "com.example.app",
            timestamp = 1L,
            kind = CryptoKind.KEY_STORE,
            operation = CryptoOperation.GET_KEY,
            algorithm = "AES",
            provider = "AndroidOpenSSL",
            key = BytesView.of(byteArrayOf(1)),
            keyAlias = "alias0",
            keyExportable = false,
            salt = BytesView.of(byteArrayOf(2)),
            aad = BytesView.of(byteArrayOf(3)),
            publicKey = BytesView.of(byteArrayOf(4)),
            params = mapOf("alias" to "alias0", "field" to "password"),
            verifyResult = true,
            processName = "com.example.app:push",
            contextId = 42L,
            threadName = "worker",
            stack = listOf("com.example.app.Main:1"),
        )
        val msg = WireCodec.parse(WireCodec.record(record))!!
        val decoded = WireCodec.recordOf(msg.payload)
        assertNotNull(decoded)
        assertEquals("02", decoded!!.salt?.hex)
        assertEquals("03", decoded.aad?.hex)
        assertEquals("04", decoded.publicKey?.hex)
        assertEquals(mapOf("alias" to "alias0", "field" to "password"), decoded.params)
        assertEquals(true, decoded.verifyResult)
        assertEquals("com.example.app:push", decoded.processName)
        assertEquals(42L, decoded.contextId)
        assertEquals("alias0", decoded.keyAlias)
        assertEquals(false, decoded.keyExportable)
    }
}