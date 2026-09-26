package com.lioisme.modev.core.model

import com.lioisme.modev.core.codec.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 * 内置数据与基线 Guise Reborn 的**同源对账**。
 *
 * 为什么这条测试值得存在(而不是「我们抄过了」写进文档就算):
 *  「照搬」是可被静默违反的 —— 上游换版、本地顺手改表、生成器漏行,全都不会让任何功能变红,
 *  只会让 Modev 的内置数据悄悄偏离基线。能长期钉住它的只有逐行/逐字节对账。
 *
 * 对账的两半各由什么承载:
 *  - **逐字节那半**(两本 JSON + 两份 NOTICE):由 `app/src/test/resources/baseline/guise-reborn.lock.json`
 *    里的摘要锁钉住。锁文件是**签核过的依赖清单**(同 `gradle.lockfile` 的地位),被跟踪 ⇒ 任何克隆都能跑;
 *    本仓不再为了跑测试而要求工作树里存在一份 1.9 MB 的上游副本 —— 那份副本连同 NC 数据被复制进仓库
 *    正是它不该有的分发形态。
 *  - **行集那半**(presets 的四张表):只锁**对账真正需要的行**(SDK↔release / 密度 / 网络槽 / 语言),
 *    同样落在锁文件里。整本 presets.json 不复制,因为它含基线自有文本。
 *
 * 本机若真的拉了基线(`third_party/Guise_Reborn-main`,按仓库惯例不入库),
 * [lock_matchesBaselineOnDisk_whenBaselineIsPresent] 会再验一次「锁 == 基线真身」,
 * 使锁文件本身也无法悄悄偏离它声称钉住的那份数据。
 *
 * 更新基线时:改 NOTICE(修订号/出处)→ 重算摘要写进锁文件 → 本测试绿。
 * 生成命令(在仓库根):
 *   sha256sum third_party/Guise_Reborn-main/app/src/main/assets/{devices,carriers}.{json,NOTICE.txt} \
 *             third_party/Guise_Reborn-main/app/src/main/res/raw/presets.json
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
            .firstOrNull { File(it, "app/src/main/assets/devices.json").isFile }
            ?: error("未定位到仓库根(user.dir=$userDir 及其父目录下都没有 app/src/main/assets/devices.json)")
    }

    /** 随包资产(Modev 侧,被跟踪 ⇒ 任何克隆都在)。 */
    private fun modevFile(relativePath: String): File =
        File(repoRoot, "app/src/main/$relativePath").also {
            assertTrue("Modev 侧资产缺失:$it", it.isFile)
        }

    /** 基线真身,仅在本机拉过基线时存在;缺席不判红(判据在锁文件里)。 */
    private val baselineRoot: File? by lazy {
        val userDir = File(System.getProperty("user.dir") ?: "")
        listOf(userDir, userDir.parentFile)
            .filterNotNull()
            .map { File(it, "third_party/Guise_Reborn-main/app/src/main") }
            .firstOrNull { it.isDirectory }
    }

    private fun digest(file: File): String =
        MessageDigest.getInstance("SHA-256").digest(file.readBytes())
            .joinToString("") { "%02x".format(it) }

    // ---------------------------------------------------------------- 摘要锁

    private data class PresetItem(val label: String, val value: String)

    private data class BaselinePresets(
        val androidVersions: List<PresetItem>,
        val densityDpi: List<PresetItem>,
        val networks: List<PresetItem>,
        val languages: List<PresetItem>,
    )

    private data class BaselineLock(val files: Map<String, String>, val presets: BaselinePresets)

    private val lock: BaselineLock by lazy {
        val text = checkNotNull(
            javaClass.getResourceAsStream("/baseline/guise-reborn.lock.json")
                ?.bufferedReader()?.readText(),
        ) { "摘要锁 baseline/guise-reborn.lock.json 不在测试类路径上(被移动过?对账即失去对象)" }
        Json.gson.fromJson(text, BaselineLock::class.java) ?: error("摘要锁解析失败")
    }

    /** 锁里钉住的五个基线文件。 */
    private val lockedFiles = listOf(
        "assets/devices.json",
        "assets/carriers.json",
        "assets/devices.NOTICE.txt",
        "assets/carriers.NOTICE.txt",
        "res/raw/presets.json",
    )

    @Test
    fun lockCoversEveryClaimedFile() {
        assertEquals(
            "锁文件的键集与本测试声明要对账的文件集不一致(新增资产必须同批登记,否则钉不到)",
            lockedFiles.sorted(),
            lock.files.keys.sorted(),
        )
    }

    @Test
    fun lock_matchesBaselineOnDisk_whenBaselineIsPresent() {
        val root = baselineRoot ?: return Unit.also {
            // 缺席不是失败:基线不入库,而判据(锁)已被上面的字节对账钉住。
        }
        for (rel in lockedFiles) {
            val file = File(root, rel)
            assertTrue("基线目录在场但 $rel 缺失(被移动?锁与真身必须同步)", file.isFile)
            assertEquals("锁里的 $rel 摘要与基线真身不符(锁过期了)", lock.files.getValue(rel), digest(file))
        }
    }

    // ------------------------------------------------- ① 两本 JSON + 两份 NOTICE:逐字节

    @Test
    fun deviceAndCarrierCatalogs_matchLockedBaselineDigest() {
        for (rel in listOf("assets/devices.json", "assets/carriers.json", "assets/devices.NOTICE.txt", "assets/carriers.NOTICE.txt")) {
            assertEquals(
                "$rel 与锁定的基线摘要不再相同(被本地改过,或上游换版未跟进 → 先更新 NOTICE 再更新锁)",
                lock.files.getValue(rel),
                digest(modevFile(rel)),
            )
        }
    }

    // ---------------------------------------- ② presets 四张表:行集同源

    /** 基线每行的 `SDK → release`。 */
    private val baselineReleaseBySdk: Map<String, String> by lazy {
        lock.presets.androidVersions.associate { item ->
            item.value.substringAfter('|') to item.value.substringBefore('|')
        }.also {
            assertEquals(
                "基线 androidVersions 自身有重复 SDK 行(本测试按 SDK 认行,重复即失去唯一性)",
                lock.presets.androidVersions.size,
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
            "密度档与基线 presets 不再逐行一致(此处零差异是允许的)",
            lock.presets.densityDpi.map { it.value },
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
            lock.presets.networks.map { it.value }.sorted(),
            ours,
        )
    }

    @Test
    fun everyBaselineLanguageValue_passesOurValidator() {
        // 基线 13 条语言的 label 是单语硬编码文本(与本仓 #1 四语资源契约冲突)⇒ 有意不照搬 label;
        // 但**值**必须恒过本仓 LANGUAGE 校验,否则「照搬」在这里就是一句空话。
        assertTrue("基线语言表为空,对账失去对象", lock.presets.languages.isNotEmpty())
        for (item in lock.presets.languages) {
            assertNull(
                "基线语言值 ${item.value}(${item.label})过不了本仓 LANGUAGE 校验",
                ProfileFieldCatalog.validate(ProfileField.LANGUAGE, item.value),
            )
        }
    }

    // -------------------------------------- ③ 解码后可达集 = 原始集扣掉非 Android 平台

    @Test
    fun decodedDeviceCatalog_dropsExactlyTheNonAndroidBrands() {
        // 字节对账已把随包 devices.json 钉成「与基线同一份数据」,故可达集只需与**它自己**的原始集比:
        // 剔掉的必须恰是非 Android 品牌(剔多 = 顺手删了 Android 数据;剔少 = 苹果还在)。
        val text = modevFile("assets/devices.json").readText()
        val raw = Json.gson.fromJson(text, DeviceCatalog::class.java) ?: error("devices.json 解析失败")
        val decoded = parseDeviceCatalog(text) ?: error("devices.json 过了解码闸失败")

        assertTrue(
            "数据里无非 Android 品牌 ⇒ 剔量口径须重估(本测试与 AndroidOnlyDataTest 一起改)",
            raw.brands.any { it.isNonAndroidPlatform() },
        )
        assertEquals(
            "解码闸应只剔非 Android 品牌",
            raw.brands.filterNot { it.isNonAndroidPlatform() }.map { it.key },
            decoded.brands.map { it.key },
        )
    }

    @Test
    fun carrierCatalog_parsesToTheLockedRowSet() {
        // 条数只防「静默缩水」这一种事故;与基线的等价性由 [deviceAndCarrierCatalogs_matchLockedBaselineDigest] 的字节对账承担。
        val ours = parseCarrierCatalog(modevFile("assets/carriers.json").readText())
            ?.size ?: error("随包 carriers.json 解析失败")
        assertTrue("运营商表不应静默缩水,实为 $ours", ours > 1_500)
    }
}
