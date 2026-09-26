package com.lioisme.modev.ui.settings

import java.util.Locale

/**
 * 存储占用格式化(V19 T7,纯函数):字节 → 人类可读大小。
 * 档位:≥1 GiB → GiB、≥1 MiB → MiB、≥1 KiB → KiB、以下 → B;
 * 小数为 1 位,单位恒大写(平台惯例,不做 locale 本地化)。
 * 记录数/统计类数值仍走 [AppCountFormat](紧凑档位),本函数仅限字节。
 */
object StorageFormat {

    fun formatBytes(bytes: Long): String {
        val value = bytes.coerceAtLeast(0L)
        val units = arrayOf("B", "KiB", "MiB", "GiB")
        var amount = value.toDouble()
        var unitIndex = 0
        while (amount >= 1024.0 && unitIndex < units.lastIndex) {
            amount /= 1024.0
            unitIndex++
        }
        return if (unitIndex == 0) {
            "${value} ${units[0]}"
        } else {
            String.format(Locale.US, "%.1f %s", amount, units[unitIndex])
        }
    }
}
