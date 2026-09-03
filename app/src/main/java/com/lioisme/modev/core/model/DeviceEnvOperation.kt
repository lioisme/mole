package com.lioisme.modev.core.model

/**
 * 设备环境监听操作(V31):具体 API 动作,[kind] 归属到类别。
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

    /** getprop 命令拦截。 */
    GETPROP(DeviceEnvKind.ROOT_PROPERTY),

    // ---- Xposed 类存在性 ----

    /** Class.forName 拦截(命中 xposed 类名)。 */
    CLASS_FOR_NAME(DeviceEnvKind.XPOSED_CLASS),

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

    /** maps 文件打开拦截。 */
    OPEN_MAPS(DeviceEnvKind.XPOSED_MAPS),

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

    /** TelephonyManager.getDeviceId 拦截。 */
    GET_DEVICE_ID(DeviceEnvKind.EMULATOR_NETWORK),

    /** TelephonyManager.getImei 拦截。 */
    GET_IMEI(DeviceEnvKind.EMULATOR_NETWORK),

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

    /** /proc/cpuinfo 读取拦截。 */
    CPU_PROC_INFO(DeviceEnvKind.EMULATOR_CPU),

    /** os.sysconf 读取拦截。 */
    CPU_SYSCONF(DeviceEnvKind.EMULATOR_CPU),

    // ---- 模拟器电池信息 ----

    /** BatteryManager.getIntProperty 拦截。 */
    BATTERY_INT_PROPERTY(DeviceEnvKind.EMULATOR_BATTERY),

    /** BatteryManager.isCharging 拦截。 */
    BATTERY_IS_CHARGING(DeviceEnvKind.EMULATOR_BATTERY),

    /** Intent.ACTION_BATTERY_CHANGED 拦截。 */
    BATTERY_CHANGED(DeviceEnvKind.EMULATOR_BATTERY),

    // ---- 系统属性(V35) ----

    /** android.os.SystemProperties.get 读取拦截(ro.debuggable 等)。 */
    SYS_PROP_GET(DeviceEnvKind.SYSTEM_PROPERTIES),

    // ---- /proc/self/status (V35) ----

    /** /proc/self/status 行读取过滤(TracerPid)。 */
    PROC_STATUS_READ(DeviceEnvKind.PROC_STATUS),

    // ---- /proc/self/mountinfo (V35) ----

    /** /proc/self/mountinfo 行读取过滤(Magisk overlay)。 */
    PROC_MOUNT_READ(DeviceEnvKind.PROC_MOUNT_INFO),

    // ---- /proc/self/cgroup (V35) ----

    /** /proc/self/cgroup 行读取过滤(Magisk cgroup)。 */
    PROC_CGROUP_READ(DeviceEnvKind.PROC_CGROUP),

    // ---- /proc/net/tcp (V35) ----

    /** /proc/net/tcp 行读取过滤(Frida 端口)。 */
    PROC_NET_READ(DeviceEnvKind.PROC_NET),

    // ---- Settings Global (V35) ----

    /** Settings.Global get 读取拦截(ADB/开发者选项)。 */
    SETTINGS_GLOBAL_GET(DeviceEnvKind.ROOT_PROPERTY),

    /** Settings.Secure get 读取拦截(OEM unlock)。 */
    SETTINGS_SECURE_GET(DeviceEnvKind.ROOT_PROPERTY),

    /** ContentResolver.query Settings 过滤。 */
    CONTENT_QUERY_SETTINGS(DeviceEnvKind.ROOT_PROPERTY),

    // ---- 检测行为监控 ----

    /** Root 检测 API 调用记录。 */
    ROOT_CHECK_API(DeviceEnvKind.DETECT_ROOT),

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
}
