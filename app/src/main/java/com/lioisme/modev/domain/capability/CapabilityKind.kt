package com.lioisme.modev.domain.capability

/**
 * 监听能力类型(V10 能力化架构;V14 R5 更名「算法监听」;V17 数据库监听扩展)。
 * 未来行为监听、交互监听、文件监听等按本枚举扩展,
 * 宿主 UI 与路由按 [CapabilityKind] 泛化驱动,新增能力不改动主链路。
 */
enum class CapabilityKind {
    /** 算法监听(加解密/摘要/签名/MAC/密钥/编码等 JCA 调用)。 */
    ALGORITHM_LISTEN,

    /** 数据库监听(SQLite 打开/关闭/增删改查/事务/绑定参数/结果集)。 */
    DATABASE_LISTEN,

    /** 行为监听(文件系统读/写/删/重命名/列目录、Assets 资源、Shell 命令,V27 一等公民)。 */
    BEHAVIOR_LISTEN,

    /** 偏好监听(SharedPreferences 读入配置文件 + Get/Put 全家桶,V28 一等公民)。 */
    PREFS_LISTEN,

    /** 网络环境监听(SSL 绕过/代理隐藏/VPN 隐藏/请求监控,V30 一等公民)。 */
    NETWORK_LISTEN,

    /** 设备环境监听(Root 隐藏/Xposed 隐藏/模拟器隐藏/检测监控,V31 一等公民)。 */
    DEVICE_ENV_LISTEN,

    /** UI 交互监听(弹窗/控件文本/Activity 跳转,V33 一等公民)。 */
    UI_INTERACTION_LISTEN,

    /** 系统信息监听(Logcat/App 元数据/签名,V33 一等公民)。 */
    SYSTEM_LISTEN,

    /** 进程控制(退出拦截/崩溃防止/录屏绕过,V33 一等公民)。 */
    PROCESS_CONTROL_LISTEN,
}
