package com.lioisme.modev.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HookSwitchTest {

    @Test
    fun default_any_enabled() {
        assertTrue(HookSwitch.DEFAULT.anyEnabled)
        assertTrue(HookSwitch.DEFAULT.anyCryptoEnabled)
        assertTrue(HookSwitch.DEFAULT.anyDbEnabled)
    }

    @Test
    fun all_off_any_disabled() {
        val s = HookSwitch(
            messageDigest = false, cipher = false, mac = false, signature = false,
            keyParam = false, keyGeneration = false, keyFactory = false,
            keyStore = false, keyAgreement = false, random = false, base64 = false,
            webView = false,
            dbOpen = false, dbQuery = false, dbDml = false, dbExec = false,
            dbTransaction = false,
        )
        assertFalse(s.anyEnabled)
        assertFalse(s.anyCryptoEnabled)
        assertFalse(s.anyDbEnabled)
    }

    @Test
    fun db_only_enables_only_db_projection() {
        val s = HookSwitch(
            messageDigest = false, cipher = false, mac = false, signature = false,
            keyParam = false, keyGeneration = false, keyFactory = false,
            keyStore = false, keyAgreement = false, random = false, base64 = false,
            webView = false,
            dbOpen = false, dbQuery = true, dbDml = false, dbExec = false,
            dbTransaction = false,
        )
        assertTrue(s.anyDbEnabled)
        assertFalse(s.anyCryptoEnabled)
        assertTrue(s.anyEnabled)
    }

    @Test
    fun crypto_only_enables_only_crypto_projection() {
        val s = HookSwitch(
            cipher = true,
            dbOpen = false, dbQuery = false, dbDml = false, dbExec = false,
            dbTransaction = false,
        )
        assertTrue(s.anyCryptoEnabled)
        assertFalse(s.anyDbEnabled)
        assertTrue(s.anyEnabled)
    }

    @Test
    fun new_domains_on_by_default() {
        assertTrue(HookSwitch.DEFAULT.enabled(CryptoKind.KEY_STORE))
        assertTrue(HookSwitch.DEFAULT.enabled(CryptoKind.KEY_AGREEMENT))
        assertTrue(HookSwitch.DEFAULT.enabled(CryptoKind.RANDOM))
    }

    @Test
    fun db_categories_on_by_default() {
        for (kind in SqlKind.entries) {
            assertTrue("db category $kind should default on", HookSwitch.DEFAULT.dbEnabled(kind))
        }
    }

    @Test
    fun base64_off_by_default() {
        assertFalse(HookSwitch.DEFAULT.enabled(CryptoKind.BASE64))
        assertTrue(HookSwitch.DEFAULT.enabled(CryptoKind.CIPHER))
        assertTrue(HookSwitch.DEFAULT.enabled(CryptoKind.MESSAGE_DIGEST))
    }

    @Test
    fun enabled_maps_each_kind() {
        val s = HookSwitch(
            messageDigest = true, cipher = false, mac = true, signature = false,
            keyParam = true, keyGeneration = false, keyFactory = true, base64 = true,
        )
        assertTrue(s.enabled(CryptoKind.MESSAGE_DIGEST))
        assertFalse(s.enabled(CryptoKind.CIPHER))
        assertTrue(s.enabled(CryptoKind.MAC))
        assertFalse(s.enabled(CryptoKind.SIGNATURE))
        assertTrue(s.enabled(CryptoKind.KEY_PARAM))
        assertFalse(s.enabled(CryptoKind.KEY_GENERATION))
        assertTrue(s.enabled(CryptoKind.KEY_FACTORY))
        assertTrue(s.enabled(CryptoKind.BASE64))
    }

    @Test
    fun db_enabled_maps_each_kind() {
        val s = HookSwitch(
            dbOpen = true, dbQuery = false, dbDml = true, dbExec = false,
            dbTransaction = true,
        )
        assertTrue(s.dbEnabled(SqlKind.OPEN))
        assertFalse(s.dbEnabled(SqlKind.QUERY))
        assertTrue(s.dbEnabled(SqlKind.DML))
        assertFalse(s.dbEnabled(SqlKind.EXEC))
        assertTrue(s.dbEnabled(SqlKind.TRANSACTION))
    }

    @Test
    fun webview_off_by_default() {
        assertFalse(HookSwitch.DEFAULT.enabled(CryptoKind.WEB_CRYPTO))
        assertFalse(HookSwitch.DEFAULT.webView)
    }

    @Test
    fun webview_enabled_maps_kind_and_any() {
        val s = HookSwitch(webView = true)
        assertTrue(s.enabled(CryptoKind.WEB_CRYPTO))
        assertTrue(s.anyEnabled)
        val allOff = HookSwitch(
            messageDigest = false, cipher = false, mac = false, signature = false,
            keyParam = false, keyGeneration = false, keyFactory = false,
            keyStore = false, keyAgreement = false, random = false, base64 = false,
            webView = false,
            dbOpen = false, dbQuery = false, dbDml = false, dbExec = false,
            dbTransaction = false,
        )
        assertFalse(allOff.anyEnabled)
    }

    @Test
    fun result_caps_have_sane_defaults() {
        assertEquals(RecordLimits.DEFAULT_MAX_RESULT_ROWS, HookSwitch.DEFAULT.maxResultRows)
        assertEquals(RecordLimits.DEFAULT_MAX_RESULT_CHARS, HookSwitch.DEFAULT.maxResultChars)
    }

    @Test
    fun copy_preserves_unknown() {
        val s = HookSwitch(maxStackDepth = 5).copy(cipher = false)
        assertEquals(5, s.maxStackDepth)
        assertTrue(s.dbQuery)
    }
}