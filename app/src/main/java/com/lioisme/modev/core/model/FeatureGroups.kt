package com.lioisme.modev.core.model

/**
 * 功能域分组(V37 T3.1/T3.4):把 [CapabilityDomain.FEATURE] 的 37 个干预类别
 * 聚合为用户可理解的功能卡(每应用功能设置页与监控入口页的唯一驱动源)。
 *
 * - 组内 kind 顺序即 UI 行顺序;
 * - [recordType] 为该组干预结果的观测记录类型(「查看记录」次级入口);
 * - 新增功能域类别必须归入某组(一致性由 FeatureGroupsTest 强制:
 * 全部 FEATURE 类别 ∈ 某组 ∧ 无监听域类别混入)。
 */
enum class FeatureGroup {
    /** 弹窗关键词拦截(UI 交互域)。 */
    DIALOG_INTERCEPT,

    /** 进程守护:退出拦截 / 崩溃预防 / 解除录屏。 */
    PROCESS_GUARD,

    /** SSL 证书校验绕过(抓包环境)。 */
    SSL_BYPASS,

    /** 代理与 VPN 隐藏(抓包环境)。 */
    PROXY_VPN_HIDE,

    /** Root 与 Xposed 隐藏(环境掩蔽)。 */
    ROOT_XPOSED_HIDE,

    /** 模拟器与设备伪装(画像驱动)。 */
    DEVICE_SPOOF,
}

/** 功能组 → 类别 id 清单(单一事实源;标签资源与记录路由映射见 UI 层)。 */
object FeatureGroups {

    /** 组内类别 id(顺序稳定)。 */
    fun kindIds(group: FeatureGroup): List<String> = when (group) {
        FeatureGroup.DIALOG_INTERCEPT -> listOf(
            UiInteractionKind.DIALOG_KEYWORD.name,
        )
        FeatureGroup.PROCESS_GUARD -> listOf(
            ProcessKind.EXIT_INTERCEPT.name,
            ProcessKind.CRASH_PREVENT.name,
            ProcessKind.SCREEN_RECORD.name,
        )
        FeatureGroup.SSL_BYPASS -> listOf(
            NetworkKind.SSL_CONTEXT.name,
            NetworkKind.SSL_TRUST_MANAGER.name,
            NetworkKind.SSL_OKHTTP_PINNER.name,
            NetworkKind.SSL_OKHTTP_VERIFIER.name,
            NetworkKind.SSL_WEBVIEW.name,
            NetworkKind.SSL_NETWORK_CONFIG.name,
        )
        FeatureGroup.PROXY_VPN_HIDE -> listOf(
            NetworkKind.PROXY_SYSTEM_PROP.name,
            NetworkKind.PROXY_CONNECTIVITY.name,
            NetworkKind.PROXY_SETTINGS.name,
            NetworkKind.PROXY_OKHTTP.name,
            NetworkKind.VPN_CAPABILITIES.name,
            NetworkKind.VPN_INTERFACE.name,
            NetworkKind.VPN_NETWORK_INFO.name,
            NetworkKind.VPN_LINK_PROPERTIES.name,
        )
        FeatureGroup.ROOT_XPOSED_HIDE -> listOf(
            DeviceEnvKind.ROOT_FILE.name,
            DeviceEnvKind.ROOT_EXEC.name,
            DeviceEnvKind.ROOT_PACKAGE_MANAGER.name,
            DeviceEnvKind.ROOT_PROPERTY.name,
            DeviceEnvKind.XPOSED_CLASS.name,
            DeviceEnvKind.XPOSED_STACK.name,
            DeviceEnvKind.XPOSED_MAPS.name,
            DeviceEnvKind.SYSTEM_PROPERTIES.name,
            DeviceEnvKind.PROC_STATUS.name,
            DeviceEnvKind.PROC_MOUNT_INFO.name,
            DeviceEnvKind.PROC_CGROUP.name,
            DeviceEnvKind.PROC_NET.name,
        )
        FeatureGroup.DEVICE_SPOOF -> listOf(
            DeviceEnvKind.EMULATOR_BUILD.name,
            DeviceEnvKind.EMULATOR_FILE.name,
            DeviceEnvKind.EMULATOR_SENSOR.name,
            DeviceEnvKind.EMULATOR_NETWORK.name,
            DeviceEnvKind.EMULATOR_TELEPHONY.name,
            DeviceEnvKind.EMULATOR_CPU.name,
            DeviceEnvKind.EMULATOR_BATTERY.name,
            // V49 WP-D1:画像驱动的强制档与条件档同卡呈现(用户在一页里既能开「脏值掩蔽」
            // 也能开「按画像无条件改写」,两者互不隐含 —— FeatureGate 红线要求各自独立可关)
            DeviceEnvKind.PROFILE_BUILD_IDENTITY.name,
            DeviceEnvKind.PROFILE_BUILD_VERSION.name,
            DeviceEnvKind.PROFILE_DENSITY.name,
            DeviceEnvKind.PROFILE_CPU.name,
            DeviceEnvKind.PROFILE_BATTERY.name,
        )
    }

    /** 组的观测记录类型(「查看记录」入口;null = 无独立记录面)。 */
    fun recordType(group: FeatureGroup): RecordType? = when (group) {
        FeatureGroup.DIALOG_INTERCEPT -> RecordType.UI_INTERACTION
        FeatureGroup.PROCESS_GUARD -> RecordType.PROCESS_CONTROL
        FeatureGroup.SSL_BYPASS -> RecordType.NETWORK
        FeatureGroup.PROXY_VPN_HIDE -> RecordType.NETWORK
        FeatureGroup.ROOT_XPOSED_HIDE -> RecordType.DEVICE_ENV
        FeatureGroup.DEVICE_SPOOF -> RecordType.DEVICE_ENV
    }

    /**
     * 组内类别 (类型,id) 对(V44 R1-6:标签/开关读写统一走限定键路由)。
     * 组内全部 kind 恒属该组观测记录类型(FeatureGroupsTest 锁完备性),
     * 观测类型缺失即装配错误。
     */
    fun kindRefs(group: FeatureGroup): List<Pair<RecordType, String>> {
        val type = checkNotNull(recordType(group)) {
            "Feature group ${group.name} requires an observation record type"
        }
        return kindIds(group).map { type to it }
    }

    /** 组所属域的总开关位名(功能页 master 行;与 HookSwitch 字段一一对应)。 */
    val ALL: List<FeatureGroup> = FeatureGroup.entries
}
