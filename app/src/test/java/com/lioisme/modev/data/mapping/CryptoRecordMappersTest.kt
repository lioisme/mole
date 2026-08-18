package com.lioisme.modev.data.mapping

import com.lioisme.modev.core.model.CryptoKind
import com.lioisme.modev.core.model.CryptoOperation
import com.lioisme.modev.core.model.CryptoRecord
import com.lioisme.modev.core.model.RecordTypes
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
        key = BytesView(hex = "aabb", text = null, totalLength = 2, truncated = false),
        iv = BytesView(hex = "cafebabe", text = null, totalLength = 4, truncated = false),
        keyAlias = "alias_1",
        keyExportable = false,
        input = BytesView(hex = "deadbeef", text = "secret", totalLength = 4, truncated = false),
        output = BytesView(hex = "00ff", text = null, totalLength = 2, truncated = true),
        salt = null,
        aad = BytesView(hex = "a1b2", text = null, totalLength = 2, truncated = false),
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
        assertEquals(2, entity.keyLength)
        assertEquals("alias_1", entity.keyAlias)
        assertEquals("deadbeef", entity.inputHex)
        assertEquals("secret", entity.inputText)
        assertEquals("00ff", entity.outputHex)
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
    fun `unknown enum values map to null instead of mislabeling`() {
        val entity = sampleRecord().toEntity().copy(kind = "FUTURE_KIND", operation = "HANDSHAKE")
        assertNull(entity.toModelOrNull())
    }

    @Test
    fun `row projection produces preview model`() {
        val row = CryptoRecordRow(
            id = 1L,
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
        assertEquals(CryptoKind.SIGNATURE, model!!.kind)
        assertEquals(CryptoOperation.SIGN, model.operation)
        assertEquals("deadbeef", model.key?.hex)
        assertTrue(model.key!!.truncated)
        assertEquals(4, model.key!!.totalLength)
        assertNull(model.input)
        assertEquals("worker", model.threadName)
        assertNull(model.direction)
    }

    @Test
    fun `row projection drops unknown kind`() {
        val row = CryptoRecordRow(
            id = 1L,
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
        assertEquals("secret", json.getJSONObject("input").getString("text"))
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
}