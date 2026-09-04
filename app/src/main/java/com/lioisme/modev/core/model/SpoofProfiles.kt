package com.lioisme.modev.core.model

/**
 * 设备/网络伪装画像(V37 T2.3):把此前散落于注入侧 hooker 常量的伪装值
 * (Build 字段组、ro.* 属性组、电话/网络/CPU 假值参数)收敛为**可枚举、可覆写**的画像模型。
 *
 * - 内置档位:[SpoofProfiles.BUILTINS](auto / pixel7pro / xiaomi13);
 * - 用户覆写:[FeatureConfig.spoofOverrides],键形态:
 *   `build.<FIELD>`(Build 字段)/ `ro.<prop>`(系统属性)/
 *   `telephony.imeiPrefix|imsiPrefix|iccidPrefix|phonePrefix` /
 *   `wifi.ssid|wifi.bssid` / `cpu.cores`;
 * - 纯 Kotlin 零 Android 依赖(注入侧与宿主 UI 共用;JVM 直测)。
 */
data class SpoofProfile(
    /** 画像标识([SpoofProfiles.AUTO]/[SpoofProfiles.PIXEL_7_PRO]/[SpoofProfiles.XIAOMI_13]/自定义)。 */
    val id: String,
    /** Build 字段名(FINGERPRINT/MODEL/...)→ 伪造值。 */
    val buildFields: Map<String, String>,
    /** 系统属性键(ro.hardware/...)→ 伪造值。 */
    val roProperties: Map<String, String>,
    /** IMEI 生成前缀(空 = 纯哈希 14 位 + Luhn 校验位)。 */
    val imeiPrefix: String,
    /** IMSI 前缀(MCC+MNC)。 */
    val imsiPrefix: String,
    /** ICCID 前缀。 */
    val iccidPrefix: String,
    /** 手机号前缀。 */
    val phonePrefix: String,
    /** 假 Wi-Fi SSID。 */
    val fakeSsid: String,
    /** 假 Wi-Fi BSSID。 */
    val fakeBssid: String,
    /** 假 CPU 核数。 */
    val cpuCores: Int,
)

/**
 * 内置画像库与解析器(V37 T2.3 单一事实源):
 * 注入侧 hooker 一律经 [resolve] 取画像,禁止再内置机型字面量
 * (grep 门禁:Pixel/raven/fuxi 等字面量仅允许出现在本文件)。
 */
object SpoofProfiles {

    /** 自动档位:按模拟器特征族路由(generic 系 → Pixel 组,vbox/nox 系 → 小米组)。 */
    const val AUTO = "auto"
    const val PIXEL_7_PRO = "pixel7pro"
    const val XIAOMI_13 = "xiaomi13"

    /** Pixel 7 Pro(raven)组 Build 字段。 */
    val PIXEL_7_PRO_BUILD = mapOf(
        "FINGERPRINT" to "google/raven/raven:14/UP1A.231105.001/10754064:userdebug/dev-keys",
        "MODEL" to "Pixel 7 Pro",
        "PRODUCT" to "raven",
        "MANUFACTURER" to "Google",
        "DEVICE" to "raven",
        "BOARD" to "raven",
        "HARDWARE" to "raven",
        "BRAND" to "google",
        "HOST" to "abfarm-release-rbe-64-00069",
        "USER" to "android-build",
        "DISPLAY" to "UP1A.231105.001",
        "SERIAL" to "unknown",
        "TYPE" to "userdebug",
    )

    /** 小米 13(fuxi)组 Build 字段。 */
    val XIAOMI_13_BUILD = mapOf(
        "FINGERPRINT" to "Xiaomi/13/13:14/UP1A.231105.001/V816.0.23.0.UNACNXM:user/release-keys",
        "MODEL" to "2211133C",
        "PRODUCT" to "fuxi",
        "MANUFACTURER" to "Xiaomi",
        "DEVICE" to "fuxi",
        "BOARD" to "taro",
        "HARDWARE" to "qcom",
        "BRAND" to "Xiaomi",
        "HOST" to "xiaomi.eu",
        "USER" to "builder",
        "DISPLAY" to "V816.0.23.0.UNACNXM",
        "SERIAL" to "unknown",
        "TYPE" to "user",
    )

    /** Pixel 组 ro.* 属性。 */
    val PIXEL_7_PRO_RO = mapOf(
        "ro.hardware" to "qcom",
        "ro.product.model" to "Pixel 7 Pro",
        "ro.product.brand" to "google",
        "ro.product.device" to "raven",
        "ro.product.board" to "raven",
        "ro.build.display.id" to "UP1A.231105.001",
        "ro.build.host" to "abfarm-release-rbe-64-00069",
        "ro.build.user" to "android-build",
        "ro.product.name" to "raven",
        "ro.product.manufacturer" to "Google",
        "ro.board.platform" to "taro",
        "ro.hardware.chipname" to "qcom",
        "ro.soc.model" to "SM8550",
        "ro.soc.manufacturer" to "Qualcomm",
    )

