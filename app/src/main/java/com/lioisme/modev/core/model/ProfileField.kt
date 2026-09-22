package com.lioisme.modev.core.model

/**
 * 画像编辑器的信息分区(V47 T1)。与基线 `ConfigEditor` 的九个区块同构,
 * 两处有意的 Modev 偏离:语言与时区从「Other」独立成 [LOCALE](它们是两种完全不同的
 * 生效机制:进程默认值改写 vs 零钩子赋值),CPU 核数留在 [DEVICE](基线无此项)。
 */
enum class ProfileSection {
    /** 设备与构建身份(Brand/Manufacturer/Model/Product/Device/Board/Hardware/Build ID/Fingerprint/版本/SDK/DPI/核数)。 */
    DEVICE,

    /** 网络活动类型与 Wi-Fi 标识。 */
    NETWORK,

    /** SIM 运营商三件套。 */
    SIM,

    /** 唯一标识(Android ID/IMEI/手机号)。 */
    IDENTITY,

    /** 基站参数。 */
    CELL,

    /** 定位坐标与两类定位来源隐藏。 */
    LOCATION,

    /** 语言与默认时区。 */
    LOCALE,

    /** 目标应用自身版本。 */
    APP_VERSION,

    /** 电池与截屏策略。 */
    OTHER,

    /** 空白通行证(内容四族与应用列表)。 */
    BLANK_PASS,
}

/**
 * 画像字段的取值形态(决定 UI 控件、MCP schema 类型与 JSON 编解码,四者单点派生)。
 */
enum class ProfileValueType {
    /** 字符串;空串 = 不生效。 */
    TEXT,

    /** 整数;负一 = 不生效。 */
    INT,

    /** 浮点;负一 = 不生效。 */
    DOUBLE,

    /** 布尔;false = 不生效。 */
    BOOL,

    /** 活动网络类型枚举槽(零 = 不生效,负一 = 隐藏活动网络,其余为 Wi-Fi 与 2G 至 5G)。 */
    NETWORK_TYPE,

    /** 截屏策略枚举槽(负一 = 不干预,零 = 强制允许,一 = 强制禁止)。 */
    SCREENSHOT_MODE,
}

/**
 * 画像字段枚举(V47 T1):**伪装配置面的唯一事实源**。
 *
 * 41 项 = 基线 `ModuleConfig` 的 40 项逐字段对齐([baselineKey] 一一映射,拼写沿用基线
 * 以保 wire 兼容)+ Modev 超基线的 [ProfileKind.PROFILE_CPU] 核数一项([baselineKey] 为 null)。
 *
 * 一切消费方(UI 表单行序、MCP 参数名与 schema、wire JSON 键、门控归属、压测期望表)
 * 都必须经本枚举与 [DeviceProfile] 的穷尽 `when` 桥接取值,
 * **禁止反射、禁止字符串键散落、禁止任何一处手抄字段清单**。
 */
