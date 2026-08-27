package com.lioisme.modev.core.model

/**
 * 设备环境监听类别(V31):覆盖 Root 检测干预 / Xposed 检测干预 / 模拟器检测干预 / 检测行为监控四大子域。
 *
 * 默认启用态与 [CategoryTaxonomy] 噪点分级一致:
 * - 干预类(Root/Xposed/Emulator)默认开:使隐藏生效;
 * - 观测类(DETECT_ROOT/DETECT_EMULATOR)默认关:检测行为监控噪声大。
 */
enum class DeviceEnvKind {
    // ---- Root 检测干预(干预类) ----

    /** Root 文件存在性检测干预(su/busybox/magisk 路径)。 */
    ROOT_FILE,

    /** Root 命令执行检测干预(Runtime.exec su)。 */
    ROOT_EXEC,

    /** Root 包管理器查询干预(root 管理 app 过滤)。 */
    ROOT_PACKAGE_MANAGER,

    /** Root 系统属性检测干预(Build.TAGS/ro.debuggable 等)。 */
    ROOT_PROPERTY,

    // ---- Xposed 检测干预(干预类) ----

    /** Xposed 类存在性检测干预(Class.forName XposedBridge)。 */
    XPOSED_CLASS,

    /** Xposed 堆栈检测干预(调用栈含 xposed 帧)。 */
    XPOSED_STACK,

    /** /proc/self/maps 扫描干预(maps 含 xposed/lspd 路径)。 */
    XPOSED_MAPS,

    // ---- 模拟器检测干预(干预类) ----

    /** 模拟器 Build 属性伪造(fingerprint/model/product)。 */
    EMULATOR_BUILD,

    /** 模拟器设备文件检测干预(qemu_pipe/goldfish_pipe)。 */
    EMULATOR_FILE,

    /** 模拟器传感器伪造(假加速度计/陀螺仪)。 */
    EMULATOR_SENSOR,

    /** 模拟器网络/MAC/IMEI 伪造。 */
    EMULATOR_NETWORK,

    // ---- 检测行为监控(观测类,默认关) ----

    /** Root 检测行为监控(仅记录,不干预)。 */
    DETECT_ROOT,

    /** 模拟器检测行为监控(仅记录,不干预)。 */
    DETECT_EMULATOR,
}
