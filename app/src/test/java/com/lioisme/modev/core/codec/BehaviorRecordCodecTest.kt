package com.lioisme.modev.core.codec

import com.lioisme.modev.core.model.BehaviorKind
import com.lioisme.modev.core.model.BehaviorOperation
import com.lioisme.modev.core.model.BehaviorRecord
import com.lioisme.modev.core.util.BytesView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * BehaviorRecord 载荷编解码契约:与 Crypto/Sql codec 同规——
 * - encode/decode Gson 往返对称(枚举/BytesView/params/stack 全字段);
 * - 畸形载荷(kind/operation 未知、包名空白)返回 null,绝不误标;
 * - recordType 恒 "behavior",注册表按其分发。
 */
class BehaviorRecordCodecTest {

    private val record = BehaviorRecord(
        seq = 7L,
        packageName = "com.example.app",
        kind = BehaviorKind.FILE_RENAME,
        operation = BehaviorOperation.RENAME,
        path = "/data/data/com.example.app/cache/old.bin",
        destPath = "/data/data/com.example.app/files/new.bin",
        fileSize = 128L,
        params = mapOf("api" to "file"),
        processName = "com.example.app:worker",
        contextId = 42L,
        threadName = "io",
        stack = listOf("a.b.c", "d.e.f"),
        timestamp = 1_700_000_000_000,
    )

    @Test
    fun `roundtrip preserves all fields`() {
        val decoded = BehaviorRecordCodec.decode(BehaviorRecordCodec.encode(record))
        assertNotNull(decoded)
        assertEquals(record, decoded)
    }

    @Test
    fun `content bytes view roundtrips`() {
        val withContent = record.copy(
            kind = BehaviorKind.ASSET_READ,
            operation = BehaviorOperation.ASSET_OPEN,
            path = "assets://config/app.json",
            destPath = null,
            content = BytesView(hex = "deadbeef", base64 = "3q2+7w==", text = null, totalLength = 4, truncated = false),
        )
        assertEquals(withContent, BehaviorRecordCodec.decode(BehaviorRecordCodec.encode(withContent)))
    }

    @Test
    fun `unknown kind or operation dropped`() {
        val badKind = BehaviorRecordCodec.encode(record).apply { addProperty("kind", "NOT_A_KIND") }
        assertNull(BehaviorRecordCodec.decode(badKind))
        val badOp = BehaviorRecordCodec.encode(record).apply { addProperty("operation", "NOT_AN_OP") }
        assertNull(BehaviorRecordCodec.decode(badOp))
    }

    @Test
    fun `blank package name dropped and missing fields dropped`() {
        val blankPkg = BehaviorRecordCodec.encode(record).apply { addProperty("packageName", " ") }
        assertNull(BehaviorRecordCodec.decode(blankPkg))
        val noKind = BehaviorRecordCodec.encode(record)
        noKind.remove("kind")
        assertNull(BehaviorRecordCodec.decode(noKind))
    }
}
