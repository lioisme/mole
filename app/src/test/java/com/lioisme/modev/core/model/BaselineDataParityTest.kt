package com.lioisme.modev.core.model

import com.lioisme.modev.core.codec.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 * 内置数据与基线 `third_party/Guise_Reborn-main` 的**同源对账**(V49 R-3「内置数据要求完全
 * 照搬基线文件夹下的 Guise 项目」)。
 *
 * 为什么这条测试值得存在(而不是「我们抄过了」写进文档就算):
 *  - 「照搬」是可被静默违反的 —— 上游换版、本地顺手改表、生成器漏行,全都不会让任何功能变红,
 *    只会让 Modev 的内置数据悄悄偏离基线。能长期钉住它的只有**逐行对账**;
 *  - 读的是**仓库工作树里的基线真身**(不是复制进测试的常量),所以基线一旦更新而本仓未跟进,
 *    本测试当场红并点名差在哪一行。
 *
 * 纯 JVM(读文件形态沿用 `core.model.DeviceCatalogTest`)。
 */
class BaselineDataParityTest {

    // ------------------------------------------------------------------ 定位

    /** 仓库根(测试工作目录可能是根,也可能是 `app/`)。 */
    private val repoRoot: File by lazy {
        val userDir = File(System.getProperty("user.dir") ?: error("user.dir unavailable"))
        listOf(userDir, userDir.parentFile)
            .filterNotNull()
            .firstOrNull { File(it, "third_party/Guise_Reborn-main").isDirectory }
            ?: error("未定位到基线目录 third_party/Guise_Reborn-main(user.dir=$userDir)")
    }

    private fun modevAsset(name: String): File =
        File(repoRoot, "app/src/main/assets/$name").also {
            assertTrue("Modev 侧资产缺失:$it", it.isFile)
        }

    private fun baselineFile(relativePath: String): File =
        File(repoRoot, "third_party/Guise_Reborn-main/$relativePath").also {
            assertTrue("基线侧文件缺失:$it(基线被移动?对账即失去意义,须同步本路径)", it.isFile)
        }

    private fun md5(file: File): String =
        MessageDigest.getInstance("MD5").digest(file.readBytes())
            .joinToString("") { "%02x".format(it) }

    // ------------------------------------------------- ① 两本 JSON:逐字节相同

    @Test
    fun deviceAndCarrierCatalogs_areByteIdenticalToBaseline() {
        for ((modevName, baselineName) in listOf(
            "devices.json" to "app/src/main/assets/devices.json",
            "carriers.json" to "app/src/main/assets/carriers.json",
        )) {
            assertEquals(
                "两本 assets 里 $modevName 与基线不再逐字节相同(被本地改过,或上游换版未跟进)",
                md5(baselineFile(baselineName)),
                md5(modevAsset(modevName)),
            )
        }
    }

    @Test
    fun notices_areByteIdenticalToBaseline() {
        // 被改写过的 NOTICE 不能与「照搬」同时成立:上游修订号对不上,照搬就成了抄袭。
        for (name in listOf("devices.NOTICE.txt", "carriers.NOTICE.txt")) {
            assertEquals(
                "$name 与基线不同源",
                md5(baselineFile("app/src/main/assets/$name")),
                md5(modevAsset(name)),
            )
        }
    }

    // ---------------------------------------- ② presets 五张表:行集同源

    private data class PresetItem(val label: String = "", val value: String = "")

    private data class BaselinePresets(
        val androidVersions: List<PresetItem> = emptyList(),
        val densityDpi: List<PresetItem> = emptyList(),
        val networks: List<PresetItem> = emptyList(),
        val languages: List<PresetItem> = emptyList(),
    )

    private val baselinePresets: BaselinePresets by lazy {
        Json.gson.fromJson(
            baselineFile("app/src/main/res/raw/presets.json").readText(),
            BaselinePresets::class.java,
        ) ?: error("基线 presets.json 解析失败")
    }

    /** 基线每行的 `SDK → release`。 */
    private val baselineReleaseBySdk: Map<String, String> by lazy {
        baselinePresets.androidVersions.associate { item ->
            item.value.substringAfter('|') to item.value.substringBefore('|')
        }.also {
            assertEquals(
                "基线 androidVersions 自身有重复 SDK 行(本测试按 SDK 认行,重复即失去唯一性)",
                baselinePresets.androidVersions.size,
                it.size,
            )
        }
    }

    /**
     * release 字面量的**已知不照搬集**:基线表里这些行的 release 串不是该 API 级别
     * `Build.VERSION.RELEASE` 实际上报的形态(例基线写 `6`,真机 API 23 上报 `6.0`)。
     * 照搬它等于伪装成一个从不存在的上报值 —— 那本身就是一枚检测点,与「PRODUCT/BOARD/HARDWARE
     * 恒不猜」同一条理由。逐条按 SDK 登记;基线若新增此类行,本测试会因「差异未登记」而红。
     */
    private val releaseLiteralDeltas: Map<String, String> = mapOf(
        "5" to "2", "11" to "3", "14" to "4", "21" to "5",
        "23" to "6", "24" to "7", "26" to "8", "32" to "12",
    )

