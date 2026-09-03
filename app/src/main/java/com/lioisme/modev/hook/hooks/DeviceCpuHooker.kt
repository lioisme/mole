package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.DeviceEnvKind
import com.lioisme.modev.core.model.DeviceEnvOperation
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.MethodHook
import com.lioisme.modev.hook.ProbeContext

/**
 * 模拟器 CPU 信息伪造 Hooker(V34):拦截 Runtime.availableProcessors 和
 * /proc/cpuinfo 读取,伪造 CPU 核心数和架构信息,防止模拟器检测框架
 * 通过 CPU 信息识别环境(模拟器通常为 1 核)。
 *
 * 覆盖 API:
 * - Runtime.availableProcessors():返回 8(模拟器通常 1)
 * - /proc/cpuinfo 读取:过滤并伪造核心信息
 */
class DeviceCpuHooker(
    context: ProbeContext,
) : DeviceEnvBaseHooker(context) {

    override fun install() {
        hookAvailableProcessors()
        hookProcCpuInfo()
    }

    private fun hookAvailableProcessors() {
        val method = runCatching {
            Runtime::class.java.getMethod("availableProcessors")
        }.getOrNull() ?: return

        engine.hook(method, object : MethodHook() {
            override fun after(invocation: Invocation) {
                if (!config().deviceEnvEnabled(DeviceEnvKind.EMULATOR_CPU)) return
                val current = invocation.result as? Int ?: return
                if (current <= FAKE_CORE_COUNT) return
                invocation.setResult(FAKE_CORE_COUNT)
                emit(
                    kind = DeviceEnvKind.EMULATOR_CPU,
                    operation = DeviceEnvOperation.CPU_AVAILABLE_PROCESSORS,
                    originalValue = current.toString(),
                    maskedValue = FAKE_CORE_COUNT.toString(),
                    interventionAction = ACTION_BLOCKED,
                    targetClass = "Runtime",
                    targetProperty = "availableProcessors",
                )
            }
        })
    }

    private fun hookProcCpuInfo() {
        val fileClass = runCatching { java.io.File::class.java }.getOrNull() ?: return
        val readText = runCatching {
            fileClass.getMethod("readText", java.nio.charset.Charset::class.java)
        }.getOrNull()
        if (readText != null) {
            engine.hook(readText, object : MethodHook() {
                override fun after(invocation: Invocation) {
                    if (!config().deviceEnvEnabled(DeviceEnvKind.EMULATOR_CPU)) return
                    val path = runCatching {
                        val thisObj = invocation.thisObject ?: return@runCatching null
                        thisObj.javaClass.getMethod("getPath").invoke(thisObj) as? String
                    }.getOrNull()
                    if (path != PROC_CPUINFO) return
                    val original = invocation.result as? String ?: return
                    if (original.contains("processor")) return
                    invocation.setResult(buildFakeCpuInfo())
                    emit(
                        kind = DeviceEnvKind.EMULATOR_CPU,
                        operation = DeviceEnvOperation.CPU_PROC_INFO,
                        originalValue = "empty",
                        maskedValue = "8 cores",
                        interventionAction = ACTION_BLOCKED,
                        targetPath = PROC_CPUINFO,
                    )
                }
            })
        }
    }

    private fun buildFakeCpuInfo(): String {
        val sb = StringBuilder()
        for (i in 0 until FAKE_CORE_COUNT) {
            sb.appendLine("processor\t: $i")
            sb.appendLine("BogoMIPS\t: 38.40")
            sb.appendLine("Features\t: fp asimd evtstrm aes pmull sha1 sha2 crc32")
            sb.appendLine("CPU implementer\t: 0x51")
            sb.appendLine("CPU architecture: 8")
            sb.appendLine("CPU variant\t: 0x6")
            sb.appendLine("CPU part\t: 0x805")
            sb.appendLine("CPU revision\t: 13")
            sb.appendLine()
        }
        return sb.toString()
    }

    companion object {
        private const val FAKE_CORE_COUNT = 8
        private const val PROC_CPUINFO = "/proc/cpuinfo"
        private const val ACTION_BLOCKED = "BLOCKED"
    }
}
