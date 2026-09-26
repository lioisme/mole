package com.lioisme.modev.core.model

/**
 * 系统信息监听操作(V33):具体 API 动作,[kind] 归属到类别。
 */
enum class SystemOperation(
    val kind: SystemKind,
) {
    // ---- Logcat ----

    /** Log.v() 调用。 */
    LOG_VERBOSE(SystemKind.LOGCAT),

    /** Log.d() 调用。 */
    LOG_DEBUG(SystemKind.LOGCAT),

    /** Log.i() 调用。 */
    LOG_INFO(SystemKind.LOGCAT),

    /** Log.w() 调用。 */
    LOG_WARN(SystemKind.LOGCAT),

    /** Log.e() 调用。 */
    LOG_ERROR(SystemKind.LOGCAT),

    /** Log.wtf() 调用。 */
    LOG_FATAL(SystemKind.LOGCAT),

    // ---- Application 信息 ----
    // V50 孤儿整改:删除 `APP_ON_CREATE`(恒零发射 + 筛选恒空)。Application 元数据
    // 在首次 getPackageInfo 即由 APP_VERSION 采到,另挂 Application.onCreate 是新功能而非补漏。

    /** PackageManager.getPackageInfo() 读取版本。 */
    APP_VERSION(SystemKind.APP_INFO),

    /** PackageManager.getPackageInfo(GET_PERMISSIONS) 读取权限。 */
    APP_PERMISSIONS(SystemKind.APP_INFO),

    // ---- 签名 ----

    /** PackageManager.getPackageInfo(GET_SIGNATURES) 读取签名。 */
    SIGNATURE_READ(SystemKind.SIGNATURE),

    /** PackageManager.getPackageInfo(GET_SIGNING_CERTIFICATES) 读取签名。 */
    SIGNATURE_READ_V2(SystemKind.SIGNATURE),
}
