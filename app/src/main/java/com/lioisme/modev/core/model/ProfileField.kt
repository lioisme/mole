package com.lioisme.modev.core.model

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
 * 画像字段枚举:**伪装配置面的唯一事实源**。
 *
 * 41 项 = 基线 `ModuleConfig` 的 40 项逐字段对齐([baselineKey] 一一映射,拼写沿用基线
 * 以保 wire 兼容)+ Modev 超基线的 [ProfileKind.PROFILE_CPU] 核数一项([baselineKey] 为 null)。
 *
 * 一切消费方(UI 表单行序、MCP 参数名与 schema、wire JSON 键、门控归属、压测期望表)
 * 都必须经本枚举与 [DeviceProfile] 的穷尽 `when` 桥接取值,
 * **禁止反射、禁止字符串键散落、禁止任何一处手抄字段清单**。
 *
 * **分类只有一轴**:[kind] 即底层来源族,同时就是 UI 分区、钩子族与 MCP enum 值;
 * (为什么只允许一条轴,见 [ProfileKind]。)
 */
enum class ProfileField(

    /** 归属干预类别 = 底层来源族(门控、记录 kind、UI 分区、钩子族四处共用同一项)。 */
    val kind: ProfileKind,

    /** 取值形态(控件与 schema 类型单点派生)。 */
    val type: ProfileValueType,

    /** 基线 `ModuleConfig` 同义字段名;null = Modev 超基线项。 */
    val baselineKey: String?,
) {

    // ---- 设备与系统身份 ----

    /** Build.BRAND。 */
    BRAND(ProfileKind.PROFILE_BUILD_IDENTITY, ProfileValueType.TEXT, "brand"),

    /** Build.MANUFACTURER(独立于品牌,留空即不改写)。 */
    MANUFACTURER(ProfileKind.PROFILE_BUILD_IDENTITY, ProfileValueType.TEXT, "manufacturer"),

    /** Build.MODEL。 */
    MODEL(ProfileKind.PROFILE_BUILD_IDENTITY, ProfileValueType.TEXT, "model"),

    /** Build.PRODUCT。 */
    PRODUCT(ProfileKind.PROFILE_BUILD_IDENTITY, ProfileValueType.TEXT, "product"),

    /** Build.DEVICE。 */
    DEVICE(ProfileKind.PROFILE_BUILD_IDENTITY, ProfileValueType.TEXT, "device"),

    /** Build.BOARD。 */
    BOARD(ProfileKind.PROFILE_BUILD_IDENTITY, ProfileValueType.TEXT, "board"),

    /** Build.HARDWARE。 */
    HARDWARE(ProfileKind.PROFILE_BUILD_IDENTITY, ProfileValueType.TEXT, "hardware"),

    /** Build.FINGERPRINT(结构须与品牌/产品/设备三元组自洽)。 */
    FINGERPRINT(ProfileKind.PROFILE_BUILD_IDENTITY, ProfileValueType.TEXT, "fingerPrint"),

    /** Build.ID。 */
    BUILD_ID(ProfileKind.PROFILE_BUILD_VERSION, ProfileValueType.TEXT, "buildId"),

    /** Build.VERSION.RELEASE。 */
    ANDROID_VERSION(ProfileKind.PROFILE_BUILD_VERSION, ProfileValueType.TEXT, "androidVersion"),

    /** Build.VERSION.SDK_INT。 */
    SDK_INT(ProfileKind.PROFILE_BUILD_VERSION, ProfileValueType.INT, "sdkInt"),

    // ---- 显示与算力 ----

    /** DisplayMetrics.densityDpi(生效域 72 至 1000)。 */
    DENSITY_DPI(ProfileKind.PROFILE_DENSITY, ProfileValueType.INT, "densityDpi"),

    /** CPU 核数(Runtime 与 cpuinfo 双路径;Modev 超基线项)。 */
    CPU_CORES(ProfileKind.PROFILE_CPU, ProfileValueType.INT, null),

    // ---- 唯一标识 ----

    /** Settings 中的 ANDROID_ID(十六进制)。 */
    ANDROID_ID(ProfileKind.PROFILE_ANDROID_ID, ProfileValueType.TEXT, "androidId"),

    /** IMEI(十五位数字含 Luhn 校验位;TAC 取其前八位派生)。 */
    IMEI(ProfileKind.PROFILE_TELEPHONY_ID, ProfileValueType.TEXT, "imei"),

    /** 手机号。 */
    PHONE_NUM(ProfileKind.PROFILE_TELEPHONY_ID, ProfileValueType.TEXT, "phoneNum"),

    // ---- 运营商与网络 ----

    /** PLMN(五位或六位纯数字;非法即整族放弃)。 */
    SIM_OPERATOR(ProfileKind.PROFILE_SIM, ProfileValueType.TEXT, "simOperator"),

    /** 运营商显示名。 */
    SIM_OPERATOR_NAME(ProfileKind.PROFILE_SIM, ProfileValueType.TEXT, "simOperatorName"),

    /** SIM 国家 ISO 两位码。 */
    SIM_COUNTRY(ProfileKind.PROFILE_SIM, ProfileValueType.TEXT, "simCountry"),

    /** 活动网络类型。 */
    NETWORK_TYPE(ProfileKind.PROFILE_NETWORK, ProfileValueType.NETWORK_TYPE, "networkType"),

    /** WifiInfo.getSSID(展示层自动补首尾双引号)。 */
    WIFI_SSID(ProfileKind.PROFILE_WIFI, ProfileValueType.TEXT, "wifiSSID"),

    /** WifiInfo.getBSSID 与扫描结果命中项。 */
    WIFI_BSSID(ProfileKind.PROFILE_WIFI, ProfileValueType.TEXT, "wifiBSSID"),

    /** WifiInfo.getMacAddress。 */
    WIFI_MAC_ADDRESS(ProfileKind.PROFILE_WIFI, ProfileValueType.TEXT, "wifiMacAddress"),

    // ---- 基站与定位 ----

    /** 位置区域码 LAC(TAC/NetworkId 同源派生)。 */
    LAC(ProfileKind.PROFILE_CELL, ProfileValueType.INT, "lac"),

    /** 小区标识 CID(CI/NCI/BaseStationId 同源派生)。 */
    CID(ProfileKind.PROFILE_CELL, ProfileValueType.INT, "cid"),

    /** Location 经度。 */
    LONGITUDE(ProfileKind.PROFILE_LOCATION, ProfileValueType.DOUBLE, "longitude"),

    /** Location 纬度。 */
    LATITUDE(ProfileKind.PROFILE_LOCATION, ProfileValueType.DOUBLE, "latitude"),

    /** 每进程启动时的坐标微抖动开关。 */
    RANDOM_OFFSET(ProfileKind.PROFILE_LOCATION, ProfileValueType.BOOL, "randomOffset"),

    /** 隐藏 Wi-Fi 定位来源(优先于 Wi-Fi 标识伪装)。 */
    WIFI_LOCATION_HIDE(ProfileKind.PROFILE_WIFI_LOCATION_HIDE, ProfileValueType.BOOL, "makeWifiLocationFail"),

    /** 隐藏基站定位信息(优先于基站参数伪装)。 */
    CELL_LOCATION_HIDE(ProfileKind.PROFILE_CELL_LOCATION_HIDE, ProfileValueType.BOOL, "makeCellLocationFail"),

    // ---- 区域与自身版本 ----

    /** 进程默认语言与地区。 */
    LANGUAGE(ProfileKind.PROFILE_LOCALE, ProfileValueType.TEXT, "language"),

    /** 进程默认时区(IANA 标识)。 */
    TIME_ZONE(ProfileKind.PROFILE_TIME_ZONE, ProfileValueType.TEXT, "timeZone"),

    /** 目标应用自身 longVersionCode。 */
    VERSION_CODE(ProfileKind.PROFILE_APP_VERSION, ProfileValueType.INT, "versionCode"),

    /** 目标应用自身 versionName。 */
    VERSION_NAME(ProfileKind.PROFILE_APP_VERSION, ProfileValueType.TEXT, "versionName"),

    // ---- 现场状态与策略 ----

    /** 电池容量百分比(零至一百)。 */
    BATTERY_LEVEL(ProfileKind.PROFILE_BATTERY, ProfileValueType.INT, "batteryLevel"),

    /** 截屏策略三态。 */
    SCREENSHOTS_FLAG(ProfileKind.PROFILE_SCREENSHOT, ProfileValueType.SCREENSHOT_MODE, "screenshotsFlag"),

    // ---- 隐私通行证 ----

    /** 通讯录查询返回空 Cursor。 */
    PASS_CONTACTS(ProfileKind.PROFILE_BLANK_CONTENT, ProfileValueType.BOOL, "passContacts"),

    /** 图片媒体查询返回空 Cursor。 */
    PASS_PHOTO(ProfileKind.PROFILE_BLANK_CONTENT, ProfileValueType.BOOL, "passPhoto"),

    /** 视频媒体查询返回空 Cursor。 */
    PASS_VIDEO(ProfileKind.PROFILE_BLANK_CONTENT, ProfileValueType.BOOL, "passVideo"),

    /** 音频媒体查询返回空 Cursor。 */
    PASS_AUDIO(ProfileKind.PROFILE_BLANK_CONTENT, ProfileValueType.BOOL, "passAudio"),

    /** 应用列表仅暴露自身与系统应用。 */
    PASS_APPLICATIONS(ProfileKind.PROFILE_APP_LIST, ProfileValueType.BOOL, "passApplications"),
}
