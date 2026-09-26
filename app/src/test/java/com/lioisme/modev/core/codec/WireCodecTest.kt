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
        val line = WireCodec.record(record.seq, record.recordType, CryptoRecordCodec.encode(record))
        val msg = WireCodec.parse(line)
        assertEquals(WireProtocol.TYPE_RECORD, msg!!.type)
        val wire = WireCodec.recordOf(msg.payload)
        assertNotNull(wire)
        assertEquals(CryptoRecordCodec.RECORD_TYPE, wire!!.recordType)
        val decoded = CryptoRecordCodec.decode(wire.payload)
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
        val msg = WireCodec.parse(WireCodec.record(record.seq, record.recordType, CryptoRecordCodec.encode(record)))!!
        val wire = WireCodec.recordOf(msg.payload)
        assertNotNull(wire)
        assertEquals(7L, wire!!.seq)
        assertEquals(7L, CryptoRecordCodec.decode(wire.payload)!!.seq)
    }

    @Test
    fun recordOf_invalid_payload() {
        assertNull(WireCodec.recordOf(null))
        // 无 recordType:类型化分发依据缺失
        assertNull(WireCodec.recordOf(JsonObject().apply { addProperty("foo", "bar") }))
        // 有 recordType 但 seq 缺失:ACK 游标依据缺失
        assertNull(
            WireCodec.recordOf(
                JsonObject().apply { addProperty(WireProtocol.KEY_RECORD_TYPE, "crypto") },
            ),
        )
        // recordType 为数字:类型化分发依据必须是字符串
        assertNull(
            WireCodec.recordOf(
                JsonObject().apply {
                    addProperty(WireProtocol.KEY_RECORD_TYPE, 123)
                    addProperty(WireProtocol.KEY_SEQ, 1L)
                },
            ),
        )
    }

    @Test
    fun recordOf_preserves_unknown_record_type_for_registry_dispatch() {
        // 未来监听类型(未知于当前编解码器):信封完整保留,由宿主注册表决定解码与否
        val payload = JsonObject().apply {
            addProperty("recordType", "database")
            addProperty("seq", 3L)
            addProperty("packageName", "com.example.app")
            addProperty("table", "contacts")
        }
        val wire = WireCodec.recordOf(payload)
        assertNotNull(wire)
        assertEquals("database", wire!!.recordType)
        assertEquals(3L, wire.seq)
        assertEquals("com.example.app", WireCodec.packageNameOf(wire.payload))
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
        val msg = WireCodec.parse(WireCodec.record(record.seq, record.recordType, CryptoRecordCodec.encode(record)))!!
        val wire = WireCodec.recordOf(msg.payload)
        assertNotNull(wire)
        val decoded = CryptoRecordCodec.decode(wire!!.payload)
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
        assertTrue(WireCodec.isProtocolCompatible(msg.payload))
    }

    @Test
    fun is_protocol_compatible_checks_magic_and_version() {
        assertTrue(WireCodec.isProtocolCompatible(WireCodec.parse(WireCodec.hello("pkg", "pkg"))!!.payload))
        val bad = JsonObject().apply {
            addProperty("magic", "WRONG")
            addProperty("version", WireProtocol.VERSION)
        }
        assertFalse(WireCodec.isProtocolCompatible(bad))
        val badVersion = JsonObject().apply {
            addProperty("magic", WireProtocol.MAGIC)
            addProperty("version", WireProtocol.VERSION + 1)
        }
        assertFalse(WireCodec.isProtocolCompatible(badVersion))
        assertFalse(WireCodec.isProtocolCompatible(JsonObject()))
        assertFalse(WireCodec.isProtocolCompatible(null))
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
        val msg = WireCodec.parse(WireCodec.record(record.seq, record.recordType, CryptoRecordCodec.encode(record)))!!
        val wire = WireCodec.recordOf(msg.payload)
        assertNotNull(wire)
        val decoded = CryptoRecordCodec.decode(wire!!.payload)
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

    @Test
    fun crypto_record_codec_rejects_malformed_payloads() {
        // 缺 kind:畸形载荷,不得误标为任何枚举
        assertNull(CryptoRecordCodec.decode(JsonObject().apply {
            addProperty("packageName", "com.example.app")
            addProperty("operation", "encrypt")
            addProperty("seq", 1L)
            addProperty("recordType", "crypto")
        }))
        // 空包名:非法
        assertNull(CryptoRecordCodec.decode(JsonObject().apply {
            addProperty("packageName", "")
            addProperty("kind", "cipher")
            addProperty("operation", "encrypt")
            addProperty("seq", 1L)
            addProperty("recordType", "crypto")
        }))
    }

    @Test
    fun crypto_record_codec_rejects_unknown_enum_values() {
        val payload = JsonObject().apply {
            addProperty("packageName", "com.example.app")
            addProperty("kind", "quantum_compute")
            addProperty("operation", "encrypt")
            addProperty("seq", 1L)
            addProperty("recordType", "crypto")
        }
        // 未知 kind:解码失败(null),由宿主管线计数丢弃,绝不回退误标
        assertNull(CryptoRecordCodec.decode(payload))
    }
}
