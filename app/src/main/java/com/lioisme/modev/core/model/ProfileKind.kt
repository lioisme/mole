package com.lioisme.modev.core.model

/**
 * 设备画像的**唯一分类轴**(V47 T1 立,基线 Guise `HookFeature` 13 组的 Modev 细粒度化;
 * V49 R-1 收口为单轴):
 *
 * ```
 * 一个值从哪个 Android 子系统来  ≡  一个 kind  ≡  一个钩子族  ≡  一个 UI 分区  ≡  一个 MCP enum 值
 * ```
 *
 * 「底层类型」不是给 UI 贴的标签,而是**实现单元**:同一枚举项里的所有字段必须由同一族 API
 * 拦截才能得到,所以分区集恒等于钩子集,不存在「一个分区里三种改法」的杂物抽屉。
 *
 * 与类别 1:1 并存的第二条呈现轴就是第二事实源,因此这里**只允许一条轴**;
 * 曾并存过的 `ProfileSection`(`OTHER` 混装电池与截屏、`LOCALE` 混装「有钩子」与「零钩子」)
 * 已退役,勿再为 UI 方便引入分组枚举。
 *
 * 分区标题资源键见 `ProfileLabels.profileKindLabelRes`(穷尽 `when`,新增项未映射即编译期红),
 * 其四语文案恒点名来源 API。
 *
 * 归属 [CategoryTaxonomy] 的 FEATURE 域(主动干预),命名前缀 `PROFILE_` 保证记录 `kind` 列
 * 与既有 [DeviceEnvKind](掩蔽族)在观测面上可区分。
 *
 * 与 [DeviceEnvKind] 的分工:本枚举 = **画像驱动的强制档**(填了即无条件改写);
 * DeviceEnvKind = **自适应条件档**(仅在检出脏值时掩蔽)。
 */
enum class ProfileKind(

    /**
     * 该族要拦截的 **Android API 标识**(纯 ASCII 代码标识符,不是文案 ⇒ 不进四语表)。
     *
     * UI 分区标题以参数注入它(`profile_section_title` 格式串),使「这一组改的是哪个子系统」
     * 在屏上直接可读 —— R-1 的「按底层类型分类」若只存在于枚举名里,用户仍然要凭记忆。
     */
    val sourceApi: String,
) {

    // ---- 设备与系统身份(android.os.*) ----

    /** `android.os.Build` 静态字段与其 OEM getter 族(BRAND / MANUFACTURER / MODEL / PRODUCT / DEVICE / BOARD / HARDWARE / FINGERPRINT)。 */
    PROFILE_BUILD_IDENTITY("android.os.Build"),

    /** `android.os.Build.VERSION` 与 `Build.ID`(市场版本号 / SDK_INT / 构建号)。 */
    PROFILE_BUILD_VERSION("android.os.Build.VERSION"),

    // ---- 显示与算力(平台资源与 Runtime) ----

    /** `Resources` / `DisplayMetrics` / `Configuration`(densityDpi 及其派生的 density、scaledDensity、屏幕 dp 三值)。 */
    PROFILE_DENSITY("Resources.DisplayMetrics"),

    /** `Runtime.availableProcessors` 与 `/proc/cpuinfo`(Modev 超基线项)。 */
    PROFILE_CPU("Runtime / proc.cpuinfo"),

    // ---- 唯一标识(两类来源,两种钩法,故 V49 拆开) ----

    /** `Settings.Secure` / `Settings.System` 的字符串读口(SSAID)。 */
    PROFILE_ANDROID_ID("Settings.Secure / System"),

    /** `TelephonyManager` 与 `SubscriptionManager` 的标识读口(IMEI 与由其派生的 TAC / 手机号)。 */
    PROFILE_TELEPHONY_ID("TelephonyManager / SubscriptionManager"),

    /** `TelephonyManager` 运营商族 + `SubscriptionInfo` + 六类 `CellIdentity` 的 MCC/MNC 派生值。 */
    PROFILE_SIM("TelephonyManager.SimOperator"),

    // ---- 网络与连接 ----

    /** `ConnectivityManager` / `NetworkInfo` / `NetworkCapabilities` 三族(活动网络类型)。 */
    PROFILE_NETWORK("ConnectivityManager"),

    /** `WifiManager` / `WifiInfo` / `ScanResult`(SSID / BSSID / MAC 与扫描结果命中项)。 */
    PROFILE_WIFI("WifiManager / WifiInfo"),

    // ---- 基站与定位 ----

    /** `GsmCellLocation` 与六类 `CellIdentity`(LAC/TAC/NetworkId 与 CID/CI/NCI/BaseStationId 同源派生)。 */
    PROFILE_CELL("android.telephony.CellIdentity"),

    /** `LocationManager` 与 `Location`(坐标与可选每进程随机偏移)。 */
    PROFILE_LOCATION("android.location.LocationManager"),

    /** Wi-Fi 定位来源压制:扫描结果与连接信息归零(优先于 [PROFILE_WIFI])。 */
    PROFILE_WIFI_LOCATION_HIDE("WifiManager.getScanResults"),

    /** 基站定位信息压制:同步/异步/回调/旧式监听与六类 identity(优先于 [PROFILE_CELL])。 */
    PROFILE_CELL_LOCATION_HIDE("TelephonyManager.getAllCellInfo"),

    // ---- 区域与自身版本 ----

    /** `Locale` / `LocaleList` / `Configuration` / `Resources` / `LocaleManager`(进程默认语言与地区)。 */
    PROFILE_LOCALE("java.util.Locale / LocaleList"),

    /** `java.util.TimeZone` 进程默认值 —— **零钩子**实现,与 [PROFILE_LOCALE] 是两种机制,故不并项。 */
    PROFILE_TIME_ZONE("java.util.TimeZone.setDefault"),

    /** `ApplicationPackageManager.getPackageInfo` 四签名(仅目标应用自身包)。 */
    PROFILE_APP_VERSION("PackageManager.getPackageInfo"),

    // ---- 现场状态与策略 ----

    /** `BatteryManager.getIntProperty` 与 `ACTION_BATTERY_CHANGED` sticky Intent extras。 */
    PROFILE_BATTERY("android.os.BatteryManager"),

    /** `Window` / `WindowManager` 的 flags 与 Activity 生命周期(截屏策略三态)。 */
    PROFILE_SCREENSHOT("android.view.Window"),

    // ---- 隐私通行证(内容侧与列表侧两种钩法) ----

    /** `ContentResolver.query` / `ContentProviderClient.query` → 保留列结构的空 `MatrixCursor`(通讯录 / 图片 / 视频 / 音频)。 */
    PROFILE_BLANK_CONTENT("ContentResolver.query"),

    /** `PackageManager` 枚举族(含 `queryIntentActivities` 与 `getPackagesHoldingPermissions`)→ 仅暴露自身与系统应用。 */
    PROFILE_APP_LIST("PackageManager enumeration"),
}
