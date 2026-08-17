package com.lioisme.modev.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HookSwitchTest {

    @Test
    fun default_any_enabled() {
        assertTrue(HookSwitch.DEFAULT.anyEnabled)
    }

    @Test
    fun all_off_any_disabled() {
        val s = HookSwitch(
            messageDigest = false, cipher = false, mac = false, signature = false,
            keyParam = false, keyGeneration = false, keyFactory = false,
            keyStore = false, keyAgreement = false, random = false, base64 = false,
        )
        assertFalse(s.anyEnabled)
    }

    @Test
    fun new_domains_on_by_default() {
        assertTrue(HookSwitch.DEFAULT.enabled(CryptoKind.KEY_STORE))
        assertTrue(HookSwitch.DEFAULT.enabled(CryptoKind.KEY_AGREEMENT))
        assertTrue(HookSwitch.DEFAULT.enabled(CryptoKind.RANDOM))
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
        )
        assertFalse(allOff.anyEnabled)
    }

    @Test
    fun copy_preserves_unknown() {
        val s = HookSwitch(maxStackDepth = 5).copy(cipher = false)
        assertEquals(5, s.maxStackDepth)
    }
}