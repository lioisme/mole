package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.DeviceEnvKind
import com.lioisme.modev.core.model.DeviceEnvOperation
import com.lioisme.modev.core.model.activeSpoofProfile
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

    /** 假核数(V37 T2.3):取自生效伪装画像。 */
    private fun fakeCores(): Int = config().activeSpoofProfile().cpuCores

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
                val cores = fakeCores()
                if (current <= cores) return
                invocation.setResult(cores)
                emit(
                    kind = DeviceEnvKind.EMULATOR_CPU,
                    operation = DeviceEnvOperation.CPU_AVAILABLE_PROCESSORS,
                    originalValue = current.toString(),
                    maskedValue = cores.toString(),
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
                    val cores = fakeCores()
                    invocation.setResult(buildFakeCpuInfo(cores))
                    emit(
                        kind = DeviceEnvKind.EMULATOR_CPU,
                        operation = DeviceEnvOperation.CPU_PROC_INFO,
                        originalValue = "empty",
                        maskedValue = "$cores cores",
                        interventionAction = ACTION_BLOCKED,
                        targetPath = PROC_CPUINFO,
                    )
                }
            })
        }
    }

    private fun buildFakeCpuInfo(cores: Int): String {
        val sb = StringBuilder()
        for (i in 0 until cores) {
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
        private const val PROC_CPUINFO = "/proc/cpuinfo"
        private const val ACTION_BLOCKED = "BLOCKED"
    }
}
