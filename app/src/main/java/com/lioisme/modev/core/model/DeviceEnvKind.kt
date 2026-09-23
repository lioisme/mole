package com.lioisme.modev.core.model

/**
 * 设备环境监听类别(V31;V35 momo 增强扩展至 21 项):
 * 覆盖 Root 检测干预 / Xposed 检测干预 / 模拟器检测干预 /
 * 系统属性 Hook / 进程文件过滤 / 检测行为监控六大子域。
 *
 * 默认启用态与 [CategoryTaxonomy] 噪点分级一致:
 * - 干预类(Root/Xposed/Emulator/SysProp/Proc)默认关(安全优先,需显式启用);
 * - 观测类(DETECT_ROOT/DETECT_EMULATOR)默认关(检测行为监控噪声大)。
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

    /** 模拟器电话信息伪造(IMEI/DeviceId/SubscriberId)。 */
    EMULATOR_TELEPHONY,

    /** 模拟器CPU信息伪造(核心数/频率/proc/cpuinfo)。 */
    EMULATOR_CPU,

    /** 模拟器电池信息伪造(电量/状态)。 */
    EMULATOR_BATTERY,

    // ---- 系统属性 / 进程文件检测干预(V35 momo 增强) ----

    /** android.os.SystemProperties.get() 拦截(ro.debuggable/ro.build.tags 等)。 */
    SYSTEM_PROPERTIES,

    /** /proc/self/status 行过滤(TracerPid/Frida 检测)。 */
    PROC_STATUS,

    /** /proc/self/mountinfo 行过滤(Magisk overlay mounts)。 */
    PROC_MOUNT_INFO,

    /** /proc/self/cgroup 行过滤(Magisk hidden processes)。 */
    PROC_CGROUP,

    /** /proc/net/tcp 行过滤(Frida 端口隐藏)。 */
    PROC_NET,

    // ---- 检测行为监控(观测类,默认关) ----

    /** Root 检测行为监控(仅记录,不干预)。 */
    DETECT_ROOT,

    /** 模拟器检测行为监控(仅记录,不干预)。 */
    DETECT_EMULATOR,

    // ---- 画像驱动的强制档(V49 R-1 分类轴 = 底层来源族;WP-D 逐批登记) ----
    //
    // 与上面 21 项的根本差别:上面是**条件档**(只在检出脏值时掩蔽,值源为内置基线),
    // 本组是**强制档**(画像填了该项就无条件改写,值源恒为 `FeatureConfig.deviceProfile`)。
    // 默认 **true** 的理由:用户往画像里填值这一步本身就是显式意图,真正的闸是
    // 「该项是否被填」(`ProfileField.isSetIn` 为空 ⇒ 钩子一族都不安装);
    // 若沿用条件档的默认 false,绑定画像后还要逐个打开 20 个开关才生效 —— 那既不是
    // 「填了就生效」,也让 R-4 的逐字段验证无从成立。仍可随时显式关(FeatureGate 红线不破)。

    /** `android.os.Build` 静态身份字段强制伪造(BRAND/MANUFACTURER/MODEL/PRODUCT/DEVICE/BOARD/HARDWARE/FINGERPRINT)。 */
    PROFILE_BUILD_IDENTITY,

    /** `android.os.Build.VERSION` 与 `Build.ID` 强制伪造(市场版本号 / SDK_INT / 构建号)。 */
    PROFILE_BUILD_VERSION,
}
