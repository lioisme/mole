package com.lioisme.modev.core.model

/**
 * 画像取值域常量与哨兵。语义与基线 `HooksValue` 一致:
 * **「字段等于缺省值」即「该干预组不安装」**,模型内不存在 per-field 布尔开关。
 *
 * 截屏方向沿用基线编号(零 = 强制禁止,一 = 强制允许),避免与基线配置互换时语义倒置。
 */
object ProfileValues {

    /** 隐藏活动网络(ConnectivityManager 三方法置空)。 */
    const val NET_NONE = -1

    /** 不干预网络类型(缺省哨兵)。 */
    const val NET_UNHOOK = 0

    /** Wi-Fi。 */
    const val NET_WIFI = 1

    /** 二代网络。 */
    const val NET_MOBILE_2G = 2

    /** 三代网络。 */
    const val NET_MOBILE_3G = 3

    /** 四代网络。 */
    const val NET_MOBILE_4G = 4

    /** 五代网络。 */
    const val NET_MOBILE_5G = 5

    /** 不干预截屏策略(缺省哨兵)。 */
    const val SCREENSHOT_UNHOOK = -1

    /** 强制禁止截屏(向 Activity 窗口追加 FLAG_SECURE)。 */
    const val SCREENSHOT_DENY = 0

    /** 强制允许截屏(清理 FLAG_SECURE)。 */
    const val SCREENSHOT_ALLOW = 1

    /** 数值型缺省哨兵。 */
    const val UNSET_INT = -1

    /** 浮点型缺省哨兵。 */
    const val UNSET_DOUBLE = -1.0

    /** 密度有效下界。 */
    const val DENSITY_MIN = 72

    /** 密度有效上界。 */
    const val DENSITY_MAX = 1000

    /** 电池有效上界。 */
    const val BATTERY_MAX = 100

    /** 核数有效上界(下界恒为一)。 */
    const val CPU_CORES_MAX = 64
}

/**
 * 设备画像值对象:**伪装配置的唯一模型**,41 个字段逐项对齐基线能力
 * (见 [ProfileField.baselineKey])。
 *
 * 与旧 [SpoofProfile] 的根本差别:
 * - 旧模型只有九个字段段,且需经扁平覆写表([FeatureConfig.spoofOverrides])间接拼装;
 * - 本模型是**可空语义完整**的扁平值对象,任何消费方(UI/MCP/wire/注入侧/压测期望表)
 *   都经 [ProfileField] 的穷尽 `when` 桥接读写,编译期保证不漏字段。
 *
 * **全部参数带默认值**:Kotlin 据此生成无参构造器,Gson 走「构造器 + 逐字段填充」路径,
 * 从而保住默认值语义(AGENTS 已实锤:Gson 无 no-arg 构造器时会 `Unsafe.allocateInstance`
 * 跳过默认值表达式,使可选字段缺省等价 null)。新增字段必须同时给默认值,否则该保证破裂。
 *
 * 纯 Kotlin 零 Android 依赖:注入侧与宿主 UI 与 JVM 单测共用同一份类型。
 */
