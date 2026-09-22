package com.lioisme.modev.core.model

/**
 * 内置画像规格(V47 T2,R-2「三套经典设备画像」)。
 *
 * 基线 Guise **没有任何内置设备画像**(其内置模板只有「空白通行证」与「允许强制截图」
 * 两项,且零设备字段)—— 故本文件是 Modev 净新增设计,基线能提供的只有字段模式、
 * 机型目录与整组随机派生法。
 *
 * **三档恒只填「机型身份」13 项**(品牌/制造商/型号/产品/设备/主板/硬件/Build ID/
 * Fingerprint/Android 版本/SDK/密度/核数),**不填**标识符、SIM、网络、定位、语言时区、
 * 电池、版本号与隐私通行证。理由两条:
 *  1. 那些不是「机型属性」而是「使用现场属性」—— 选一台 Pixel 不该顺手把用户的真实
 *     运营商与坐标改掉;
 *  2. 尤其 IMEI/Android ID 若在内置档里写死,会让所有绑定该档的应用共享同一个标识,
 *     反而成为跨应用关联指纹。需要伪标识请另存 DIY 画像(或后续独立随机化动作)。
 *
 * 字面量取自公开规格与本仓既有内置档(V37 的 Pixel/小米两组),**WP-8 的门禁只锁两条**:
 * 画像内部自洽(fingerprint 与三元组/版本/SDK 互推一致)与目标应用所见恰等于画像值;
 * 不承诺与某台实物逐位相同。日后要换成真机采集值,只需改本文件的 [BUILTINS]。
 */
data class BuiltinProfileSpec(
    /** 稳定键(账本主键,永不改名;`builtin:` 前缀拼成条目 id)。 */
    val seedId: String,
    /** 内容版本:字段值或字段集变化时递增,受管辖行随之原地更新。 */
    val version: Int,
    /** 画像正文。 */
    val profile: DeviceProfile,
) {
    /** 账本条目 id。 */
    val entryId: String get() = BuiltinDeviceProfiles.idFor(seedId)
}

/** 内置画像库与账本期望集(V47 T2 机型字面量唯一事实源,钩子与 UI 禁再内置)。 */
object BuiltinDeviceProfiles {

    /** 内置条目 id 前缀。 */
    const val ID_PREFIX = "builtin:"

    /** 内置档 seedId:Pixel 7 Pro。 */
    const val PIXEL_7_PRO = "pixel-7-pro"

    /** 内置档 seedId:Xiaomi 13。 */
    const val XIAOMI_13 = "xiaomi-13"

    /** 内置档 seedId:Galaxy S23 Ultra。 */
    const val GALAXY_S23_ULTRA = "galaxy-s23-ultra"

    /** seedId → 条目 id。 */
    fun idFor(seedId: String): String = "$ID_PREFIX$seedId"

    /** 期望内置集(顺序即首装顺序)。 */
    val BUILTINS: List<BuiltinProfileSpec> = listOf(
        BuiltinProfileSpec(
            seedId = PIXEL_7_PRO,
            version = 1,
            profile = DeviceProfile(
                brand = "google",
                manufacturer = "Google",
                model = "Pixel 7 Pro",
                product = "raven",
                device = "raven",
                board = "raven",
                hardware = "oriole",
                buildId = "UP1A.231005.00B",
                fingerPrint = "google/raven/raven:14/UP1A.231005.00B/10763599:user/release-keys",
                androidVersion = "14",
                sdkInt = 34,
                densityDpi = 500,
                cpuCores = 8,
            ),
        ),
        BuiltinProfileSpec(
            seedId = XIAOMI_13,
            version = 1,
            profile = DeviceProfile(
                brand = "Xiaomi",
                manufacturer = "Xiaomi",
                model = "2211133C",
                product = "fuxi",
                device = "fuxi",
                board = "taro",
                hardware = "qcom",
                buildId = "UKQ1.230804.001",
                fingerPrint = "Xiaomi/fuxi/fuxi:14/UKQ1.230804.001/V816.0.23.0.UNACNXM:user/release-keys",
                androidVersion = "14",
                sdkInt = 34,
                densityDpi = 440,
                cpuCores = 8,
            ),
        ),
        BuiltinProfileSpec(
            seedId = GALAXY_S23_ULTRA,
            version = 1,
            profile = DeviceProfile(
                brand = "samsung",
                manufacturer = "Samsung",
                model = "SM-S918B",
                product = "d2sxx",
                device = "d2s",
                board = "d2s",
                hardware = "qcom",
                buildId = "UP1A.231005.007",
                fingerPrint = "samsung/d2sxx/d2s:14/UP1A.231005.007/S918BXXU3AWK3:user/release-keys",
                androidVersion = "14",
                sdkInt = 34,
                densityDpi = 560,
                cpuCores = 8,
            ),
        ),
    )

    /** 按 seedId 取期望规格。 */
    fun specFor(seedId: String): BuiltinProfileSpec? = BUILTINS.firstOrNull { it.seedId == seedId }
}
