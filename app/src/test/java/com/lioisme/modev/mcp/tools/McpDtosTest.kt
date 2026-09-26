package com.lioisme.modev.mcp.tools

import com.lioisme.modev.core.model.CryptoKind
import com.lioisme.modev.core.model.CryptoOperation
import com.lioisme.modev.core.model.CryptoRecord
import com.lioisme.modev.core.model.PrefsKind
import com.lioisme.modev.core.model.PrefsOperation
import com.lioisme.modev.core.model.PrefsRecord
import com.lioisme.modev.core.model.RecordTypes
import com.lioisme.modev.core.model.SqlKind
import com.lioisme.modev.core.model.SqlOperation
import com.lioisme.modev.domain.record.CryptoDetail
import com.lioisme.modev.domain.record.CryptoRow
import com.lioisme.modev.domain.record.PrefsDetail
import com.lioisme.modev.domain.record.PrefsRow
import com.lioisme.modev.domain.record.SqlRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MCP DTO 映射门禁:统一信封形态、NULL 语义(serializeNulls)、大字段截断守卫。
 */
class McpDtosTest {

    private fun cryptoRow(id: Long) = CryptoRow(
        record = CryptoRecord(
            id = id,
            recordType = RecordTypes.CRYPTO,
            packageName = "pkg.a",
            kind = CryptoKind.CIPHER,
            operation = CryptoOperation.ENCRYPT,
            algorithm = "AES/CBC/PKCS5Padding",
            threadName = "main",
            timestamp = 1_000L,
        ),
    )

    @Test
    fun row_envelope_shape() {
        val json = McpDtos.row(cryptoRow(7))
        assertEquals(7, json.get("id").asLong)
        assertEquals("crypto", json.get("recordType").asString)
        assertEquals("CIPHER", json.get("kind").asString)
        assertEquals("ENCRYPT", json.get("operation").asString)
        assertEquals("pkg.a", json.get("packageName").asString)
        assertTrue(json.get("fields").asJsonObject.has("algorithm"))
    }

    @Test
    fun prefs_row_envelope() {
        val row = PrefsRow(
            recordId = 9,
            recordTimestamp = 2_000L,
            packageName = "pkg.b",
            kind = PrefsKind.PUT,
            operation = PrefsOperation.PUT_STRING,
            prefKey = "user_token",
            path = "/data/user/0/pkg.b/shared_prefs/x.xml",
            threadName = "main",
        )
        val json = McpDtos.row(row)
        assertEquals("prefs", json.get("recordType").asString)
        assertEquals("PUT", json.get("kind").asString)
        assertEquals("user_token", json.get("fields").asJsonObject.get("prefKey").asString)
    }

    @Test
    fun detail_includes_null_fields_via_serializeNulls() {
        val model = CryptoDetail(cryptoRow(7).record.copy(id = 7, provider = null))
        val text = McpJson.gson.toJson(McpDtos.detail(model))
        // SQL NULL 语义:provider 为 null 必须落 "provider":null(Gson 默认省略键)
        assertTrue("null fields must serialize", text.contains("\"provider\":null"))
    }

    @Test
    fun detail_truncates_oversized_fields() {
        val big = "x".repeat(McpDtos.MAX_FIELD_CHARS + 100)
        val model = CryptoDetail(cryptoRow(7).record.copy(id = 7, algorithm = big))
        val json = McpDtos.detail(model)
        assertTrue(json.get("truncated").asBoolean)
        val alg = json.get("record").asJsonObject.get("algorithm").asString
        assertTrue(alg.endsWith("[truncated]"))
        assertTrue(alg.length <= McpDtos.MAX_FIELD_CHARS + 20)
    }

    @Test
    fun detail_no_truncation_flag_when_clean() {
        // V45 T3.3 / DUAL-8:`truncated` **恒在**(旧形态「键不存在」与「false」两种口径混用)
        val json = McpDtos.detail(CryptoDetail(cryptoRow(7).record.copy(id = 7)))
        assertTrue("truncated key must always be present", json.has("truncated"))
        assertFalse(json.get("truncated").asBoolean)
    }

    /**
     * V45 T3.3 / DUAL-7 列表行信封统一:身份键只在顶层出现一次,`fields` 只剩类型特有列。
     * 旧形态算法行叫 `fields.id`、其余九型叫 `fields.recordId`(两种命名同存一处),
     * Agent 侧读法不统一且身份键在信封与 fields 各存一份。
     * 三个臂覆盖两种载体形态:类型化记录载体(crypto)、行投影载体(prefs/sql)。
     */
    @Test
    fun row_envelope_identity_keys_are_not_duplicated_in_fields() {
        val identity = setOf(
            "id", "seq", "recordId", "recordType", "packageName", "kind", "operation", "threadName",
            "timestamp", "recordTimestamp",
        )
        val sqlRow = SqlRow(
            recordId = 11,
            recordTimestamp = 3_000L,
            packageName = "pkg.c",
            kind = SqlKind.QUERY,
            operation = SqlOperation.QUERY,
            databaseName = "app.db",
            sqlPreview = "SELECT 1",
            rowCount = 1,
            affectedCount = null,
            rowId = null,
            threadName = "main",
        )
        listOf(McpDtos.row(cryptoRow(7)), McpDtos.row(sqlRow), McpDtos.row(prefsSeed())).forEach { json ->
            assertEquals(
                "envelope keys must be exactly the uniform eight",
                setOf(
                    "id", "recordType", "packageName", "kind", "operation", "threadName", "timestamp", "fields",
                ),
                json.keySet(),
            )
            val leaked = json.get("fields").asJsonObject.keySet().intersect(identity)
            assertTrue("identity keys must not leak into fields: $leaked", leaked.isEmpty())
        }
    }

    private fun prefsSeed() = PrefsRow(
        recordId = 9,
        recordTimestamp = 2_000L,
        packageName = "pkg.b",
        kind = PrefsKind.PUT,
        operation = PrefsOperation.PUT_STRING,
        prefKey = "user_token",
        path = "/data/user/0/pkg.b/shared_prefs/x.xml",
        threadName = "main",
    )
}