data class DeviceProfile(

    // ---- DEVICE 分区:身份与构建 ----
    /** Build.BRAND。 */
    val brand: String = "",
    /** Build.MANUFACTURER。 */
    val manufacturer: String = "",
    /** Build.MODEL。 */
    val model: String = "",
    /** Build.PRODUCT。 */
    val product: String = "",
    /** Build.DEVICE。 */
    val device: String = "",
    /** Build.BOARD。 */
    val board: String = "",
    /** Build.HARDWARE。 */
    val hardware: String = "",
    /** Build.ID。 */
    val buildId: String = "",
    /** Build.FINGERPRINT(基线字段名拼写为 fingerPrint,wire 键沿用)。 */
    val fingerPrint: String = "",
    /** Build.VERSION.RELEASE。 */
    val androidVersion: String = "",
    /** Build.VERSION.SDK_INT。 */
    val sdkInt: Int = ProfileValues.UNSET_INT,
    /** DisplayMetrics.densityDpi。 */
    val densityDpi: Int = ProfileValues.UNSET_INT,
    /** CPU 核数(Modev 超基线项)。 */
    val cpuCores: Int = ProfileValues.UNSET_INT,
    /** 活动网络类型([ProfileValues.NET_UNHOOK] 表示不干预)。 */
    val networkType: Int = ProfileValues.NET_UNHOOK,
    /** WifiInfo.getSSID。 */
    val wifiSSID: String = "",
    /** WifiInfo.getBSSID。 */
    val wifiBSSID: String = "",
    /** WifiInfo.getMacAddress。 */
    val wifiMacAddress: String = "",
    /** SIM PLMN。 */
    val simOperator: String = "",
    /** SIM 运营商显示名。 */
    val simOperatorName: String = "",
    /** SIM 国家 ISO。 */
    val simCountry: String = "",
    /** Settings 中的 ANDROID_ID。 */
    val androidId: String = "",
    /** IMEI。 */
    val imei: String = "",
    /** 手机号。 */
    val phoneNum: String = "",
    /** 位置区域码。 */
    val lac: Int = ProfileValues.UNSET_INT,
    /** 小区标识。 */
    val cid: Int = ProfileValues.UNSET_INT,
    /** 经度。 */
    val longitude: Double = ProfileValues.UNSET_DOUBLE,
    /** 纬度。 */
    val latitude: Double = ProfileValues.UNSET_DOUBLE,
    /** 坐标每进程微抖动。 */
    val randomOffset: Boolean = false,
    /** 隐藏 Wi-Fi 定位来源。 */
    val makeWifiLocationFail: Boolean = false,
    /** 隐藏基站定位信息。 */
    val makeCellLocationFail: Boolean = false,
    /** 语言与地区。 */
    val language: String = "",
    /** 默认时区。 */
    val timeZone: String = "",
    /** 目标应用 longVersionCode。 */
    val versionCode: Int = ProfileValues.UNSET_INT,
    /** 目标应用 versionName。 */
    val versionName: String = "",
    /** 电池容量百分比。 */
    val batteryLevel: Int = ProfileValues.UNSET_INT,
    /** 截屏策略([ProfileValues.SCREENSHOT_UNHOOK] 表示不干预)。 */
    val screenshotsFlag: Int = ProfileValues.SCREENSHOT_UNHOOK,
    /** 通讯录空白通行证。 */
    val passContacts: Boolean = false,
    /** 图片空白通行证。 */
    val passPhoto: Boolean = false,
    /** 视频空白通行证。 */
    val passVideo: Boolean = false,
    /** 音频空白通行证。 */
    val passAudio: Boolean = false,
    /** 应用列表空白通行证。 */
    val passApplications: Boolean = false,
) {

    /** 已填字段数(零 = 空画像 = 画像计划编译器裁剪出零个干预组)。 */
    val filledCount: Int get() = ProfileField.entries.count { it.isSetIn(this) }

    /** 全部字段均处于缺省态。 */
    val isEmpty: Boolean get() = filledCount == 0

    companion object {
        /** 空画像:所有字段处于「不生效」态;既是默认值,也是「不伪装」的机器可比对形态。 */
        val EMPTY = DeviceProfile()
    }
}

/**
 * [ProfileField] 与 [DeviceProfile] 属性之间的**唯一桥接**(穷尽 `when`,零反射)。
 * 新增画像字段时,以下四个函数与数据类参数同时编译期报错,强制四处同步
 * (与 AGENTS 既有的 readKind 与 applyKinds 互逆契约同一手法)。
 */
