package com.lioisme.modev.core.model

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AppListenOverride JSON 往返与容错(V23 D2):
 * 往返对称、缺字段补 INHERIT、非法 JSON/未知 kind/非法三态名逐项容错、isDefault 语义。
 */
class AppListenOverrideJsonTest {

    private val gson = Gson()

    @Test
    fun `round trip preserves all fields`() {
        val origin = AppListenOverride(
            cryptoListen = TriState.ON,
            dbListen = TriState.OFF,
            kindOverrides = mapOf(
                CryptoKind.CIPHER.name to TriState.OFF,
                SqlKind.DML.name to TriState.ON,
            ),
        )
        val parsed = AppListenOverride.fromJson(gson, origin.toJson(gson))
        assertEquals(origin, parsed)
    }

    @Test
    fun `null blank and invalid json fall back to default`() {
        assertEquals(AppListenOverride.DEFAULT, AppListenOverride.fromJson(gson, null))
        assertEquals(AppListenOverride.DEFAULT, AppListenOverride.fromJson(gson, ""))
        assertEquals(AppListenOverride.DEFAULT, AppListenOverride.fromJson(gson, "   "))
        assertEquals(AppListenOverride.DEFAULT, AppListenOverride.fromJson(gson, "{not-json"))
    }

    @Test
    fun `missing fields default to inherit`() {
        val parsed = AppListenOverride.fromJson(gson, "{}")
        assertEquals(AppListenOverride.DEFAULT, parsed)
        assertTrue(parsed.isDefault)
    }

    @Test
    fun `unknown kind names are dropped`() {
        val json = """{"kindOverrides":{"NOT_A_KIND":"ON","${CryptoKind.MAC.name}":"OFF"}}"""
        val parsed = AppListenOverride.fromJson(gson, json)
        assertEquals(mapOf(CryptoKind.MAC.name to TriState.OFF), parsed.kindOverrides)
    }

    @Test
    fun `invalid tri-state values degrade to inherit`() {
        val json = """{"cryptoListen":"MAYBE","kindOverrides":{"${SqlKind.EXEC.name}":"SOMETIMES"}}"""
        val parsed = AppListenOverride.fromJson(gson, json)
        assertEquals(TriState.INHERIT, parsed.cryptoListen)
        assertTrue(parsed.isDefault)
    }

    @Test
    fun `normalized drops inherit kind entries`() {
        val override = AppListenOverride(
            kindOverrides = mapOf(
                CryptoKind.CIPHER.name to TriState.INHERIT,
                CryptoKind.BASE64.name to TriState.ON,
            ),
        )
        assertFalse(override.isDefault)
        val normalized = override.normalized()
        assertEquals(mapOf(CryptoKind.BASE64.name to TriState.ON), normalized.kindOverrides)
    }

    @Test
    fun `all-inherit normalizes to default`() {
        val override = AppListenOverride(
            kindOverrides = mapOf(CryptoKind.CIPHER.name to TriState.INHERIT),
        ).normalized()
        assertTrue(override.isDefault)
        assertEquals(AppListenOverride.DEFAULT, override)
    }

    @Test
    fun `master-only override is not default`() {
        assertFalse(AppListenOverride(cryptoListen = TriState.OFF).isDefault)
        assertFalse(AppListenOverride(dbListen = TriState.ON).isDefault)
    }

    @Test
    fun `json serialization uses stable field names`() {
        val json = AppListenOverride(dbListen = TriState.ON).toJson(gson)
        assertTrue(json.contains("\"dbListen\":\"ON\""))
        assertFalse(json.contains("dbOpen"))
    }
}
