package com.lioisme.modev.core.model

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 单应用监听自定义 JSON 契约(V24 D2):
 * - 新格式 round-trip 严格对称(布尔 + kinds 键);
 * - null/空白/非法/空对象一律容错为 DEFAULT;
 * - 未知 kind 名、非法值条目忽略(防存储漂移);
 * - V23 三态遗留格式无损迁移:"ON"/"OFF" 映射 true/false,"INHERIT"/未知串取默认,
 *   kindOverrides 旧键名平滑读取,写入恒为新格式(首次读改写即收敛)。
 */
class AppListenOverrideJsonTest {

    private val gson = Gson()

    private fun parse(json: String?): AppListenOverride = AppListenOverride.fromJson(gson, json)

    @Test
    fun `roundtrip symmetry in new format`() {
        val override = AppListenOverride(
            cryptoListen = false,
            dbListen = true,
            kinds = mapOf(
                CryptoKind.CIPHER.name to false,
                SqlKind.DML.name to true,
            ),
        )
        assertEquals(override, parse(override.toJson(gson)))
    }

    @Test
    fun `null blank invalid json degrade to default`() {
        assertEquals(AppListenOverride.DEFAULT, parse(null))
        assertEquals(AppListenOverride.DEFAULT, parse("  "))
        assertEquals(AppListenOverride.DEFAULT, parse("{broken"))
        assertEquals(AppListenOverride.DEFAULT, parse("null"))
        assertEquals(AppListenOverride.DEFAULT, parse("{}"))
    }

    /** JSON 字符串构造助手:避免在测试里手写转义引号。 */
    private fun quoted(s: String): String = "\"" + s + "\""

    @Test
    fun `unknown kind names dropped`() {
        val json = "{ \"kinds\": { " +
            quoted("NOT_A_KIND") + ": false, " +
            quoted(CryptoKind.MAC.name) + ": false } }"
        assertEquals(mapOf(CryptoKind.MAC.name to false), parse(json).kinds)
    }

    @Test
    fun `non primitive values degrade to take-default`() {
        val json = "{ " +
            quoted("cryptoListen") + ": [], " +
            quoted("dbListen") + ": {}, " +
            quoted("kinds") + ": { " + quoted(SqlKind.EXEC.name) + ": { \"x\": 1 } } }"
        val parsed = parse(json)
        assertNull(parsed.cryptoListen)
        assertNull(parsed.dbListen)
        assertTrue(parsed.kinds.isEmpty())
    }

    @Test
    fun `legacy tristate master strings migrate losslessly`() {
        assertEquals(true, parse("{ \"cryptoListen\": \"ON\" }").cryptoListen)
        assertEquals(false, parse("{ \"cryptoListen\": \"OFF\" }").cryptoListen)
        val both = parse("{ \"cryptoListen\": \"OFF\", \"dbListen\": \"OFF\" }")
        assertEquals(false, both.dbListen)
        // INHERIT / 未知串 → 取默认(null)
        assertNull(parse("{ \"cryptoListen\": \"INHERIT\" }").cryptoListen)
        assertNull(parse("{ \"cryptoListen\": \"MAYBE\" }").cryptoListen)
    }

    @Test
    fun `legacy kindOverrides key and string values migrate`() {
        val json = "{ " + quoted("kindOverrides") + ": { " +
            quoted("NOT_A_KIND") + ": \"ON\", " +
            quoted(CryptoKind.MAC.name) + ": \"OFF\", " +
            quoted(CryptoKind.BASE64.name) + ": \"ON\" } }"
        assertEquals(
            mapOf(CryptoKind.MAC.name to false, CryptoKind.BASE64.name to true),
            parse(json).kinds,
        )
    }

    @Test
    fun `legacy full document migrates and rewrites to new format`() {
        val legacy = "{ " + quoted("cryptoListen") + ": \"OFF\", " +
            quoted("kindOverrides") + ": { " + quoted(CryptoKind.CIPHER.name) + ": \"OFF\" } }"
        val parsed = parse(legacy)
        assertEquals(
            AppListenOverride(cryptoListen = false, kinds = mapOf(CryptoKind.CIPHER.name to false)),
            parsed,
        )
        // 写回恒为新格式:kinds 键名 + JSON 布尔(一次读改写完成迁移收敛)
        val rewritten = parsed.toJson(gson)
        assertTrue(!rewritten.contains("kindOverrides"))
        assertTrue(!rewritten.contains("OFF"))
    }

    @Test
    fun `stable new format field names and boolean values`() {
        val json = AppListenOverride(dbListen = false).toJson(gson)
        assertTrue(json.contains(quoted("dbListen")))
        assertTrue(json.contains("false"))
        // null 位(cryptoListen)不输出;零偏差类别为空对象,无垃圾条目
        assertTrue(!json.contains(quoted("cryptoListen")))
        assertEquals(
            mapOf(CryptoKind.CIPHER.name to false),
            parse("{ " + quoted("kinds") + ": { " + quoted(CryptoKind.CIPHER.name) + ": false } }").kinds,
        )
    }

    /** V25 采集微调项:round-trip 严格对称。 */
    @Test
    fun `capture option fields roundtrip symmetrically`() {
        val override = AppListenOverride(
            logStack = false,
            maxBytesPerField = 2048,
            ignoreStackPrefixes = listOf("com.example.crypto", "kotlin."),
            maxResultRows = 50,
            maxResultChars = 1024,
        )
        assertEquals(override, parse(override.toJson(gson)))
    }

    @Test
    fun `option nulls omitted and empty prefix list normalizes to default`() {
        val json = AppListenOverride(logStack = true).toJson(gson)
        assertTrue(json.contains(quoted("logStack")))
        listOf("maxBytesPerField", "ignoreStackPrefixes", "maxResultRows", "maxResultChars")
            .forEach { key -> assertTrue("null 位不得输出:" + key, !json.contains(quoted(key))) }
        // 空前缀表与默认等价(isDefault 归零),不构成偏差
        assertTrue(AppListenOverride(ignoreStackPrefixes = emptyList()).isDefault)
    }

    @Test
    fun `option malformed values degrade to take-default`() {
        val parsed = parse(
            "{ " +
                quoted("logStack") + ": \"YES\", " +
                quoted("maxBytesPerField") + ": \"big\", " +
                quoted("ignoreStackPrefixes") + ": \"com.a\", " +
                quoted("maxResultRows") + ": {}, " +
                quoted("maxResultChars") + ": [] }",
        )
        assertNull(parsed.logStack)
        assertNull(parsed.maxBytesPerField)
        assertNull(parsed.ignoreStackPrefixes)
        assertNull(parsed.maxResultRows)
        assertNull(parsed.maxResultChars)
    }

    @Test
    fun `legacy v23 document parses without option fields`() {
        val legacy = "{ " + quoted("kindOverrides") + ": { " +
            quoted(CryptoKind.CIPHER.name) + ": \"OFF\" } }"
        val parsed = parse(legacy)
        assertEquals(mapOf(CryptoKind.CIPHER.name to false), parsed.kinds)
        assertNull(parsed.logStack)
        assertNull(parsed.maxBytesPerField)
        assertNull(parsed.ignoreStackPrefixes)
    }
}
