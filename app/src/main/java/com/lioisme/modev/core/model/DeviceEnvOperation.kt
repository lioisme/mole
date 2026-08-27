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
