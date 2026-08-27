package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.DeviceEnvKind
import com.lioisme.modev.core.model.DeviceEnvOperation
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.MethodHook
import com.lioisme.modev.hook.ProbeContext

/**
 * 文件存在性检测干预 Hooker(V31):统一拦截 java.io.File 的 exists/length/canRead,
 * 同时覆盖 root 路径与模拟器路径检测,避免同一方法被多个 hooker 重复 hook。
 *
 * 合并自原 RootFileHooker + EmulatorFileHooker 的 File.exists() hook,
 * 减少热路径回调开销(三合一→单一)。
 */
class RootFileHooker(
    context: ProbeContext,
) : DeviceEnvBaseHooker(context) {

    override fun install() {
        hookFileExists()
        hookFileLength()
        hookFileCanRead()
    }

    private fun hookFileExists() {
        val clazz = runCatching { Class.forName("java.io.File") }.getOrNull() ?: return
        val method = runCatching { clazz.getMethod("exists") }.getOrNull() ?: return

        engine.hook(method, object : MethodHook() {
            override fun before(invocation: Invocation) {
                val path = (invocation.thisObject as? java.io.File)?.path ?: return
                val lower = path.lowercase()
                val switch = config()

                // Root 路径检测干预
                if (switch.rootFile) {
                    val rootMatch = ROOT_PATHS.any { lower.contains(it) }
                    if (rootMatch) {
                        val original = runCatching { invocation.result }.getOrNull()?.toString()
                        invocation.setResult(false)
                        emit(
                            kind = DeviceEnvKind.ROOT_FILE,
                            operation = DeviceEnvOperation.FILE_EXISTS,
                            originalValue = original,
                            maskedValue = "false",
                            targetPath = path,
                            detectionPattern = ROOT_PATTERN,
                        )
                        return
                    }
                }

                // 模拟器路径检测干预
                if (switch.emulatorFile) {
                    val emuMatch = EMULATOR_PATHS.firstOrNull { lower.contains(it) }
                    if (emuMatch != null) {
                        invocation.setResult(false)
                        val op = when {
                            emuMatch.startsWith("/dev/") -> DeviceEnvOperation.DEV_FILE_EXISTS
                            emuMatch.startsWith("/system/lib") || emuMatch.startsWith("/system/bin") -> DeviceEnvOperation.LIB_FILE_EXISTS
                            else -> DeviceEnvOperation.SYS_FILE_EXISTS
                        }
                        emit(
                            kind = DeviceEnvKind.EMULATOR_FILE,
                            operation = op,
                            originalValue = "true",
                            maskedValue = "false",
                            targetPath = path,
                            detectionPattern = emuMatch,
                        )
                        return
                    }
                }
            }
        })
    }

    private fun hookFileLength() {
        val clazz = runCatching { Class.forName("java.io.File") }.getOrNull() ?: return
        val method = runCatching { clazz.getMethod("length") }.getOrNull() ?: return

        engine.hook(method, object : MethodHook() {
            override fun before(invocation: Invocation) {
                if (!config().rootFile) return
                val path = (invocation.thisObject as? java.io.File)?.path ?: return
                if (!matchesRootPath(path)) return
                invocation.setResult(0L)
                emit(
                    kind = DeviceEnvKind.ROOT_FILE,
                    operation = DeviceEnvOperation.FILE_LENGTH,
                    originalValue = null,
                    maskedValue = "0",
                    targetPath = path,
                    detectionPattern = ROOT_PATTERN,
                )
            }
        })
    }

    private fun hookFileCanRead() {
        val clazz = runCatching { Class.forName("java.io.File") }.getOrNull() ?: return
        val method = runCatching { clazz.getMethod("canRead") }.getOrNull() ?: return

        engine.hook(method, object : MethodHook() {
            override fun before(invocation: Invocation) {
                if (!config().rootFile) return
                val path = (invocation.thisObject as? java.io.File)?.path ?: return
                if (!matchesRootPath(path)) return
                invocation.setResult(false)
                emit(
                    kind = DeviceEnvKind.ROOT_FILE,
                    operation = DeviceEnvOperation.FILE_READABLE,
                    originalValue = null,
                    maskedValue = "false",
                    targetPath = path,
                    detectionPattern = ROOT_PATTERN,
                )
            }
        })
    }

    private fun matchesRootPath(path: String): Boolean {
        val lower = path.lowercase()
        return ROOT_PATHS.any { lower.contains(it) }
    }

    companion object {

        private const val ROOT_PATTERN = "root-file"

        private val ROOT_PATHS = listOf(
            "/su",
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
    }
}
