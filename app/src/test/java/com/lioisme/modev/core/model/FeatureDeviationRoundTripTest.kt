package com.lioisme.modev.core.model

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** V37 T1.2 功能域偏差:JSON 读写互逆 + resolve 叠加语义。 */
class FeatureDeviationRoundTripTest {

    private val gson = Gson()

    @Test
    fun `feature deviations survive json round trip`() {
        val override = AppListenOverride(
            dialogKeywords = listOf("k1"),
            spoofProfileId = SpoofProfiles.XIAOMI_13,
            spoofOverrides = mapOf("build.MODEL" to "Custom"),
            extraRootPackages = listOf("com.foo"),
            extraXposedPrefixes = listOf("com.bar."),
            extraFridaPorts = listOf(9999),
            extraMaskKeywords = listOf("hook"),
            keywordIds = listOf(1L, 2L),
        )
        val parsed = AppListenOverride.fromJson(gson, override.toJson(gson))
        assertEquals(override, parsed)
        assertTrue(!parsed.isDefault)
    }

    @Test
    fun `empty feature deviations parse to default`() {
        val parsed = AppListenOverride.fromJson(gson, """{"spoofOverrides":{},"extraFridaPorts":[],"keywordIds":[]}""")
        assertTrue(parsed.isDefault)
        assertNull(parsed.spoofProfileId)
    }

    @Test
    fun `resolve overlays feature deviations onto default`() {
        val override = AppListenOverride(
            dialogKeywords = listOf("risk"),
            spoofProfileId = SpoofProfiles.PIXEL_7_PRO,
            extraRootPackages = listOf("com.extra"),
        )
        val effective = EffectiveSwitch.resolve(HookSwitch.DEFAULT, override)
        assertEquals(listOf("risk"), effective.feature.dialogKeywords)
        assertEquals(SpoofProfiles.PIXEL_7_PRO, effective.feature.spoofProfileId)
        assertEquals(listOf("com.extra"), effective.feature.extraRootPackages)
    }

    @Test
    fun `resolve without feature deviations keeps default feature section`() {
        val effective = EffectiveSwitch.resolve(HookSwitch.DEFAULT, AppListenOverride(cryptoListen = false))
        assertEquals(FeatureConfig(), effective.feature)
    }
}
