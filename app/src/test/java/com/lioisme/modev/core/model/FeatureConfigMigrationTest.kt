package com.lioisme.modev.core.model

import com.google.gson.Gson
import com.lioisme.modev.core.codec.WireProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V37 T1.2/T1.3 配置 JSON 兼容矩阵:
 * 旧扁平文档读入即迁移、新 feature 段读入即用、写出一律新格式;
 * wire 信封 VERSION 恒 4 不变(feature 随 switch 字段透传)。
 */
class FeatureConfigMigrationTest {

    private val gson = Gson()

    @Test
    fun `v1 flat dialogKeywords migrates into feature section`() {
        val v1Json = """{"configVersion":1,"dialogKeywords":["升级","update"],"uiListen":true,"dialogKeyword":true}"""
        val parsed = HookSwitch.fromJson(gson, v1Json)!!
        assertEquals("V47 T2 起当前版本为 3(画像库主键体系)", 3, parsed.configVersion)
        assertEquals(listOf("升级", "update"), parsed.feature.dialogKeywords)
        val rules = parsed.activeKeywordRules
        assertEquals(2, rules.size)
        assertEquals(KeywordMatchMode.CONTAINS, rules.first().matchMode)
    }

    @Test
    fun `v2 feature section parses directly`() {
        val v2Json = """
            {"configVersion":2,"feature":{"dialogKeywordRules":[{"text":"risk","matchMode":"REGEX"}],"spoofProfileId":"xiaomi13","spoofOverrides":{"build.MODEL":"Mi 14"},"extraRootPackages":["com.evil.su"]}}
        """.trimIndent()
        val parsed = HookSwitch.fromJson(gson, v2Json)!!
        assertEquals(listOf("risk"), parsed.feature.dialogKeywordRules.map { it.text })
        assertEquals(KeywordMatchMode.REGEX, parsed.feature.dialogKeywordRules.first().matchMode)
        assertEquals(
            "旧档位名必须映射到画像库主键(否则升级即静默失效)",
            BuiltinDeviceProfiles.idFor(BuiltinDeviceProfiles.XIAOMI_13),
            parsed.feature.spoofProfileId,
        )
        assertEquals("Mi 14", parsed.feature.spoofOverrides["build.MODEL"])
        assertEquals(listOf("com.evil.su"), parsed.feature.extraRootPackages)
    }

    // ---- V47 T2:configVersion 3 的画像主键迁移矩阵 ----

    @Test
    fun `legacy auto profile migrates to unbound plus adaptive masking, never to a fixed device`() {
        val legacy = """
            {"configVersion":2,"feature":{"spoofProfileId":"auto"}}
        """.trimIndent()
        val parsed = HookSwitch.fromJson(gson, legacy)!!
        assertEquals("auto 的条件档语义不得升级为固定机型", "", parsed.feature.spoofProfileId)
        assertTrue("auto 的自适应掩蔽语义必须由 adaptiveMask 承接,不得凭空丢失", parsed.feature.adaptiveMask)
    }

    @Test
    fun `legacy pixel profile maps to its builtin counterpart`() {
        val legacy = """
            {"configVersion":2,"feature":{"spoofProfileId":"pixel7pro"}}
        """.trimIndent()
        val parsed = HookSwitch.fromJson(gson, legacy)!!
        assertEquals(BuiltinDeviceProfiles.idFor(BuiltinDeviceProfiles.PIXEL_7_PRO), parsed.feature.spoofProfileId)
        assertTrue("机型档迁移不得顺带打开自适应档", !parsed.feature.adaptiveMask)
    }

    @Test
    fun `unknown legacy profile id is preserved so resolve can report it`() {
        val legacy = """
            {"configVersion":2,"feature":{"spoofProfileId":"nexus-9"}}
        """.trimIndent()
        val parsed = HookSwitch.fromJson(gson, legacy)!!
        assertEquals(
            "未知旧值不得被吞成空或猜成某档 —— 必须原样交给 resolve 报 Missing(UI 可见)",
            "nexus-9",
            parsed.feature.spoofProfileId,
        )
    }

    @Test
    fun `v3 config is never re-migrated so a bound library id survives round trip`() {
        val bound = BuiltinDeviceProfiles.idFor(BuiltinDeviceProfiles.GALAXY_S23_ULTRA)
        val current = """
            {"configVersion":3,"feature":{"spoofProfileId":"$bound","adaptiveMask":true}}
        """.trimIndent()
        val parsed = HookSwitch.fromJson(gson, current)!!
        assertEquals(bound, parsed.feature.spoofProfileId)
        assertTrue(parsed.feature.adaptiveMask)
        assertEquals(3, parsed.configVersion)
    }

    @Test
    fun `expanded device profile snapshot round trips through the feature section`() {
        val profile = ProfileField.BRAND.withText(
            ProfileField.MODEL.withText(DeviceProfile.EMPTY, "Pixel 7 Pro"),
            "google",
        )
        val written = gson.toJson(HookSwitch.DEFAULT.copy(feature = FeatureConfig(deviceProfile = profile)))
        val parsed = HookSwitch.fromJson(gson, written)!!
        assertEquals(profile, parsed.feature.deviceProfile)
        assertEquals("google", parsed.feature.deviceProfile.brand)
    }

    @Test
    fun `empty snapshot deserializes to empty profile and never to null`() {
        val parsed = HookSwitch.fromJson(gson, """{"configVersion":3}""")!!
        assertEquals(DeviceProfile.EMPTY, parsed.feature.deviceProfile)
        assertTrue(parsed.feature.deviceProfile.isEmpty)
        assertTrue("缺省即不伪装:新字段不得把默认配置变成有偏差", !parsed.feature.adaptiveMask)
    }

    @Test
    fun `legacy zero-version json still resets intervention flags`() {
        val legacy = """{"sslContext":true,"rootFile":true,"dialogKeywords":["old"]}"""
        val parsed = HookSwitch.fromJson(gson, legacy)!!
        assertTrue("v0 迁移:干预位强制关", !parsed.sslContext && !parsed.rootFile)
        assertEquals(listOf("old"), parsed.feature.dialogKeywords)
    }

    @Test
    fun `round trip preserves feature section`() {
        val original = HookSwitch.DEFAULT.copy(
            feature = FeatureConfig(
                dialogKeywordRules = listOf(KeywordRule("a", KeywordMatchMode.EQUALS)),
                spoofProfileId = SpoofProfiles.XIAOMI_13,
                spoofOverrides = mapOf("wifi.ssid" to "Home"),
                extraFridaPorts = listOf(12345),
            ),
        )
        val parsed = HookSwitch.fromJson(gson, gson.toJson(original))!!
        assertEquals(original.feature, parsed.feature)
    }

    @Test
    fun `wire envelope version stays 4`() {
        assertEquals(4, WireProtocol.VERSION)
    }
}
