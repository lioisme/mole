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
        assertEquals(2, parsed.configVersion)
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
        assertEquals("xiaomi13", parsed.feature.spoofProfileId)
        assertEquals("Mi 14", parsed.feature.spoofOverrides["build.MODEL"])
        assertEquals(listOf("com.evil.su"), parsed.feature.extraRootPackages)
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
