package com.lioisme.modev.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/**
 * 「Android 产品不应有苹果内置库」(V49 R-3)的可达面扫描。
 *
 * 判据是**可达即违规**,不是「资产文件里没有」:上游 MobileModels 收的是全平台机型目录,
 * 资产照搬就不能改文件(V49 D-5),于是本测试逐面扫四个出口 —— 解码后的品牌集、机型选择器的
 * 每一段呈现串、9 个内置画像档的全部字段值、随机源 2000 种子的产出。
 * 只在解码口下一道谓词([DeviceBrandEntry.isNonAndroidPlatform])而四处都扫,是因为
 * 「屏上看不见但选得到」正是这类违规的存活形态(缓存的旧目录、MCP 目录工具、随机派生
 * 都不经过选择器 UI)。
 *
 * 纯 JVM(读资产形态沿用 `DeviceCatalogTest`)。
 */
class AndroidOnlyDataTest {

    private val assetRelative = "src/main/assets"

    private fun asset(name: String): File {
        val userDir = System.getProperty("user.dir") ?: error("user.dir unavailable")
        return listOf(
            File(userDir, "$assetRelative/$name"),
            File(userDir, "app/$assetRelative/$name"),
            File(userDir, "../app/$assetRelative/$name"),
        ).firstOrNull { it.isFile }
            ?: error("随包资产未定位($name, user.dir=$userDir)")
    }

    /**
     * 违规标记。两条**真实数据教出来的**豁免,不留隐式规则:
     *  - `pineapple` 是高通骁龙 8 Gen 3 的板级代号(`BuiltinDeviceProfiles` 的
     *    xiaomi-14-ultra 档 `board` 就是它),先摘掉这个词再扫 `apple`;
     *  - **不扫 `darwin`**:它是锤子(smartisan 的 `hardware=darwin`)与荣耀(`DarwinA` 机型代号)
     *    的 Android 平台代号,扫它会把真 Android 数据判成苹果痕迹(V49 WP-B 首轮实踩两条)。
     */
    private val appleTokens = listOf(
        "iphone", "ipad", "ipod", "appletv", "apple watch", "apple tv", "airpods",
        "watchos", "tvos", "iphoneos", "macintosh", "mac os", "mac-",
    )

    private fun String.appleHit(): String? {
        val lowered = lowercase().replace("pineapple", "")
        return (appleTokens + listOf("apple")).firstOrNull { lowered.contains(it) }
    }

    private fun assertClean(label: String, value: String) {
        val hit = value.appleHit()
        if (hit != null) error("$label 命中非 Android 平台标记 \"$hit\":$value")
    }

    private val bundled: DeviceCatalog by lazy {
        parseDeviceCatalog(asset("devices.json").readText())
            ?: error("随包 devices.json 解析失败")
    }

    // ------------------------------------------------------------ ① 品牌与机型集

    @Test
    fun appleBrand_isNotReachableAfterDecoding() {
        assertTrue(
            "解码后仍有非 Android 品牌:${bundled.brands.filter { it.isNonAndroidPlatform() }.map { it.key }}",
            bundled.brands.none { it.isNonAndroidPlatform() },
        )
        // 精确数「剔了多少」:剔少了 = 苹果还在;剔多了 = 顺手删掉了 Android 数据。
        assertEquals("可达品牌数(基线 26 减 apple)", 25, bundled.brands.size)
        assertEquals("可达机型数(基线 8533 减 apple 458)", 8_075, bundled.brands.sumOf { it.models.size })
        assertTrue("apple" !in bundled.brands.map { it.key.lowercase() })
    }

    @Test
    fun everyReachableModelRecord_isFreeOfAppleIdentifiers() {
        // 覆盖 model / 市场名 / device / code / alias / versionName 与品牌四段身份串,
        // 即选择器可能写进画像的每一段值。
        for (brand in bundled.brands) {
            assertClean("品牌 ${brand.key}.key", brand.key)
            assertClean("品牌 ${brand.key}.displayName", brand.displayName)
            assertClean("品牌 ${brand.key}.buildBrand", brand.buildBrand)
            assertClean("品牌 ${brand.key}.manufacturer", brand.manufacturer)
            for (model in brand.models) {
                assertClean("${brand.key}.model", model.model)
                assertClean("${brand.key}.name", model.name)
                assertClean("${brand.key}.device", model.device)
                assertClean("${brand.key}.code", model.code.orEmpty())
                assertClean("${brand.key}.alias", model.alias.orEmpty())
                assertClean("${brand.key}.versionName", model.versionName.orEmpty())
                assertClean("${brand.key}.label()", model.label())
                assertClean("${brand.key}.resolvedDevice()", model.resolvedDevice())
            }
        }
    }

    // ------------------------------------------------------ ② 内置画像档的字段值

    @Test
    fun builtinProfiles_carryNoAppleValues() {
        assertEquals("内置档数量变了须同时复核本扫描", 9, BuiltinDeviceProfiles.BUILTINS.size)
        for (spec in BuiltinDeviceProfiles.BUILTINS) {
            for (field in ProfileField.entries) {
                assertClean("${spec.seedId}.${field.name}", field.stringOf(spec.profile))
            }
        }
    }

    // ------------------------------------------------------------ ③ 随机源产出

    @Test
    fun generators_neverEmitAppleValues() {
        val random = Random(20260924)
        val releases = ProfileValueSets.androidReleases()
        val brands = bundled.brands

        // 每个 release 的 Build ID 前缀表都走一遍(前缀表按版本分支,漏档就扫不到)。
        for (release in releases) {
            assertClean("buildId($release)", ProfileGenerators.randomBuildId(release, random))
        }
        // 指纹 = 品牌三段 + 版本 + Build ID 的合成出口,拿真目录里的真实身份去喂它。
        repeat(2_000) {
            assertClean("mac", ProfileGenerators.randomMacAddress(random))
            assertClean("imei", ProfileGenerators.randomImei(random))
            assertClean("androidId", ProfileGenerators.randomAndroidId(random))
            assertClean("phoneNum", ProfileGenerators.randomPhoneNum(random))
            assertClean("battery", ProfileGenerators.randomBatteryLevel(random).toString())
            val brand = brands[random.nextInt(brands.size)]
            val sample = brand.models[random.nextInt(brand.models.size)]
            val release = releases[random.nextInt(releases.size)]
            assertClean(
                "fingerprint(${brand.key})",
                ProfileGenerators.deriveFingerprint(
                    brand = brand.buildBrand,
                    product = sample.resolvedDevice(),
                    device = sample.resolvedDevice(),
                    release = release,
                    buildId = ProfileGenerators.randomBuildId(release, random),
                    random = random,
                ),
            )
        }
    }

    // ---------------------------------------------- ④ 遗留档(未退役前的现网值源)

    @Test
    fun legacySpoofProfileConstants_carryNoAppleValues() {
        // 遗留通路要到 WP-D6 才退役,退役前它是**现网实际生效**的值源 ⇒ 必须同扫描。
        for (profile in SpoofProfiles.BUILTINS) {
            for ((key, value) in profile.buildFields + profile.roProperties) {
                assertClean("legacy ${profile.id}.$key", value)
            }
            assertClean("legacy ${profile.id}.fakeSsid", profile.fakeSsid)
            assertClean("legacy ${profile.id}.fakeBssid", profile.fakeBssid)
        }
    }
}
