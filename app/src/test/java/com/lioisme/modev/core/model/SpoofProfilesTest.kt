package com.lioisme.modev.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** V37 T2.3 伪装画像:内置档位、覆写分派与漂移回落。 */
class SpoofProfilesTest {

    @Test
    fun `unknown or null profile id falls back to auto`() {
        assertEquals(SpoofProfiles.AUTO, SpoofProfiles.resolve(null, emptyMap()).id)
        assertEquals(SpoofProfiles.AUTO, SpoofProfiles.resolve("nonexistent", emptyMap()).id)
    }

    @Test
    fun `builtin profiles expose distinct build groups`() {
        val pixel = SpoofProfiles.resolve(SpoofProfiles.PIXEL_7_PRO, emptyMap())
        val xiaomi = SpoofProfiles.resolve(SpoofProfiles.XIAOMI_13, emptyMap())
        assertEquals("Pixel 7 Pro", pixel.buildFields["MODEL"])
        assertEquals("2211133C", xiaomi.buildFields["MODEL"])
        assertEquals("qcom", pixel.roProperties["ro.hardware"])
    }

    @Test
    fun `overrides dispatch by key prefix`() {
        val profile = SpoofProfiles.resolve(
            SpoofProfiles.PIXEL_7_PRO,
            mapOf(
                "build.MODEL" to "My Phone",
                "ro.product.brand" to "mybrand",
                "telephony.imsiPrefix" to "31010",
                "wifi.ssid" to "CafeNet",
                "cpu.cores" to "12",
            ),
        )
        assertEquals("My Phone", profile.buildFields["MODEL"])
        assertEquals("mybrand", profile.roProperties["ro.product.brand"])
        assertEquals("31010", profile.imsiPrefix)
        assertEquals("CafeNet", profile.fakeSsid)
        assertEquals(12, profile.cpuCores)
    }

    @Test
    fun `invalid override values are ignored safely`() {
        val profile = SpoofProfiles.resolve(
            SpoofProfiles.PIXEL_7_PRO,
            mapOf(
                "cpu.cores" to "not-a-number",
                "build.MODEL" to "",
                "unknown.key" to "x",
            ),
        )
        assertEquals(8, profile.cpuCores)
        assertEquals("Pixel 7 Pro", profile.buildFields["MODEL"])
    }

    @Test
    fun `hookswitch extension resolves active profile`() {
        val switch = HookSwitch.DEFAULT.copy(
            feature = FeatureConfig(
                spoofProfileId = SpoofProfiles.XIAOMI_13,
                spoofOverrides = mapOf("wifi.bssid" to "AA:BB:CC:DD:EE:FF"),
            ),
        )
        val profile = switch.activeSpoofProfile()
        assertEquals(SpoofProfiles.XIAOMI_13, profile.id)
        assertEquals("AA:BB:CC:DD:EE:FF", profile.fakeBssid)
    }

    @Test
    fun `builtin list is non empty and starts with auto`() {
        assertTrue(SpoofProfiles.BUILTINS.isNotEmpty())
        assertEquals(SpoofProfiles.AUTO, SpoofProfiles.BUILTINS.first().id)
    }
}