fun ProfileField.stringOf(profile: DeviceProfile): String = when (this) {
    ProfileField.BRAND -> profile.brand
    ProfileField.MANUFACTURER -> profile.manufacturer
    ProfileField.MODEL -> profile.model
    ProfileField.PRODUCT -> profile.product
    ProfileField.DEVICE -> profile.device
    ProfileField.BOARD -> profile.board
    ProfileField.HARDWARE -> profile.hardware
    ProfileField.BUILD_ID -> profile.buildId
    ProfileField.FINGERPRINT -> profile.fingerPrint
    ProfileField.ANDROID_VERSION -> profile.androidVersion
    ProfileField.WIFI_SSID -> profile.wifiSSID
    ProfileField.WIFI_BSSID -> profile.wifiBSSID
    ProfileField.WIFI_MAC_ADDRESS -> profile.wifiMacAddress
    ProfileField.SIM_OPERATOR -> profile.simOperator
    ProfileField.SIM_OPERATOR_NAME -> profile.simOperatorName
    ProfileField.SIM_COUNTRY -> profile.simCountry
    ProfileField.ANDROID_ID -> profile.androidId
    ProfileField.IMEI -> profile.imei
    ProfileField.PHONE_NUM -> profile.phoneNum
    ProfileField.LANGUAGE -> profile.language
    ProfileField.TIME_ZONE -> profile.timeZone
    ProfileField.VERSION_NAME -> profile.versionName
    ProfileField.SDK_INT -> numericText(profile.sdkInt)
    ProfileField.DENSITY_DPI -> numericText(profile.densityDpi)
    ProfileField.CPU_CORES -> numericText(profile.cpuCores)
    ProfileField.LAC -> numericText(profile.lac)
    ProfileField.CID -> numericText(profile.cid)
    ProfileField.VERSION_CODE -> numericText(profile.versionCode)
    ProfileField.BATTERY_LEVEL -> numericText(profile.batteryLevel)
    ProfileField.NETWORK_TYPE -> enumText(profile.networkType, ProfileValues.NET_UNHOOK)
    ProfileField.SCREENSHOTS_FLAG -> enumText(profile.screenshotsFlag, ProfileValues.SCREENSHOT_UNHOOK)
    ProfileField.LONGITUDE -> numericText(profile.longitude)
    ProfileField.LATITUDE -> numericText(profile.latitude)
    ProfileField.RANDOM_OFFSET -> boolText(profile.randomOffset)
    ProfileField.WIFI_LOCATION_HIDE -> boolText(profile.makeWifiLocationFail)
    ProfileField.CELL_LOCATION_HIDE -> boolText(profile.makeCellLocationFail)
    ProfileField.PASS_CONTACTS -> boolText(profile.passContacts)
    ProfileField.PASS_PHOTO -> boolText(profile.passPhoto)
    ProfileField.PASS_VIDEO -> boolText(profile.passVideo)
    ProfileField.PASS_AUDIO -> boolText(profile.passAudio)
    ProfileField.PASS_APPLICATIONS -> boolText(profile.passApplications)
}

private fun numericText(value: Int): String =
    if (value == ProfileValues.UNSET_INT) "" else value.toString()

/** 枚举槽字段的编辑态文本:处于「不干预」槽位时渲染为空,与其他字段的清空即取消语义一致。 */
private fun enumText(value: Int, unhookSlot: Int): String =
    if (value == unhookSlot) "" else value.toString()

private fun numericText(value: Double): String =
    if (value == ProfileValues.UNSET_DOUBLE) "" else value.toString()

private fun boolText(value: Boolean): String = if (value) "true" else ""

