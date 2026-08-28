package com.lioisme.modev.hook.hooks

/**
 * Root/模拟器路径匹配纯函数——可脱离 Android 框架在 JVM 单测中验证。
 *
 * 匹配规则:精确相等 / 前缀+路径分隔符(`/`) / 前缀+扩展名(`.`),
 * 杜绝 `contains()` 子串误杀(如 `/data/user/0` 不应匹配 `/su`)。
 */
object DeviceEnvPathMatcher {

    private val ROOT_PATHS = listOf(
        "/su/bin/su",
        "/system/xbin/su",
        "/system/bin/su",
        "/sbin/su",
        "/data/local/su",
        "/data/local/bin/su",
        "/data/local/xbin/su",
        "/system/app/superuser",
        "/system/app/Superuser.apk",
        "/system/app/SuperSU",
        "/system/app/SuperSU.apk",
        "/system/bin/.ext/.su",
        "/system/etc/init.d/99sud",
        "/system/su.d",
        "/system/usr/we-need-root",
        "/cache/su",
        "/data/su",
        "/dev/su",
        "/system/xbin/busybox",
        "/system/bin/busybox",
        "/sbin/busybox",
        "/data/local/busybox",
        "/data/local/xbin/busybox",
        "/system/app/Superuser",
        "/system/app/SuperUser",
        "/system/app/Magisk",
        "/system/app/MagiskManager",
        "/data/adb/magisk",
        "/data/adb/magisk.db",
        "/proc/1/root",
        "/system/etc/permissive.patches",
    )

    private val EMULATOR_PATHS = listOf(
        "/dev/qemu_pipe",
        "/dev/goldfish_pipe",
        "/dev/socket/qemud",
        "/dev/qemu_trace",
        "/system/lib/libc_malloc_debug_qemu.so",
        "/sys/qemu_trace",
        "/system/bin/qemu-props",
    )

    fun matchesRootPath(lower: String): Boolean =
        ROOT_PATHS.any { pattern ->
            lower == pattern ||
                lower.startsWith("$pattern/") ||
                lower.startsWith("$pattern.")
        }

    fun matchesEmulatorPath(lower: String): String? =
        EMULATOR_PATHS.firstOrNull { pattern ->
            lower == pattern || lower.startsWith("$pattern/")
        }
}
