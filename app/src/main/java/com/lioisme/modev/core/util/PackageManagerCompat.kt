package com.lioisme.modev.core.util

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build

/**
 * 弃用 API 现代化(PackageManager,2026-08-18 V16 R6 合规;V17 R1 flags 入参):
 * `getInstalledApplications(int)` 于 API 33 提供带 `ApplicationInfoFlags` 重载
 * (嵌套于 PackageManager),旧系统走弃用路径(行为等价)。
 * flags 由调用方按需传入(如 GET_META_DATA,模块识别需读 meta-data)。
 * 注意:`getLaunchIntentForPackage(String)` 全 API 无 flags 重载且从未弃用,
 * 各调用点直接使用原 API 即可,不必经本文件。
 */
fun PackageManager.installedApplications(flags: Int): List<ApplicationInfo> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        @Suppress("NewApi")
        getInstalledApplications(PackageManager.ApplicationInfoFlags.of(flags.toLong()))
    } else {
        @Suppress("DEPRECATION")
        getInstalledApplications(flags)
    }

fun PackageManager.installedApplications(): List<ApplicationInfo> = installedApplications(0)