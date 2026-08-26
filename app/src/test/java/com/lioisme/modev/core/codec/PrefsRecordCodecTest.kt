package com.lioisme.modev.core.codec

import com.lioisme.modev.core.model.PrefsKind
import com.lioisme.modev.core.model.PrefsOperation
import com.lioisme.modev.core.model.PrefsRecord
import com.lioisme.modev.core.util.BytesView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * PrefsRecordCodec 契约(V28):Gson 往返对称;值域 BytesView 往返;
 * 未知枚举/空包名/缺关键字段丢弃(与 BehaviorRecordCodec 同规)。
 */
class PrefsRecordCodecTest {

    private fun record(
        kind: PrefsKind = PrefsKind.PUT,
        operation: PrefsOperation = PrefsOperation.PUT_STRING,
        value: BytesView? = null,
    ) = PrefsRecord(
        packageName = "com.example.app",
        kind = kind,
        operation = operation,
        key = "server_url",
        value = value,
        path = "/data/data/com.example.app/shared_prefs/config.xml",
        params = mapOf("type" to "string"),
        threadName = "main",
        stack = listOf("a.b.C.m(C.kt:1)"),
        timestamp = 1_700_000_000_000,
    )

    @Test
    fun `roundtrip preserves all fields`() {
        val record = record()
        assertEquals(record, PrefsRecordCodec.decode(PrefsRecordCodec.encode(record)))
    }

    @Test
    fun `value bytes view roundtrips`() {
        val withValue =
            record(value = BytesView(hex = "68656c6c6f", base64 = null, text = "hello", totalLength = 5, truncated = false))
        assertEquals(withValue, PrefsRecordCodec.decode(PrefsRecordCodec.encode(withValue)))
    }

    @Test
    fun `unknown kind or operation dropped`() {
        val badKind = PrefsRecordCodec.encode(record()).apply { addProperty("kind", "NOT_A_KIND") }
        val badOp = PrefsRecordCodec.encode(record()).apply { addProperty("operation", "NOT_AN_OP") }
        assertNull(PrefsRecordCodec.decode(badKind))
        assertNull(PrefsRecordCodec.decode(badOp))
    }

    @Test
    fun `blank package name dropped and missing fields dropped`() {
        val blankPkg =
            PrefsRecordCodec.encode(record()).apply { remove("packageName"); addProperty("packageName", " ") }
        val noKind = PrefsRecordCodec.encode(record()).apply { remove("kind") }
        assertNull(PrefsRecordCodec.decode(blankPkg))
        assertNotNull(PrefsRecordCodec.decode(PrefsRecordCodec.encode(record())))
        assertNull(PrefsRecordCodec.decode(noKind))
    }
}
