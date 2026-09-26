package com.lioisme.modev.data.mapping

import com.lioisme.modev.core.model.PrefsKind
import com.lioisme.modev.core.model.PrefsOperation
import com.lioisme.modev.core.model.PrefsRecord
import com.lioisme.modev.core.util.BytesView
import com.lioisme.modev.data.db.CryptoRecordEntity
import com.lioisme.modev.data.db.CryptoRecordRow
import com.lioisme.modev.domain.record.PrefsDetail
import com.lioisme.modev.domain.record.PrefsRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 偏好记录映射契约:wire ↔ 实体 ↔ 投影行往返对称;
 * 枚举严格解析(未知返回 null);导出 JSON 含关键键。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PrefsRecordMappersTest {

    private fun record() = PrefsRecord(
        id = 7L,
        seq = 3L,
        packageName = "com.example.app",
        kind = PrefsKind.GET,
        operation = PrefsOperation.GET_STRING,
        key = "server_url",
        value = BytesView(hex = "68656c6c6f", base64 = null, text = "hello", totalLength = 5, truncated = false),
        path = "/data/data/com.example.app/shared_prefs/config.xml",
        params = mapOf("type" to "string"),
        processName = "com.example.app",
        threadName = "main",
        stack = listOf("a.b.C.m(C.kt:1)"),
        timestamp = 1_700_000_000_000L,
    )

    @Test
    fun `entity roundtrip preserves prefs fields`() {
        val entity = record().toEntity()
        assertEquals("prefs", entity.recordType)
        assertEquals("server_url", entity.prefKey)
        assertEquals("/data/data/com.example.app/shared_prefs/config.xml", entity.path)
        assertNull(entity.command)
        assertNull(entity.fileSize)
        val restored = entity.toPrefsModelOrNull()
        assertNotNull(restored)
        // id 由落库分配,实体侧为 0;其余字段对称
        assertEquals(record().copy(id = 0L), restored)
    }

    @Test
    fun `unknown kind or operation drops row`() {
        val badKind = record().toEntity().copy(kind = "NOT_A_KIND")
        val badOp = record().toEntity().copy(operation = "NOT_AN_OP")
        assertNull(badKind.toPrefsModelOrNull())
        assertNull(badOp.toPrefsModelOrNull())
    }

    @Test
    fun `row model maps headline from prefKey`() {
        val row = prefsRowFor(record().copy(key = "dark_mode", path = "/data/data/pkg/shared_prefs/u.xml"))
        val model = row.prefsRowModelOrNull()
        assertNotNull(model)
        assertEquals("dark_mode", model!!.headlineText())
        // 键缺失时回落文件 basename
        val noKey = row.copy(prefKey = null)
        assertEquals("u.xml", noKey.prefsRowModelOrNull()!!.headlineText())
    }

    @Test
    fun `detail dispatch routes prefs rows and entities`() {
        val entity = record().toEntity()
        val detail = entity.toRecordDetailOrNull()
        assertNotNull(detail)
        assertEquals(PrefsDetail::class, detail!!::class)
        val rowModel = prefsRowFor(record()).toRowModelOrNull()
        assertEquals(PrefsRow::class, rowModel!!::class)
    }

    @Test
    fun `export json carries identity keys`() {
        val json = record().toJson()
        assertTrue(json.contains("\"recordType\": \"prefs\""))
        assertTrue(json.contains("\"server_url\""))
        assertTrue(json.contains("config.xml"))
    }

    private fun prefsRowFor(record: PrefsRecord): CryptoRecordRow =
        CryptoRecordRow(
            id = record.id,
            recordType = record.recordType,
            packageName = record.packageName,
            kind = record.kind.name,
            operation = record.operation.name,
            algorithm = "",
            threadName = record.threadName,
            direction = null,
            timestamp = record.timestamp,
            path = record.path,
            prefKey = record.key,
        )
}
