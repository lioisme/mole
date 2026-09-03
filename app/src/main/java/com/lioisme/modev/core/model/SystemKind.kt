package com.lioisme.modev.core.model

/**
 * 系统信息监听类别(V33):覆盖 Logcat/App 元数据/签名三大子域。
 *
 * 默认启用态与 [CategoryTaxonomy] 噪点分级一致:
 * - 高信号默认开:APP_INFO/SIGNATURE;
 * - 高频噪声默认关:LOGCAT(全量日志极高频)。
 */
enum class SystemKind {
    // ---- Logcat ----

    /** Logcat 输出捕获(极高频噪声,默认关)。 */
    LOGCAT,

    // ---- Application 信息 ----

    /** Application 元数据(版本/权限等)。 */
    APP_INFO,

    // ---- 签名 ----

    /** 签名读取监听(PackageManager.getPackageInfo GET_SIGNATURES)。 */
    SIGNATURE,
}
