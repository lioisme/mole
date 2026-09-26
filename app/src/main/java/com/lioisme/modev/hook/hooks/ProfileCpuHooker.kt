package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.DeviceEnvKind
import com.lioisme.modev.core.model.DeviceEnvOperation
import com.lioisme.modev.core.model.DeviceProfile
import com.lioisme.modev.core.model.ProfileField
import com.lioisme.modev.core.model.ProfileFieldCatalog
import com.lioisme.modev.core.model.isSetIn
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.ProbeContext
import com.lioisme.modev.hook.SharedMethodBroadcast

/**
 * 画像强制档 · CPU 核数一族。
 *
 * 与 [DeviceCpuHooker] 的分工:那边是条件档(真实核数**多于**目标时才改),本类是强制档
 * (画像填了就等于它 —— 真机 8 核伪装成 4 核同样是意图)。两者写同一读口,所以条件档在画像
 * 填写该项时让位,且两侧共用 [SharedMethodBroadcast] 的单一注册点。
 *
 * **本族不含 `/proc/cpuinfo` 正文改写**:那条通路此前是死代码(见 [DeviceCpuHooker] 的登记),
 * 而正确的钩点(`kotlin.io` 扩展 = 静态 `TextStreamsKt`,或 `FileInputStream`)要先定档,
 * 归 V49-DFC-13。与其在新钩子里抄一份同样永不安装的查找,不如把缺口留在明面上。
 *
 * 留痕去重:`availableProcessors` 会被反复调用,每调一次记一条等于刷屏。
 */
class ProfileCpuHooker(
    context: ProbeContext,
) : DeviceEnvBaseHooker(context) {

    private var auditedCores: Int? = null

    override fun install() {
        val method = runCatching {
            Runtime::class.java.getMethod("availableProcessors")
        }.getOrNull()
        broadcast.subscribe(
            method,
            object : SharedMethodBroadcast.Sub {
                override fun after(invocation: Invocation, result: Any?) {
                    if (!featureOn(DeviceEnvKind.PROFILE_CPU)) return
                    val cores = targetCores(config().feature.deviceProfile) ?: return
                    val current = result as? Int ?: return
                    if (current != cores) invocation.setResult(cores)
                    if (auditedCores != cores) {
                        auditedCores = cores
                        emit(
                            kind = DeviceEnvKind.PROFILE_CPU,
                            operation = DeviceEnvOperation.CPU_WRITE,
                            originalValue = current.toString(),
                            maskedValue = cores.toString(),
                            targetClass = "Runtime",
                            targetProperty = TARGET_PROPERTY,
                            interventionAction = ACTION_BLOCKED,
                        )
                    }
                }
            },
        )
    }

    companion object {
        const val TARGET_PROPERTY = "Runtime.availableProcessors"
        private const val ACTION_BLOCKED = "BLOCKED"

        /**
         * 目标核数(纯函数):未填 ⇒ null(一次 setResult 都不发生)。
         * 界恒取 `ProfileFieldCatalog.rangeOf`,与画像校验同一张表。
         */
        internal fun targetCores(profile: DeviceProfile): Int? {
            if (!ProfileField.CPU_CORES.isSetIn(profile)) return null
            val range = requireNotNull(ProfileFieldCatalog.rangeOf(ProfileField.CPU_CORES))
            return profile.cpuCores.takeIf { it in range }
        }
    }
}