/** 该字段当前是否处于「生效」态(哨兵判定唯一实现处)。 */
fun ProfileField.isSetIn(profile: DeviceProfile): Boolean = when (this) {
    ProfileField.BRAND -> profile.brand.isNotEmpty()
    ProfileField.MANUFACTURER -> profile.manufacturer.isNotEmpty()
    ProfileField.MODEL -> profile.model.isNotEmpty()
    ProfileField.PRODUCT -> profile.product.isNotEmpty()
    ProfileField.DEVICE -> profile.device.isNotEmpty()
    ProfileField.BOARD -> profile.board.isNotEmpty()
    ProfileField.HARDWARE -> profile.hardware.isNotEmpty()
    ProfileField.BUILD_ID -> profile.buildId.isNotEmpty()
    ProfileField.FINGERPRINT -> profile.fingerPrint.isNotEmpty()
    ProfileField.ANDROID_VERSION -> profile.androidVersion.isNotEmpty()
    ProfileField.SDK_INT -> profile.sdkInt != ProfileValues.UNSET_INT
    ProfileField.DENSITY_DPI -> profile.densityDpi != ProfileValues.UNSET_INT
    ProfileField.CPU_CORES -> profile.cpuCores != ProfileValues.UNSET_INT
    ProfileField.NETWORK_TYPE -> profile.networkType != ProfileValues.NET_UNHOOK
    ProfileField.WIFI_SSID -> profile.wifiSSID.isNotEmpty()
    ProfileField.WIFI_BSSID -> profile.wifiBSSID.isNotEmpty()
    ProfileField.WIFI_MAC_ADDRESS -> profile.wifiMacAddress.isNotEmpty()
    ProfileField.SIM_OPERATOR -> profile.simOperator.isNotEmpty()
    ProfileField.SIM_OPERATOR_NAME -> profile.simOperatorName.isNotEmpty()
    ProfileField.SIM_COUNTRY -> profile.simCountry.isNotEmpty()
    ProfileField.ANDROID_ID -> profile.androidId.isNotEmpty()
    ProfileField.IMEI -> profile.imei.isNotEmpty()
    ProfileField.PHONE_NUM -> profile.phoneNum.isNotEmpty()
    ProfileField.LAC -> profile.lac != ProfileValues.UNSET_INT
    ProfileField.CID -> profile.cid != ProfileValues.UNSET_INT
    ProfileField.LONGITUDE -> profile.longitude != ProfileValues.UNSET_DOUBLE
    ProfileField.LATITUDE -> profile.latitude != ProfileValues.UNSET_DOUBLE
    ProfileField.RANDOM_OFFSET -> profile.randomOffset
    ProfileField.WIFI_LOCATION_HIDE -> profile.makeWifiLocationFail
    ProfileField.CELL_LOCATION_HIDE -> profile.makeCellLocationFail
    ProfileField.LANGUAGE -> profile.language.isNotEmpty()
    ProfileField.TIME_ZONE -> profile.timeZone.isNotEmpty()
    ProfileField.VERSION_CODE -> profile.versionCode != ProfileValues.UNSET_INT
    ProfileField.VERSION_NAME -> profile.versionName.isNotEmpty()
    ProfileField.BATTERY_LEVEL -> profile.batteryLevel != ProfileValues.UNSET_INT
    ProfileField.SCREENSHOTS_FLAG -> profile.screenshotsFlag != ProfileValues.SCREENSHOT_UNHOOK
    ProfileField.PASS_CONTACTS -> profile.passContacts
    ProfileField.PASS_PHOTO -> profile.passPhoto
    ProfileField.PASS_VIDEO -> profile.passVideo
    ProfileField.PASS_AUDIO -> profile.passAudio
    ProfileField.PASS_APPLICATIONS -> profile.passApplications
}

/**
 * 以编辑态文本写入字段(空串或不可解析一律回落缺省哨兵 ⇒ 编辑清空即取消干预,
 * 无需额外的「清除」控件;数值域校验见 [ProfileFieldCatalog.validate])。
 */
