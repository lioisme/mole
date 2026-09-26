package com.lioisme.modev.core.model

/**
 * 设备环境监听操作:具体 API 动作,[kind] 归属到类别。
 * kind = 用户开关粒度([HookSwitch.deviceEnvEnabled] 门控),
 * operation = 列表元行/详情页展示粒度。
 */
enum class DeviceEnvOperation(
    val kind: DeviceEnvKind,
) {
    // ---- Root 文件检测 ----

    /** java.io.File.exists 拦截(命中 root 路径)。 */
    FILE_EXISTS(DeviceEnvKind.ROOT_FILE),

    /** java.io.File.length 拦截。 */
    FILE_LENGTH(DeviceEnvKind.ROOT_FILE),

    /** java.io.File.canRead 拦截。 */
    FILE_READABLE(DeviceEnvKind.ROOT_FILE),

    // ---- Root 命令执行 ----

    /** Runtime.exec 命令拦截(命中 su)。 */
    EXEC_COMMAND(DeviceEnvKind.ROOT_EXEC),

    /** ProcessBuilder.start 命令拦截。 */
    PROCESS_BUILDER(DeviceEnvKind.ROOT_EXEC),

    // ---- Root 包查询 ----

    /** PackageManager.getInstalledPackages 过滤。 */
    GET_INSTALLED_PACKAGES(DeviceEnvKind.ROOT_PACKAGE_MANAGER),

    /** PackageManager.getInstalledApplications 过滤。 */
    GET_INSTALLED_APPLICATIONS(DeviceEnvKind.ROOT_PACKAGE_MANAGER),

    /** PackageManager.queryIntentActivities 过滤。 */
    QUERY_INTENT_ACTIVITIES(DeviceEnvKind.ROOT_PACKAGE_MANAGER),

    // ---- Root 属性 ----

    /** System.getProperty 读取拦截(ro.debuggable 等)。 */
    GET_PROPERTY(DeviceEnvKind.ROOT_PROPERTY),

    /** Build.TAGS 字段拦截。 */
    BUILD_TAGS(DeviceEnvKind.ROOT_PROPERTY),

    // 以下 7 个值曾恒零发射却仍在四语标签与筛选菜单里可见(选中即恒空 = 用户以为
    // 「没有这类活动」),已整体退役。每一个都有已在发射的同义项或不可挂钩的理由,
    // 因此**禁止复活**;要新增操作,先在下表里找到自己的归并去向:
    //   GETPROP            → SYS_PROP_GET / GET_PROPERTY(属性读取已覆盖;getprop 子进程由
    //                        RootExecHooker 拦截并以 EXEC_COMMAND 记录)
    //   CLASS_FOR_NAME     → LOAD_CLASS / FIND_CLASS(**V36 D-17 硬约束禁止钩 Class.forName**
    //                        —— 该方法经 Reflection.getCallerClass 取调用方 classloader,
    //                        LSPlant 桥接帧会替换真实调用方 ⇒ 目标 App 自身类加载全崩,
    //                        且钩子无条件安装、开关关闭也崩。删除,永不补钩)
    //   OPEN_MAPS          → READ_MAPS / SYS_FILE_EXISTS
    //   GET_DEVICE_ID      → PHONE_DEVICE_ID
    //   GET_IMEI           → PHONE_IMEI
    //   ROOT_CHECK_API     → ROOT_CHECK_FILE(DetectionMonitorHooker 的 monitorClassForName
    //                        正是因上述 D-17 主动跳过 ⇒ 本操作无可达钩点)
    //   CONTENT_QUERY_SETTINGS → SETTINGS_SECURE_GET / SETTINGS_GLOBAL_GET
    // V49 已登记的收口纪律同样适用:照抄上游钩点名单前,先在测试里证明它存在。

    // ---- Xposed 类存在性 ----

    /** ClassLoader.loadClass 拦截。 */
    LOAD_CLASS(DeviceEnvKind.XPOSED_CLASS),

    /** ClassLoader.findClass 拦截。 */
    FIND_CLASS(DeviceEnvKind.XPOSED_CLASS),

    // ---- Xposed 堆栈 ----

    /** Throwable.getStackTrace 过滤。 */
    GET_STACK_TRACE(DeviceEnvKind.XPOSED_STACK),

    /** Thread.stackTrace 过滤。 */
    THREAD_STACK(DeviceEnvKind.XPOSED_STACK),

    // ---- Xposed Maps ----

    /** /proc/self/maps 读取过滤。 */
    READ_MAPS(DeviceEnvKind.XPOSED_MAPS),

    // ---- 模拟器 Build ----

    /** Build.FINGERPRINT 替换。 */
    BUILD_FINGERPRINT(DeviceEnvKind.EMULATOR_BUILD),

    /** Build.MODEL 替换。 */
    BUILD_MODEL(DeviceEnvKind.EMULATOR_BUILD),

    /** Build.PRODUCT 替换。 */
    BUILD_PRODUCT(DeviceEnvKind.EMULATOR_BUILD),

    /** Build.MANUFACTURER 替换。 */
    BUILD_MANUFACTURER(DeviceEnvKind.EMULATOR_BUILD),

    /** Build.DEVICE 替换。 */
    BUILD_DEVICE(DeviceEnvKind.EMULATOR_BUILD),

    /** Build.BOARD 替换。 */
    BUILD_BOARD(DeviceEnvKind.EMULATOR_BUILD),

    /** Build.HARDWARE 替换。 */
    BUILD_HARDWARE(DeviceEnvKind.EMULATOR_BUILD),

    /** Build.BRAND 替换。 */
    BUILD_BRAND(DeviceEnvKind.EMULATOR_BUILD),

    /** Build.HOST 替换。 */
    BUILD_HOST(DeviceEnvKind.EMULATOR_BUILD),

    /** Build.USER 替换。 */
    BUILD_USER(DeviceEnvKind.EMULATOR_BUILD),

    /** Build.DISPLAY 替换。 */
    BUILD_DISPLAY(DeviceEnvKind.EMULATOR_BUILD),

    /** Build.SERIAL 替换。 */
    BUILD_SERIAL(DeviceEnvKind.EMULATOR_BUILD),

    /** Build.TYPE 替换。 */
    BUILD_TYPE(DeviceEnvKind.EMULATOR_BUILD),

    // ---- 模拟器文件 ----

    /** /dev/qemu_pipe 存在性拦截。 */
    DEV_FILE_EXISTS(DeviceEnvKind.EMULATOR_FILE),

    /** /system 文件存在性拦截。 */
    SYS_FILE_EXISTS(DeviceEnvKind.EMULATOR_FILE),

    /** 库文件存在性拦截。 */
    LIB_FILE_EXISTS(DeviceEnvKind.EMULATOR_FILE),

    // ---- 模拟器传感器 ----

    /** SensorManager.getDefaultSensor 拦截。 */
    GET_DEFAULT_SENSOR(DeviceEnvKind.EMULATOR_SENSOR),

    /** SensorManager.getSensorList 拦截。 */
    SENSOR_LIST(DeviceEnvKind.EMULATOR_SENSOR),

    // ---- 模拟器网络 ----

    /** WifiInfo.getMacAddress 拦截。 */
    GET_MAC_ADDRESS(DeviceEnvKind.EMULATOR_NETWORK),

    /** Settings.Secure.ANDROID_ID 拦截。 */
    ANDROID_ID(DeviceEnvKind.EMULATOR_NETWORK),

    // ---- 模拟器电话信息 ----

    /** TelephonyManager.getDeviceId 拦截。 */
    PHONE_DEVICE_ID(DeviceEnvKind.EMULATOR_TELEPHONY),

    /** TelephonyManager.getImei 拦截(双卡)。 */
    PHONE_IMEI(DeviceEnvKind.EMULATOR_TELEPHONY),

    /** TelephonyManager.getSubscriberId 拦截(IMSI)。 */
    PHONE_SUBSCRIBER_ID(DeviceEnvKind.EMULATOR_TELEPHONY),

    /** TelephonyManager.getSimSerialNumber 拦截。 */
    PHONE_SIM_SERIAL(DeviceEnvKind.EMULATOR_TELEPHONY),

    /** TelephonyManager.getLine1Number 拦截。 */
    PHONE_LINE1_NUMBER(DeviceEnvKind.EMULATOR_TELEPHONY),

    // ---- 模拟器CPU信息 ----

    /** Runtime.availableProcessors 拦截。 */
    CPU_AVAILABLE_PROCESSORS(DeviceEnvKind.EMULATOR_CPU),

    // V50 孤儿整改(V-OPS-2):CPU_PROC_INFO / CPU_SYSCONF 随其钩点一并退役 ——
    //   CPU_PROC_INFO:V49 WP-D1 已把「/proc/cpuinfo 正文改写」整条死路删除(V49-DFC-13),
    //     但枚举值留了下来 ⇒ 筛选菜单上是一个恒空选项(比无选项更糟:它谎报能力)。
    //   CPU_SYSCONF:libc `sysconf(_SC_NPROCESSORS)` 不经任何 Java 方法,无可达钩点。
    // 二者共同的真实读口 = CPU_AVAILABLE_PROCESSORS。

    // ---- 模拟器电池信息 ----

    /** BatteryManager.getIntProperty 拦截。 */
    BATTERY_INT_PROPERTY(DeviceEnvKind.EMULATOR_BATTERY),

    /** BatteryManager.isCharging 拦截。 */
    BATTERY_IS_CHARGING(DeviceEnvKind.EMULATOR_BATTERY),

    /** Intent.ACTION_BATTERY_CHANGED 拦截。 */
    BATTERY_CHANGED(DeviceEnvKind.EMULATOR_BATTERY),

    // ---- 系统属性 ----

    /** android.os.SystemProperties.get 读取拦截(ro.debuggable 等)。 */
    SYS_PROP_GET(DeviceEnvKind.SYSTEM_PROPERTIES),

    // ---- /proc/self/status  ----

    /** /proc/self/status 行读取过滤(TracerPid)。 */
    PROC_STATUS_READ(DeviceEnvKind.PROC_STATUS),

    // ---- /proc/self/mountinfo  ----

    /** /proc/self/mountinfo 行读取过滤(Magisk overlay)。 */
    PROC_MOUNT_READ(DeviceEnvKind.PROC_MOUNT_INFO),

    // ---- /proc/self/cgroup  ----

    /** /proc/self/cgroup 行读取过滤(Magisk cgroup)。 */
    PROC_CGROUP_READ(DeviceEnvKind.PROC_CGROUP),

    // ---- /proc/net/tcp  ----

    /** /proc/net/tcp 行读取过滤(Frida 端口)。 */
    PROC_NET_READ(DeviceEnvKind.PROC_NET),

    // ---- Settings Global  ----

    /** Settings.Global get 读取拦截(ADB/开发者选项)。 */
    SETTINGS_GLOBAL_GET(DeviceEnvKind.ROOT_PROPERTY),

    /** Settings.Secure get 读取拦截(OEM unlock)。 */
    SETTINGS_SECURE_GET(DeviceEnvKind.ROOT_PROPERTY),

    // ---- 检测行为监控 ----

    /** Root 检测文件扫描记录。 */
    ROOT_CHECK_FILE(DeviceEnvKind.DETECT_ROOT),

    /** Root 检测命令执行记录。 */
    ROOT_CHECK_EXEC(DeviceEnvKind.DETECT_ROOT),

    /** 模拟器 Build 检查记录。 */
    EMULATOR_CHECK_BUILD(DeviceEnvKind.DETECT_EMULATOR),

    /** 模拟器文件检查记录。 */
    EMULATOR_CHECK_FILE(DeviceEnvKind.DETECT_EMULATOR),

    /** 模拟器属性检查记录。 */
    EMULATOR_CHECK_PROP(DeviceEnvKind.DETECT_EMULATOR),

    // ---- 画像驱动的强制档 ----

    /** `android.os.Build` 静态身份字段被画像值无条件改写(一项一条记录,字段名在载荷里)。 */
    BUILD_IDENTITY_WRITE(DeviceEnvKind.PROFILE_BUILD_IDENTITY),

    /** `android.os.Build.VERSION` / `Build.ID` 被画像值无条件改写。 */
    BUILD_VERSION_WRITE(DeviceEnvKind.PROFILE_BUILD_VERSION),

    /** `DisplayMetrics` / `Configuration` 被画像密度改写(每一处被改的实例一条记录)。 */
    DENSITY_WRITE(DeviceEnvKind.PROFILE_DENSITY),

    /** 核数被画像值无条件改写(`availableProcessors` 返回值与 `/proc/cpuinfo` 正文)。 */
    CPU_WRITE(DeviceEnvKind.PROFILE_CPU),

    /** 电池读数被画像值无条件改写(`getIntProperty` 与 sticky Intent 的 extras)。 */
    BATTERY_WRITE(DeviceEnvKind.PROFILE_BATTERY),

    /** `Settings.Secure` / `Settings.System` 的 `android_id` 被画像值改写。 */
    ANDROID_ID_WRITE(DeviceEnvKind.PROFILE_ANDROID_ID),

    /** IMEI / TAC / 手机号读口被画像值改写(方法名在载荷里)。 */
    TELEPHONY_ID_WRITE(DeviceEnvKind.PROFILE_TELEPHONY_ID),

    /** 运营商码 / 名称 / 国家码及其派生 MCC/MNC 被画像值改写。 */
    SIM_WRITE(DeviceEnvKind.PROFILE_SIM),
}
