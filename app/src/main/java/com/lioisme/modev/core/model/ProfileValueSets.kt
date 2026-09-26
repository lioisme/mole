package com.lioisme.modev.core.model

/*
 * 画像字段的标准取值集:Android release ↔ SDK、屏幕密度档、CPU 核数档。
 *
 * 为什么这三张表留在仓里而不是找「平台权威表」:语言 / 国家 / 时区那三项平台真的给了枚举
 * (`ProfileOptionSource` 走 Locale/TimeZone 表,禁再抄一份),而 **Android release 与 API level
 * 的对应关系、以及 OEM 实际使用的 dpi 档位,平台侧没有可枚举 API**(`Build.VERSION_CODES` 是常量、
 * 不是"历史 release↔sdk 表",且拿不到对端要写的值)。这就是事实数据,不是第二份清单。
 *
 * 三条铁律:
 *  1. **release 与 sdk 成对写**(见 [sdkForRelease]):只改一个就会存出一台从不存在的设备
 *     (Android 14 配 API 31),而那本身就是一枚检测点 —— 与本仓「PRODUCT/BOARD/HARDWARE 恒不猜」
 *     同一条理由。
 *  2. 档位值恒过 [ProfileFieldCatalog.validate](`ProfileValueSetsTest` 逐值断言),
 *     否则选择器会把用户推向「选了却报非法」。
 *  3. **表尾恒与基线逐行同源**(基线 `res/raw/presets.json` androidVersions = 37 行,
 *     表尾停在 SDK 37 / Android 17)。V48 曾定档「只到本机理解的最高 API 36、不预告未来版本」,
 *     V49 R-3「内置数据要求完全照搬基线」把它反转:**照搬**优先于本机 SDK 上下限
 *     (`BaselineDataParityTest` 逐行对账,少一行即红)。选到高于本机认得的版本不影响真实设备
 *     上报形态,但少一行就是「基线有的选不了」。
 *  4. release 字面量以**该 API 级别 `Build.VERSION.RELEASE` 实际会上报的形态**为准
 *     (API 23 是 `6.0` 而非基线表里的 `6`)——伪装成一个从不存在的上报值是自造检测点,
 *     这一处不照搬,已在 `BaselineDataParityTest` 的 DELTA 白名单里逐条点名理由。
 */

/** Android 市场版本号 → `Build.VERSION.SDK_INT`(基线 `presets.json` androidVersions 同行集)。 */
private val ANDROID_RELEASE_TO_SDK: List<Pair<String, Int>> = listOf(
    "1" to 1, "1.1" to 2, "1.5" to 3, "1.6" to 4,
    "2.0" to 5, "2.0.1" to 6, "2.1" to 7, "2.2" to 8,
    "2.3" to 9, "2.3.3" to 10,
    "3.0" to 11, "3.1" to 12, "3.2" to 13,
    "4.0" to 14, "4.0.3" to 15, "4.1" to 16, "4.2" to 17, "4.3" to 18, "4.4" to 19, "4.4W" to 20,
    "5.0" to 21, "5.1" to 22,
    "6.0" to 23, "7.0" to 24, "7.1" to 25, "8.0" to 26, "8.1" to 27,
    "9" to 28, "10" to 29, "11" to 30, "12" to 31, "12L" to 32, "13" to 33,
    "14" to 34, "15" to 35, "16" to 36, "17" to 37,
)

object ProfileValueSets {

    /** 可选的 Android 版本号(表序即呈现序,升序)。 */
    fun androidReleases(): List<String> = ANDROID_RELEASE_TO_SDK.map { it.first }

    /** 可选的 SDK 值(与 [androidReleases] 同一张表的另一列)。 */
    fun sdkLevels(): List<String> = ANDROID_RELEASE_TO_SDK.map { it.second.toString() }

    /** release → SDK;认不出返回 null(调用方据此**放弃成对写**,而不是猜一个)。 */
    fun sdkForRelease(release: String): String? =
        ANDROID_RELEASE_TO_SDK.firstOrNull { it.first == release }?.second?.toString()

    /** release 的完整呈现,例 `14 (API 34)`;认不出原样返回。 */
    fun releaseLabel(release: String): String =
        sdkForRelease(release)?.let { "$release (API $it)" } ?: release

    /**
     * 「近期世代」随机池(V48 收口:此前 [ProfileEditorForm] 自带一张 `13/14/15` 私有表,
     * 那是 release↔SDK 的**第二份事实源**,且 release 认不出时会硬写 `34` 一个猜测值)。
     * 取本表最后四档 ⇒ 覆盖旧的三档池并补上当代,配对值一律回查本表,不另抄。
     */
    fun recentAndroidReleases(): List<String> = androidReleases().takeLast(RECENT_GENERATIONS)

    /** 标准密度档(Android 密度桶 + OEM 常见中间档)。 */
    fun densities(): List<String> = DENSITIES

    /** 常见 CPU 核数。 */
    fun cpuCores(): List<String> = CPU_CORES

    private val DENSITIES = listOf("120", "160", "213", "240", "280", "320", "360", "400", "420", "440", "480", "560", "640")

    private val CPU_CORES = listOf("2", "4", "6", "8", "10", "12")

    /** 随机池的世代宽度(表尾向上取四档)。 */
    private const val RECENT_GENERATIONS = 4
}
