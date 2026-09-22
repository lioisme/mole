package com.lioisme.modev.core.model

/**
 * 设备画像干预类别(V47 T1,基线 Guise `HookFeature` 13 组的 Modev 细粒度化):
 * **一个用户可见开关 = 一个 kind**,使注入侧门控、UI 行、MCP 声明面、压测期望表
 * 四者严格一对一(基线把 Wi-Fi/SIM 折进 NETWORK、把两类定位隐藏折进 LOCATION,
 * Modev 拆开以对齐 FeatureGate 红线)。
 *
 * 归属 [CategoryTaxonomy] 的 FEATURE 域(主动干预),命名前缀 `PROFILE_` 保证
 * 记录 `kind` 列与既有 [DeviceEnvKind](掩蔽族)在观测面上可区分。
 *
 * 与 [DeviceEnvKind] 的分工:本枚举 = **画像驱动的强制档**(填了即无条件写入);
 * DeviceEnvKind = **自适应条件档**(仅在检出脏值时掩蔽)。
 */
enum class ProfileKind {

    /** Build 与 Build.VERSION 字段组(品牌/制造商/型号/产品/设备/主板/硬件/Build ID/Fingerprint/版本/SDK)。 */
    PROFILE_BUILD,

    /** 显示密度(densityDpi 及其派生的 density/scaledDensity/屏幕 dp 三值)。 */
    PROFILE_DENSITY,

    /** CPU 核数(Runtime.availableProcessors 与 /proc/cpuinfo;Modev 超基线项)。 */
    PROFILE_CPU,

    /** 活动网络类型(NetworkInfo/NetworkCapabilities/ConnectivityManager 三族)。 */
    PROFILE_NETWORK,

    /** Wi-Fi 标识(SSID/BSSID/MAC 与扫描结果命中项)。 */
    PROFILE_WIFI,

    /** SIM 运营商(PLMN/运营商名/国家 ISO,含 CellIdentity 与 SubscriptionInfo 派生值)。 */
    PROFILE_SIM,

    /** 唯一标识(Android ID/IMEI 与 TAC/手机号)。 */
    PROFILE_UNIQUE_ID,

    /** 基站参数(LAC/TAC 与 CID/CI/NCI/BaseStationId)。 */
    PROFILE_CELL,

    /** 定位坐标(经纬度与可选每进程随机偏移)。 */
    PROFILE_LOCATION,

    /** 隐藏 Wi-Fi 定位来源(扫描结果与连接信息归零;优先于 [PROFILE_WIFI])。 */
    PROFILE_WIFI_LOCATION_HIDE,

    /** 隐藏基站定位信息(同步/异步/回调/旧式监听与六类 identity;优先于 [PROFILE_CELL])。 */
    PROFILE_CELL_LOCATION_HIDE,

    /** 语言与地区(进程默认 Locale/LocaleList/Configuration/per-app locales)。 */
    PROFILE_LOCALE,

    /** 默认时区(进程 TimeZone 默认值;零钩子实现)。 */
    PROFILE_TIME_ZONE,

    /** 目标应用自身版本号与版本名(PackageInfo 查询)。 */
    PROFILE_APP_VERSION,

    /** 电池电量(BatteryManager 容量与电池广播 Intent extras)。 */
    PROFILE_BATTERY,

    /** 截屏策略三态(不干预/强制允许/强制禁止)。 */
    PROFILE_SCREENSHOT,

    /** 空白通行证内容侧(通讯录/图片/视频/音频查询返回保留列结构的空 Cursor)。 */
    PROFILE_BLANK_CONTENT,

    /** 空白通行证应用列表侧(仅暴露自身与系统应用)。 */
    PROFILE_APP_LIST,
}