fun ProfileField.withText(profile: DeviceProfile, raw: String): DeviceProfile {
    val text = raw.trim()
    return when (this) {
        ProfileField.BRAND -> profile.copy(brand = text)
        ProfileField.MANUFACTURER -> profile.copy(manufacturer = text)
        ProfileField.MODEL -> profile.copy(model = text)
        ProfileField.PRODUCT -> profile.copy(product = text)
        ProfileField.DEVICE -> profile.copy(device = text)
        ProfileField.BOARD -> profile.copy(board = text)
        ProfileField.HARDWARE -> profile.copy(hardware = text)
        ProfileField.BUILD_ID -> profile.copy(buildId = text)
        ProfileField.FINGERPRINT -> profile.copy(fingerPrint = text)
        ProfileField.ANDROID_VERSION -> profile.copy(androidVersion = text)
        ProfileField.SDK_INT -> profile.copy(sdkInt = text.toIntOrUnset())
        ProfileField.DENSITY_DPI -> profile.copy(densityDpi = text.toIntOrUnset())
        ProfileField.CPU_CORES -> profile.copy(cpuCores = text.toIntOrUnset())
        ProfileField.NETWORK_TYPE -> profile.copy(networkType = text.toIntOr(ProfileValues.NET_UNHOOK))
        ProfileField.WIFI_SSID -> profile.copy(wifiSSID = text)
        ProfileField.WIFI_BSSID -> profile.copy(wifiBSSID = text)
        ProfileField.WIFI_MAC_ADDRESS -> profile.copy(wifiMacAddress = text)
        ProfileField.SIM_OPERATOR -> profile.copy(simOperator = text)
        ProfileField.SIM_OPERATOR_NAME -> profile.copy(simOperatorName = text)
        ProfileField.SIM_COUNTRY -> profile.copy(simCountry = text)
        ProfileField.ANDROID_ID -> profile.copy(androidId = text)
        ProfileField.IMEI -> profile.copy(imei = text)
        ProfileField.PHONE_NUM -> profile.copy(phoneNum = text)
        ProfileField.LAC -> profile.copy(lac = text.toIntOrUnset())
        ProfileField.CID -> profile.copy(cid = text.toIntOrUnset())
        ProfileField.LONGITUDE -> profile.copy(longitude = text.toDoubleOrUnset())
        ProfileField.LATITUDE -> profile.copy(latitude = text.toDoubleOrUnset())
        ProfileField.RANDOM_OFFSET -> profile.copy(randomOffset = text.toBooleanStrictOrNull() ?: false)
        ProfileField.WIFI_LOCATION_HIDE -> profile.copy(makeWifiLocationFail = text.toBooleanStrictOrNull() ?: false)
        ProfileField.CELL_LOCATION_HIDE -> profile.copy(makeCellLocationFail = text.toBooleanStrictOrNull() ?: false)
        ProfileField.LANGUAGE -> profile.copy(language = text)
        ProfileField.TIME_ZONE -> profile.copy(timeZone = text)
        ProfileField.VERSION_CODE -> profile.copy(versionCode = text.toIntOrUnset())
        ProfileField.VERSION_NAME -> profile.copy(versionName = text)
        ProfileField.BATTERY_LEVEL -> profile.copy(batteryLevel = text.toIntOrUnset())
        ProfileField.SCREENSHOTS_FLAG -> profile.copy(screenshotsFlag = text.toIntOr(ProfileValues.SCREENSHOT_UNHOOK))
        ProfileField.PASS_CONTACTS -> profile.copy(passContacts = text.toBooleanStrictOrNull() ?: false)
        ProfileField.PASS_PHOTO -> profile.copy(passPhoto = text.toBooleanStrictOrNull() ?: false)
        ProfileField.PASS_VIDEO -> profile.copy(passVideo = text.toBooleanStrictOrNull() ?: false)
        ProfileField.PASS_AUDIO -> profile.copy(passAudio = text.toBooleanStrictOrNull() ?: false)
        ProfileField.PASS_APPLICATIONS -> profile.copy(passApplications = text.toBooleanStrictOrNull() ?: false)
    }
}

private fun String.toIntOrUnset(): Int = toIntOrNull() ?: ProfileValues.UNSET_INT

private fun String.toIntOr(fallback: Int): Int = toIntOrNull() ?: fallback

private fun String.toDoubleOrUnset(): Double = toDoubleOrNull() ?: ProfileValues.UNSET_DOUBLE