enum class ProfileField(

    /** 归属干预类别(门控、记录 kind、UI 分组三处共用)。 */
    val kind: ProfileKind,

    /** 归属信息分区(UI 单点驱动)。 */
    val section: ProfileSection,

    /** 取值形态(控件与 schema 类型单点派生)。 */
    val type: ProfileValueType,

    /** 基线 `ModuleConfig` 同义字段名;null = Modev 超基线项。 */
    val baselineKey: String?,
) {

    // ---- DEVICE ----

    /** Build.BRAND。 */
    BRAND(ProfileKind.PROFILE_BUILD, ProfileSection.DEVICE, ProfileValueType.TEXT, "brand"),

    /** Build.MANUFACTURER(独立于品牌,留空即不改写)。 */
    MANUFACTURER(ProfileKind.PROFILE_BUILD, ProfileSection.DEVICE, ProfileValueType.TEXT, "manufacturer"),

    /** Build.MODEL。 */
    MODEL(ProfileKind.PROFILE_BUILD, ProfileSection.DEVICE, ProfileValueType.TEXT, "model"),

    /** Build.PRODUCT。 */
    PRODUCT(ProfileKind.PROFILE_BUILD, ProfileSection.DEVICE, ProfileValueType.TEXT, "product"),

    /** Build.DEVICE。 */
    DEVICE(ProfileKind.PROFILE_BUILD, ProfileSection.DEVICE, ProfileValueType.TEXT, "device"),

    /** Build.BOARD。 */
    BOARD(ProfileKind.PROFILE_BUILD, ProfileSection.DEVICE, ProfileValueType.TEXT, "board"),

    /** Build.HARDWARE。 */
    HARDWARE(ProfileKind.PROFILE_BUILD, ProfileSection.DEVICE, ProfileValueType.TEXT, "hardware"),

    /** Build.ID。 */
    BUILD_ID(ProfileKind.PROFILE_BUILD, ProfileSection.DEVICE, ProfileValueType.TEXT, "buildId"),

    /** Build.FINGERPRINT(结构须与品牌/产品/设备三元组自洽)。 */
    FINGERPRINT(ProfileKind.PROFILE_BUILD, ProfileSection.DEVICE, ProfileValueType.TEXT, "fingerPrint"),

    /** Build.VERSION.RELEASE。 */
    ANDROID_VERSION(ProfileKind.PROFILE_BUILD, ProfileSection.DEVICE, ProfileValueType.TEXT, "androidVersion"),

    /** Build.VERSION.SDK_INT。 */
    SDK_INT(ProfileKind.PROFILE_BUILD, ProfileSection.DEVICE, ProfileValueType.INT, "sdkInt"),

    /** DisplayMetrics.densityDpi(生效域 72 至 1000)。 */
    DENSITY_DPI(ProfileKind.PROFILE_DENSITY, ProfileSection.DEVICE, ProfileValueType.INT, "densityDpi"),

    /** CPU 核数(Runtime 与 cpuinfo 双路径;Modev 超基线项)。 */
    CPU_CORES(ProfileKind.PROFILE_CPU, ProfileSection.DEVICE, ProfileValueType.INT, null),

    // ---- NETWORK ----

    /** 活动网络类型。 */
    NETWORK_TYPE(ProfileKind.PROFILE_NETWORK, ProfileSection.NETWORK, ProfileValueType.NETWORK_TYPE, "networkType"),

    /** WifiInfo.getSSID(展示层自动补首尾双引号)。 */
    WIFI_SSID(ProfileKind.PROFILE_WIFI, ProfileSection.NETWORK, ProfileValueType.TEXT, "wifiSSID"),

    /** WifiInfo.getBSSID 与扫描结果命中项。 */
    WIFI_BSSID(ProfileKind.PROFILE_WIFI, ProfileSection.NETWORK, ProfileValueType.TEXT, "wifiBSSID"),

    /** WifiInfo.getMacAddress。 */
    WIFI_MAC_ADDRESS(ProfileKind.PROFILE_WIFI, ProfileSection.NETWORK, ProfileValueType.TEXT, "wifiMacAddress"),

    // ---- SIM ----

    /** PLMN(五位或六位纯数字;非法即整族放弃)。 */
    SIM_OPERATOR(ProfileKind.PROFILE_SIM, ProfileSection.SIM, ProfileValueType.TEXT, "simOperator"),

    /** 运营商显示名。 */
    SIM_OPERATOR_NAME(ProfileKind.PROFILE_SIM, ProfileSection.SIM, ProfileValueType.TEXT, "simOperatorName"),

    /** SIM 国家 ISO 两位码。 */
    SIM_COUNTRY(ProfileKind.PROFILE_SIM, ProfileSection.SIM, ProfileValueType.TEXT, "simCountry"),

    // ---- IDENTITY ----

    /** Settings 中的 ANDROID_ID(十六进制)。 */
    ANDROID_ID(ProfileKind.PROFILE_UNIQUE_ID, ProfileSection.IDENTITY, ProfileValueType.TEXT, "androidId"),

    /** IMEI(十五位数字含 Luhn 校验位;TAC 取其前八位派生)。 */
    IMEI(ProfileKind.PROFILE_UNIQUE_ID, ProfileSection.IDENTITY, ProfileValueType.TEXT, "imei"),

    /** 手机号。 */
    PHONE_NUM(ProfileKind.PROFILE_UNIQUE_ID, ProfileSection.IDENTITY, ProfileValueType.TEXT, "phoneNum"),

    // ---- CELL ----

    /** 位置区域码 LAC(TAC/NetworkId 同源派生)。 */
    LAC(ProfileKind.PROFILE_CELL, ProfileSection.CELL, ProfileValueType.INT, "lac"),

    /** 小区标识 CID(CI/NCI/BaseStationId 同源派生)。 */
    CID(ProfileKind.PROFILE_CELL, ProfileSection.CELL, ProfileValueType.INT, "cid"),

    // ---- LOCATION ----

    /** Location 经度。 */
    LONGITUDE(ProfileKind.PROFILE_LOCATION, ProfileSection.LOCATION, ProfileValueType.DOUBLE, "longitude"),

    /** Location 纬度。 */
    LATITUDE(ProfileKind.PROFILE_LOCATION, ProfileSection.LOCATION, ProfileValueType.DOUBLE, "latitude"),

    /** 每进程启动时的坐标微抖动开关。 */
    RANDOM_OFFSET(ProfileKind.PROFILE_LOCATION, ProfileSection.LOCATION, ProfileValueType.BOOL, "randomOffset"),

    /** 隐藏 Wi-Fi 定位来源(优先于 Wi-Fi 标识伪装)。 */
    WIFI_LOCATION_HIDE(
        ProfileKind.PROFILE_WIFI_LOCATION_HIDE,
        ProfileSection.LOCATION,
        ProfileValueType.BOOL,
        "makeWifiLocationFail",
    ),

    /** 隐藏基站定位信息(优先于基站参数伪装)。 */
    CELL_LOCATION_HIDE(
        ProfileKind.PROFILE_CELL_LOCATION_HIDE,
        ProfileSection.LOCATION,
        ProfileValueType.BOOL,
        "makeCellLocationFail",
    ),

    // ---- LOCALE ----

    /** 进程默认语言与地区。 */
    LANGUAGE(ProfileKind.PROFILE_LOCALE, ProfileSection.LOCALE, ProfileValueType.TEXT, "language"),

    /** 进程默认时区(IANA 标识)。 */
    TIME_ZONE(ProfileKind.PROFILE_TIME_ZONE, ProfileSection.LOCALE, ProfileValueType.TEXT, "timeZone"),

    // ---- APP_VERSION ----

    /** 目标应用自身 longVersionCode。 */
    VERSION_CODE(ProfileKind.PROFILE_APP_VERSION, ProfileSection.APP_VERSION, ProfileValueType.INT, "versionCode"),

    /** 目标应用自身 versionName。 */
    VERSION_NAME(ProfileKind.PROFILE_APP_VERSION, ProfileSection.APP_VERSION, ProfileValueType.TEXT, "versionName"),

    // ---- OTHER ----

    /** 电池容量百分比(零至一百)。 */
    BATTERY_LEVEL(ProfileKind.PROFILE_BATTERY, ProfileSection.OTHER, ProfileValueType.INT, "batteryLevel"),

    /** 截屏策略三态。 */
    SCREENSHOTS_FLAG(ProfileKind.PROFILE_SCREENSHOT, ProfileSection.OTHER, ProfileValueType.SCREENSHOT_MODE, "screenshotsFlag"),

    // ---- BLANK_PASS ----

    /** 通讯录查询返回空 Cursor。 */
    PASS_CONTACTS(ProfileKind.PROFILE_BLANK_CONTENT, ProfileSection.BLANK_PASS, ProfileValueType.BOOL, "passContacts"),

    /** 图片媒体查询返回空 Cursor。 */
    PASS_PHOTO(ProfileKind.PROFILE_BLANK_CONTENT, ProfileSection.BLANK_PASS, ProfileValueType.BOOL, "passPhoto"),

    /** 视频媒体查询返回空 Cursor。 */
    PASS_VIDEO(ProfileKind.PROFILE_BLANK_CONTENT, ProfileSection.BLANK_PASS, ProfileValueType.BOOL, "passVideo"),

    /** 音频媒体查询返回空 Cursor。 */
    PASS_AUDIO(ProfileKind.PROFILE_BLANK_CONTENT, ProfileSection.BLANK_PASS, ProfileValueType.BOOL, "passAudio"),

    /** 应用列表仅暴露自身与系统应用。 */
    PASS_APPLICATIONS(ProfileKind.PROFILE_APP_LIST, ProfileSection.BLANK_PASS, ProfileValueType.BOOL, "passApplications"),
}
