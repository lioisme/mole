package com.lioisme.modev.core.util

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build

/**
 * 弃用 API 现代化(PackageManager,2026-08-18 V16 R6 合规):
 * `getInstalledApplications(int)` 于 API 33 提供带 `ApplicationInfoFlags` 重载
 * (嵌套于 PackageManager),旧系统走弃用路径(行为等价:flags 均为 0)。
 * 注意:`getLaunchIntentForPackage(String)` 全 API 无 flags 重载且从未弃用,
 * 各调用点直接使用原 API 即可,不必经本文件。
 */
fun PackageManager.installedApplications(): List<ApplicationInfo> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        @Suppress("NewApi")
        getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        getInstalledApplications(0)
    }