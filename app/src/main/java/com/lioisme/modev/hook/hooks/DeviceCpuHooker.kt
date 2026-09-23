package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.DeviceEnvKind
import com.lioisme.modev.core.model.DeviceEnvOperation
import com.lioisme.modev.core.model.ProfileHookPlan
import com.lioisme.modev.core.model.ProfileKind
import com.lioisme.modev.core.model.activeSpoofProfile
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.ProbeContext
import com.lioisme.modev.hook.SharedMethodBroadcast

/**
 * 模拟器 CPU 信息掩蔽 Hooker(V34,**条件档**):拦截 `Runtime.availableProcessors`,
 * 仅在真实核数多于目标时压平(V49 WP-D2 起本类**只**负责这一条通路)。
 *
 * 两条登记:
 *  1. **`/proc/cpuinfo` 正文改写自始未生效**(V49-DFC-13):旧实装查的是
 *     `java.io.File.getMethod("readText", Charset)`,而 `readText` 是 `kotlin.io` 的
 *     **扩展函数**(编译成静态 `TextStreamsKt.readText(File, Charset)`),`File` 上根本
 *     没有这个成员 ⇒ `NoSuchMethodException` 被 `getOrNull() ?: return` 咽掉,钩子从未注册。
 *     本批把这条死路整体删除(留着就是「文档说掩蔽 cpuinfo、实装零钩子」的漂移本体);
 *     真正可用的钩点(`TextStreamsKt` 经 app classloader,或 `FileInputStream`)属设计题,
 *     归 V49-DFC-13,须连同时机/开销一起定档,不在画像批次里顺手猜。
 *  2. **与画像强制档共享读口**:[ProfileCpuHooker] 无条件写同一个 `availableProcessors`,
 *     所以画像填了核数时本类让位(V49 R-D),两侧经 [SharedMethodBroadcast] 共用单一注册点。
 */
class DeviceCpuHooker(
    context: ProbeContext,
) : DeviceEnvBaseHooker(context) {

    /** 假核数(V37 T2.3):取自生效伪装画像。 */
    private fun fakeCores(): Int = config().activeSpoofProfile().cpuCores

    /** 条件档是否生效 = 自身门控开 **且** 画像核数不在场(V49 WP-D2)。 */
    private fun maskActive(): Boolean =
        featureOn(DeviceEnvKind.EMULATOR_CPU) &&
            !ProfileHookPlan.adaptiveMaskShouldYield(
                config().feature.deviceProfile,
                ProfileKind.PROFILE_CPU,
            )

    override fun install() {
        val method = runCatching {
            Runtime::class.java.getMethod("availableProcessors")
        }.getOrNull()
        broadcast.subscribe(
            method,
            object : SharedMethodBroadcast.Sub {
                override fun after(invocation: Invocation, result: Any?) {
                    if (!maskActive()) return
                    val current = result as? Int ?: return
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
            },
        )
    }

    companion object {
        private const val ACTION_BLOCKED = "BLOCKED"
    }
}