    /** 小米组 ro.* 属性。 */
    val XIAOMI_13_RO = mapOf(
        "ro.hardware" to "qcom",
        "ro.product.model" to "2211133C",
        "ro.product.brand" to "Xiaomi",
        "ro.product.device" to "fuxi",
        "ro.product.board" to "taro",
        "ro.build.display.id" to "V816.0.23.0.UNACNXM",
        "ro.build.host" to "xiaomi.eu",
        "ro.build.user" to "builder",
        "ro.product.name" to "fuxi",
        "ro.product.manufacturer" to "Xiaomi",
        "ro.board.platform" to "taro",
        "ro.hardware.chipname" to "qcom",
        "ro.soc.model" to "SM8550",
        "ro.soc.manufacturer" to "Xiaomi",
    )

    /** 内置可选档位(UI 选择器驱动源;AUTO 恒居首)。 */
    val BUILTINS: List<SpoofProfile> = listOf(
        SpoofProfile(
            id = AUTO,
            buildFields = PIXEL_7_PRO_BUILD,
            roProperties = PIXEL_7_PRO_RO,
            imeiPrefix = "",
            imsiPrefix = "46000",
            iccidPrefix = "89860",
            phonePrefix = "138",
            fakeSsid = "MyNetwork",
            fakeBssid = "01:02:03:04:05:06",
            cpuCores = 8,
        ),
        SpoofProfile(
            id = PIXEL_7_PRO,
            buildFields = PIXEL_7_PRO_BUILD,
            roProperties = PIXEL_7_PRO_RO,
            imeiPrefix = "",
            imsiPrefix = "46000",
            iccidPrefix = "89860",
            phonePrefix = "138",
            fakeSsid = "MyNetwork",
            fakeBssid = "01:02:03:04:05:06",
            cpuCores = 8,
        ),
        SpoofProfile(
            id = XIAOMI_13,
            buildFields = XIAOMI_13_BUILD,
            roProperties = XIAOMI_13_RO,
            imeiPrefix = "",
            imsiPrefix = "46001",
            iccidPrefix = "89860",
            phonePrefix = "139",
            fakeSsid = "Xiaomi_WiFi",
            fakeBssid = "02:02:03:04:05:07",
            cpuCores = 8,
        ),
    )

    /**
     * 解析有效画像:档位缺失回落 [AUTO];[overrides] 按键前缀分派覆写
     * (非法键静默忽略,漂移安全)。
     */
    fun resolve(id: String?, overrides: Map<String, String>): SpoofProfile {
        val base = BUILTINS.firstOrNull { it.id == id } ?: BUILTINS.first()
        if (overrides.isEmpty()) return base
        var build = base.buildFields
        var ro = base.roProperties
        var imei = base.imeiPrefix
        var imsi = base.imsiPrefix
        var iccid = base.iccidPrefix
        var phone = base.phonePrefix
        var ssid = base.fakeSsid
        var bssid = base.fakeBssid
        var cores = base.cpuCores
        for ((key, value) in overrides) {
            when {
                key.startsWith("build.") && value.isNotEmpty() ->
                    build = build + (key.removePrefix("build.") to value)
                key.startsWith("ro.") && value.isNotEmpty() ->
                    ro = ro + (key to value)
                key == "telephony.imeiPrefix" -> imei = value
                key == "telephony.imsiPrefix" && value.isNotEmpty() -> imsi = value
                key == "telephony.iccidPrefix" && value.isNotEmpty() -> iccid = value
                key == "telephony.phonePrefix" && value.isNotEmpty() -> phone = value
                key == "wifi.ssid" && value.isNotEmpty() -> ssid = value
                key == "wifi.bssid" && value.isNotEmpty() -> bssid = value
                key == "cpu.cores" -> cores = value.toIntOrNull()?.coerceIn(1, 64) ?: cores
            }
        }
        return base.copy(
            buildFields = build,
            roProperties = ro,
            imeiPrefix = imei,
            imsiPrefix = imsi,
            iccidPrefix = iccid,
            phonePrefix = phone,
            fakeSsid = ssid,
            fakeBssid = bssid,
            cpuCores = cores,
        )
    }

    /** 解析有效画像(无覆写快捷入口)。 */
    fun resolve(id: String?): SpoofProfile = resolve(id, emptyMap())
}

/** 有效配置 → 当前生效伪装画像(V37 T2.3:注入侧唯一取用入口)。 */
fun HookSwitch.activeSpoofProfile(): SpoofProfile =
    SpoofProfiles.resolve(feature.spoofProfileId, feature.spoofOverrides)
