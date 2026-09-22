package com.lioisme.modev.core.codec

import com.google.gson.JsonObject
import com.lioisme.modev.core.model.CryptoKind
import com.lioisme.modev.core.model.CryptoOperation
import com.lioisme.modev.core.model.CryptoRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 记录解码注册表:类型化分发与未知类型丢弃(ADR-1)。 */
class RecordCodecRegistryTest {

    @Test
    fun `crypto record decodes via registered codec`() {
        val registry = RecordCodecRegistry()
        registry.register(CryptoRecordCodec.RECORD_TYPE, CryptoRecordCodec::decode)

        val record = CryptoRecord(
            packageName = "com.example.app",
            kind = CryptoKind.CIPHER,
            operation = CryptoOperation.DO_FINAL,
            algorithm = "AES",
            threadName = "main",
            timestamp = 1L,
            seq = 5L,
        )
        val wire = WireRecord(
            seq = record.seq,
            recordType = record.recordType,
            payload = CryptoRecordCodec.encode(record),
        )
        val decoded = registry.decode(wire)
        assertTrue(decoded is CryptoRecord)
        assertEquals(5L, (decoded as CryptoRecord).seq)
    }

    @Test
    fun `unknown record type decodes to null and is not registered`() {
        val registry = RecordCodecRegistry()
        registry.register(CryptoRecordCodec.RECORD_TYPE, CryptoRecordCodec::decode)

        assertFalse(registry.isRegistered("database"))
        val wire = WireRecord(
            seq = 1L,
            recordType = "database",
            payload = JsonObject().apply { addProperty("table", "contacts") },
        )
        assertNull(registry.decode(wire))
    }

    @Test
    fun `re-registration replaces decoder deterministically`() {
        val registry = RecordCodecRegistry()
        var calls = 0
        // 值类型收口 MonitoredRecord(V45 T2.5)后,替身解码器同样返回类型化记录
        fun record(algorithm: String) = CryptoRecord(
            packageName = "pkg.a",
            kind = CryptoKind.CIPHER,
            operation = CryptoOperation.ENCRYPT,
            algorithm = algorithm,
            threadName = "test",
        )
        registry.register("crypto", { calls++; record("first") })
        registry.register("crypto", { calls++; record("second") })
        val decoded = registry.decode(WireRecord(1L, "crypto", JsonObject()))
        assertTrue("重复注册后以最后一次为准", decoded is CryptoRecord && decoded.algorithm == "second")
        assertEquals(1, calls)
    }

    @Test
    fun `decode is safe concurrently with registration`() {
        val registry = RecordCodecRegistry()
        registry.register(CryptoRecordCodec.RECORD_TYPE, CryptoRecordCodec::decode)
        // 装配期注册 + 协议线程读取:快照切换语义下并发读取不抛异常
        val threads = (1..4).map { i ->
            Thread {
                repeat(200) { n ->
                    val wire = WireRecord(
                        seq = (i * 1000 + n).toLong(),
                        recordType = CryptoRecordCodec.RECORD_TYPE,
                        payload = JsonObject().apply { addProperty("seq", (i * 1000 + n).toLong()) },
                    )
                    registry.decode(wire)
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
    }
}