package com.lioisme.modev.data.mapping

import com.lioisme.modev.core.model.CryptoKind
import com.lioisme.modev.core.model.CryptoOperation
import com.lioisme.modev.core.model.CryptoRecord
import com.lioisme.modev.core.model.RecordTypes
import com.lioisme.modev.core.model.SqlBindValue
import com.lioisme.modev.core.model.SqlKind
import com.lioisme.modev.core.model.SqlOperation
import com.lioisme.modev.core.model.SqlRecord
import com.lioisme.modev.core.model.SqlResultCell
import com.lioisme.modev.core.model.SqlResultRow
import com.lioisme.modev.core.model.SqlValueType
import com.lioisme.modev.core.util.BytesView
import com.lioisme.modev.data.db.CryptoRecordEntity
import com.lioisme.modev.data.db.CryptoRecordRow
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 实体 ↔ 模型全字段双向映射 + JSON 导出(ADR-4/L8,P5-2 补充)。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CryptoRecordMappersTest {

    private fun sampleRecord() = CryptoRecord(
        id = 7L,
        seq = 42L,
        recordType = RecordTypes.CRYPTO,
        packageName = "com.example.app",
        kind = CryptoKind.CIPHER,
        operation = CryptoOperation.ENCRYPT,
        algorithm = "AES/GCM/NoPadding",
        provider = "AndroidKeyStoreBCWorkaround",
        direction = 1,
        key = BytesView(hex = "aabb", base64 = "qrs=", text = null, totalLength = 2, truncated = false),
        iv = BytesView(hex = "cafebabe", base64 = "yvq6vg==", text = null, totalLength = 4, truncated = false),
        keyAlias = "alias_1",
        keyExportable = false,
        input = BytesView(hex = "deadbeef", base64 = "3q2+7w==", text = "secret", totalLength = 4, truncated = false),
        output = BytesView(hex = "00ff", base64 = "AP8=", text = null, totalLength = 2, truncated = true),
        salt = null,
        aad = BytesView(hex = "a1b2", base64 = "obI=", text = null, totalLength = 2, truncated = false),
        publicKey = null,
        params = mapOf("mode" to "GCM"),
        verifyResult = true,
        processName = "com.example.app:sub",
        contextId = 9L,
        threadName = "main",
        stack = listOf("a.b.c()", "d.e.f()"),
        timestamp = 1_700_000_000_000L,
    )

    @Test
    fun `model to entity preserves all fields`() {
        val entity = sampleRecord().toEntity()
        assertEquals("com.example.app", entity.packageName)
        assertEquals(CryptoKind.CIPHER.name, entity.kind)
        assertEquals(CryptoOperation.ENCRYPT.name, entity.operation)
        assertEquals("AES/GCM/NoPadding", entity.algorithm)
        assertEquals(1, entity.direction)
        assertEquals("aabb", entity.keyHex)
        assertEquals("qrs=", entity.keyB64)
        assertEquals(2, entity.keyLength)
        assertEquals("alias_1", entity.keyAlias)
        assertEquals("deadbeef", entity.inputHex)
        assertEquals("3q2+7w==", entity.inputB64)
        assertEquals("secret", entity.inputText)
        assertEquals("00ff", entity.outputHex)
        assertEquals("AP8=", entity.outputB64)
        assertTrue(entity.outputTruncated)
        assertEquals("{\"mode\":\"GCM\"}", entity.paramsJson)
        assertEquals("[\"a.b.c()\",\"d.e.f()\"]", entity.stackJson)
        assertEquals(42L, entity.seq)
    }

    @Test
    fun `entity to model round trips full record`() {
        val model = sampleRecord().toEntity().toModelOrNull()
        assertNotNull(model)
        assertEquals(sampleRecord().copy(id = 0L), model)
    }

    @Test
    fun `legacy entity without base64 yields null base64 views`() {
        val legacy = sampleRecord().toEntity().copy(
            keyB64 = null, inputB64 = null, outputB64 = null,
            ivB64 = null, aadB64 = null,
        )
        val model = legacy.toModelOrNull()
        assertNotNull(model)
        assertEquals("aabb", model!!.key?.hex)
        assertNull(model.key!!.base64)
        assertNull(model.input?.base64)
        assertEquals("secret", model.input?.text)
    }

    @Test
    fun `unknown enum values map to null instead of mislabeling`() {
        val entity = sampleRecord().toEntity().copy(kind = "FUTURE_KIND", operation = "HANDSHAKE")
        assertNull(entity.toModelOrNull())
    }

    @Test
    fun `row projection produces preview model`() {
        val row = CryptoRecordRow(
            id = 1L,
            recordType = RecordTypes.CRYPTO,
            packageName = "com.example.app",
            kind = CryptoKind.SIGNATURE.name,
            operation = CryptoOperation.SIGN.name,
            algorithm = "SHA256withRSA",
            threadName = "worker",
            direction = null,
            timestamp = 5L,
            keyAlias = null,
            keyPreview = "deadbeef",
            keyTruncated = true,
            inputPreview = null,
            inputTruncated = false,
            outputPreview = "00ff",
            outputTruncated = false,
        )
        val model = row.toRowModelOrNull()
        assertNotNull(model)
        val crypto = (model as CryptoRow).record
        assertEquals(CryptoKind.SIGNATURE, crypto.kind)
        assertEquals(CryptoOperation.SIGN, crypto.operation)
        assertEquals("deadbeef", crypto.key?.hex)
        assertTrue(crypto.key!!.truncated)
        assertEquals(4, crypto.key!!.totalLength)
        assertNull(crypto.input)
        assertEquals("worker", crypto.threadName)
        assertNull(crypto.direction)
    }

    @Test
    fun `row projection drops unknown kind`() {
        val row = CryptoRecordRow(
            id = 1L,
            recordType = RecordTypes.CRYPTO,
            packageName = "p",
            kind = "FUTURE",
            operation = CryptoOperation.SIGN.name,
            algorithm = "a",
            threadName = "",
            timestamp = 0L,
        )
        assertNull(row.toRowModelOrNull())
    }

    @Test
    fun `toJson exports structured fields with indentation`() {
        val json = JSONObject(sampleRecord().toJson())
        assertEquals("com.example.app", json.getString("packageName"))
        assertEquals(CryptoKind.CIPHER.name, json.getString("kind"))
        assertEquals(2, json.getJSONObject("key").getInt("totalLength"))
        assertEquals("qrs=", json.getJSONObject("key").getString("base64"))
        assertEquals("secret", json.getJSONObject("input").getString("text"))
        assertEquals("3q2+7w==", json.getJSONObject("input").getString("base64"))
        assertEquals("GCM", json.getJSONObject("params").getString("mode"))
        assertEquals(JSONArray(listOf("a.b.c()", "d.e.f()")).toString(), json.getJSONArray("stack").toString())
        assertEquals(1_700_000_000_000L, json.getLong("timestamp"))
    }

    @Test
    fun `toJson omits absent fields`() {
        val sparse = CryptoRecord(
            packageName = "p",
            kind = CryptoKind.MESSAGE_DIGEST,
            operation = CryptoOperation.DIGEST,
            algorithm = "SHA-256",
            threadName = "",
        )
        val json = JSONObject(sparse.toJson())
        assertTrue(!json.has("key"))
        assertTrue(!json.has("params"))
        assertEquals(0, json.getJSONArray("stack").length())
    }

    private fun sampleSqlRecord() = SqlRecord(
        id = 3L,
        seq = 11L,
        packageName = "com.example.db",
        kind = SqlKind.QUERY,
        operation = SqlOperation.QUERY,
        databaseName = "app.db",
        provider = "SQLiteDatabase",
        dbPath = "/data/data/com.example.db/databases/app.db",
        sql = "SELECT * FROM t WHERE id = ?",
        bindArgs = listOf(SqlBindValue(i = 1, t = SqlValueType.L, v = "7")),
        rows = listOf(
            SqlResultRow(
                c = listOf(
                    SqlResultCell(n = "id", t = SqlValueType.L, v = "7"),
                    SqlResultCell(n = "blob", t = SqlValueType.B, hex = "aabb", len = 2, truncated = false),
                ),
            ),
        ),
        rowCount = 1,
        affectedCount = null,
        rowId = null,
        resultText = null,
        params = mapOf("table" to "t"),
        processName = "com.example.db:main",
        contextId = 5L,
        threadName = "db-worker",
        stack = listOf("x.y()"),
        timestamp = 1_700_000_100_000L,
    )

    @Test
    fun `sql record to entity preserves db columns`() {
        val entity = sampleSqlRecord().toEntity()
        assertEquals(RecordTypes.DATABASE, entity.recordType)
        assertEquals("com.example.db", entity.packageName)
        assertEquals(SqlKind.QUERY.name, entity.kind)
        assertEquals(SqlOperation.QUERY.name, entity.operation)
        assertEquals("app.db", entity.algorithm)
        assertEquals("/data/data/com.example.db/databases/app.db", entity.dbPath)
        assertEquals("SELECT * FROM t WHERE id = ?", entity.sql)
        assertEquals(1, entity.rowCount)
        assertNull(entity.affectedCount)
        assertNull(entity.rowId)
        assertNull(entity.resultText)
        assertEquals(11L, entity.seq)
        // bindArgsJson/rowsJson 与模型字段名同构(i/t/v/hex/len/truncated、c/n),可逆解析
        assertTrue(entity.bindArgsJson!!.contains("\"i\":1"))
        assertTrue(entity.bindArgsJson!!.contains("\"t\":\"L\""))
        assertTrue(entity.bindArgsJson!!.contains("\"v\":\"7\""))
        assertTrue(entity.rowsJson!!.contains("\"n\":\"id\""))
        assertTrue(entity.rowsJson!!.contains("\"hex\":\"aabb\""))
    }

    @Test
    fun `sql entity to model round trips full record`() {
        val back = sampleSqlRecord().toEntity().toSqlModelOrNull()
        assertNotNull(back)
        assertEquals(sampleSqlRecord().copy(id = 0L), back)
    }

    @Test
    fun `sql entity to detail dispatches by recordType`() {
        val detail = sampleSqlRecord().toEntity().toRecordDetailOrNull()
        assertNotNull(detail)
        assertTrue(detail is SqlDetail)
        assertEquals("app.db", (detail as SqlDetail).record.databaseName)
        assertEquals("com.example.db", detail.packageName)
        assertEquals(1, detail.record.rows!!.size)
    }

    @Test
    fun `db row projection produces SqlRow`() {
        val row = CryptoRecordRow(
            id = 1L,
            recordType = RecordTypes.DATABASE,
            packageName = "com.example.db",
            kind = SqlKind.DML.name,
            operation = SqlOperation.UPDATE.name,
            algorithm = "app.db",
            sqlPreview = "UPDATE t SET a=1 WHERE id=?",
            rowCount = null,
            affectedCount = 2,
            rowId = null,
            threadName = "worker",
            timestamp = 5L,
        )
        val model = row.toRowModelOrNull()
        assertNotNull(model)
        assertTrue(model is SqlRow)
        val sql = model as SqlRow
        assertEquals(SqlKind.DML, sql.kind)
        assertEquals(SqlOperation.UPDATE, sql.operation)
        assertEquals("app.db", sql.databaseName)
        assertEquals("UPDATE t SET a=1 WHERE id=?", sql.sqlPreview)
        assertEquals(2, sql.affectedCount)
        assertNull(sql.rowCount)
        assertEquals(1L, sql.id)
    }

    @Test
    fun `db row projection drops unknown kind`() {
        val row = CryptoRecordRow(
            id = 1L,
            recordType = RecordTypes.DATABASE,
            packageName = "p",
            kind = "FUTURE_KIND",
            operation = SqlOperation.QUERY.name,
            algorithm = "a.db",
            threadName = "",
            timestamp = 0L,
        )
        assertNull(row.toRowModelOrNull())
    }

    @Test
    fun `sql toJson exports compact structured fields`() {
        val json = JSONObject(sampleSqlRecord().toJson())
        assertEquals(RecordTypes.DATABASE, json.getString("recordType"))
        assertEquals(SqlKind.QUERY.name, json.getString("kind"))
        assertEquals("app.db", json.getString("databaseName"))
        assertEquals("SELECT * FROM t WHERE id = ?", json.getString("sql"))
        val bind = json.getJSONArray("bindArgs").getJSONObject(0)
        assertEquals(1, bind.getInt("i"))
        assertEquals(SqlValueType.L.name, bind.getString("t"))
        assertEquals("7", bind.getString("v"))
        assertTrue(!bind.has("hex"))
        val row = json.getJSONArray("rows").getJSONObject(0).getJSONArray("c")
        assertEquals("id", row.getJSONObject(0).getString("n"))
        assertEquals(SqlValueType.B.name, row.getJSONObject(1).getString("t"))
        assertEquals("aabb", row.getJSONObject(1).getString("hex"))
        assertEquals(2, row.getJSONObject(1).getInt("len"))
        assertEquals(1, json.getInt("rowCount"))
        assertEquals(5L, json.getLong("contextId"))
    }
}