    @Test
    fun androidReleaseTable_coversEveryBaselineSdkLevel() {
        val mineBySdk = ProfileValueSets.androidReleases()
            .mapNotNull { release -> ProfileValueSets.sdkForRelease(release)?.let { it to release } }
            .toMap()

        assertEquals(
            "release↔SDK 表的 SDK 集与基线不一致(表头/表尾缺一档即红)",
            baselineReleaseBySdk.keys.sorted(),
            mineBySdk.keys.sorted(),
        )
        for ((sdk, baselineRelease) in baselineReleaseBySdk) {
            val ours = mineBySdk[sdk] ?: error("SDK $sdk 在本仓表中缺失")
            if (ours != baselineRelease) {
                assertEquals(
                    "SDK $sdk 的 release 与基线不同且差异未登记理由:基线=$baselineRelease 本仓=$ours",
                    baselineRelease,
                    releaseLiteralDeltas[sdk],
                )
            }
        }
        assertEquals("行数应与基线同量", baselineReleaseBySdk.size, mineBySdk.size)
        assertEquals(
            "表尾必须停在基线表尾 Android 17 / API 37(V49 D-6 反转了 V48 的 36 上限)",
            "37",
            ProfileValueSets.sdkForRelease("17"),
        )
    }

    @Test
    fun everyRowOfOurReleaseTable_isValidProfileContent() {
        // 对账之外还要证明「照搬来的每一行都存得进去」:成对写全的两字段各自过校验。
        for (release in ProfileValueSets.androidReleases()) {
            val sdk = ProfileValueSets.sdkForRelease(release) ?: error("$release 无配对 SDK")
            assertNull("release $release 过不了 ANDROID_VERSION 校验", ProfileFieldCatalog.validate(ProfileField.ANDROID_VERSION, release))
            assertNull("SDK $sdk 过不了 SDK_INT 校验", ProfileFieldCatalog.validate(ProfileField.SDK_INT, sdk))
        }
    }

    @Test
    fun densityTiers_areTheBaselineSetInBaselineOrder() {
        assertEquals(
            "密度档与基线 presets.json 不再逐行一致(此处零差异是允许的)",
            baselinePresets.densityDpi.map { it.value },
            ProfileValueSets.densities(),
        )
    }

    @Test
    fun networkSlots_matchBaselineValues() {
        val ours = listOf(
            ProfileValues.NET_NONE, ProfileValues.NET_WIFI, ProfileValues.NET_MOBILE_2G,
            ProfileValues.NET_MOBILE_3G, ProfileValues.NET_MOBILE_4G, ProfileValues.NET_MOBILE_5G,
        ).map { it.toString() }.sorted()
        assertEquals(
            "活动网络类型槽集与基线不一致",
            baselinePresets.networks.map { it.value }.sorted(),
            ours,
        )
    }

    @Test
    fun everyBaselineLanguageValue_passesOurValidator() {
        // 基线 13 条语言的 label 是单语硬编码文本(与本仓 #1 四语资源契约冲突)⇒ 有意不照搬 label;
        // 但**值**必须恒过本仓 LANGUAGE 校验,否则「照搬」在这里就是一句空话。
        assertTrue("基线语言表为空,对账失去对象", baselinePresets.languages.isNotEmpty())
        for (item in baselinePresets.languages) {
            assertNull(
                "基线语言值 ${item.value}(${item.label})过不了本仓 LANGUAGE 校验",
                ProfileFieldCatalog.validate(ProfileField.LANGUAGE, item.value),
            )
        }
    }

    // -------------------------------------- ③ 解码后可达集 = 基线扣掉非 Android 平台

    @Test
    fun decodedDeviceCatalog_isBaselineMinusNonAndroidBrands() {
        val baselineText = baselineFile("app/src/main/assets/devices.json").readText()
        val rawBaseline = Json.gson.fromJson(baselineText, DeviceCatalog::class.java)
            ?: error("基线 devices.json 解析失败")
        val baselineDecoded = parseDeviceCatalog(baselineText) ?: error("基线 devices.json 过了解码闸失败")
        val modevDecoded = parseDeviceCatalog(modevAsset("devices.json").readText())
            ?: error("随包 devices.json 解析失败")

        // 剔掉的必须恰是非 Android 品牌(剔多 = 顺手删了 Android 数据;剔少 = 苹果还在)。
        assertEquals(
            "解码闸应只剔非 Android 品牌",
            rawBaseline.brands.filterNot { it.isNonAndroidPlatform() }.map { it.key },
            baselineDecoded.brands.map { it.key },
        )
        assertTrue(
            "基线原始集里无非 Android 品牌 ⇒ 剔量口径须重估(本测试与 AndroidOnlyDataTest 一起改)",
            rawBaseline.brands.any { it.isNonAndroidPlatform() },
        )
        assertEquals(
            "基线与 Modev 的**可达集**必须整本相等(品牌、机型、逐字段)",
            baselineDecoded,
            modevDecoded,
        )
    }

    @Test
    fun carrierCount_matchesBaselineRowSet() {
        val baselineSize = parseCarrierCatalog(baselineFile("app/src/main/assets/carriers.json").readText())
            ?.size ?: error("基线 carriers.json 解析失败")
        val ours = parseCarrierCatalog(modevAsset("carriers.json").readText())
            ?.size ?: error("随包 carriers.json 解析失败")
        assertEquals("运营商条数与基线不一致", baselineSize, ours)
        assertTrue("运营商表不应静默缩水,实为 $ours", ours > 1_500)
    }
}
