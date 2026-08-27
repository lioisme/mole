package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.DeviceEnvKind
import com.lioisme.modev.core.model.DeviceEnvOperation
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.MethodHook
import com.lioisme.modev.hook.ProbeContext
import java.io.File

class EmulatorFileHooker(
    context: ProbeContext,
) : DeviceEnvBaseHooker(context) {

    override fun install() {
        val method = runCatching {
            File::class.java.getMethod("exists")
        }.getOrNull() ?: return
        engine.hook(method, object : MethodHook() {
            override fun before(invocation: Invocation) {
                val path = (invocation.thisObject as? File)?.path ?: return
                val lower = path.lowercase()
                val matched = EMULATOR_PATHS.firstOrNull { lower.contains(it) }
                if (matched != null) {
                    invocation.setResult(false)
                    val op = when {
                        matched.startsWith("/dev/") -> DeviceEnvOperation.DEV_FILE_EXISTS
                        matched.startsWith("/system/lib") || matched.startsWith("/system/bin") -> DeviceEnvOperation.LIB_FILE_EXISTS
                        else -> DeviceEnvOperation.SYS_FILE_EXISTS
                    }
                    emit(
                        kind = DeviceEnvKind.EMULATOR_FILE,
                        operation = op,
                        originalValue = "true",
                        maskedValue = "false",
                        targetPath = path,
                        detectionPattern = matched,
                    )
                }
            }
        })
    }

    companion object {
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
