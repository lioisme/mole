package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.DeviceEnvKind
import com.lioisme.modev.core.model.activeSpoofProfile
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.ProbeContext
import com.lioisme.modev.hook.SharedMethodBroadcast

/**
 * 模拟器系统属性伪造 Hooker(V34):拦截 System.getProperty 全量系统属性读取,
 * 对模拟器特征属性返回真实设备值,防止检测框架通过 ro.* 属性识别环境。
 *
 * 覆盖属性:
 * - ro.hardware:qemu → qcom
 * - ro.product.model:generic/sdk → Pixel 7 Pro
 * - ro.product.brand:generic/android → google
 * - ro.product.device:generic/sdk → raven
 * - ro.product.board:goldfish → raven
 * - ro.build.display.id:含 sdk/emulator → UP1A.231105.001
 * - ro.build.user:android-build → android-build(保留)
 * - ro.build.host:含 generic → abfarm-release-rbe-64-00069
 */
class DeviceSysPropHooker(
    context: ProbeContext,
) : DeviceEnvBaseHooker(context) {

    /** 重入守卫(V43 T3,V4.5):回调体内调 System.getProperty 命中本钩子自身。 */
    private val inHook = ThreadLocal.withInitial { false }

    override fun install() {
        val method = runCatching {
            System::class.java.getMethod("getProperty", String::class.java)
        }.getOrNull() ?: return

        // V43 T3/D-4:System.getProperty 三 hooker 共享单一广播注册点
        broadcast.subscribe(
            method,
            object : SharedMethodBroadcast.Sub {
                override fun before(invocation: Invocation) {
                    if (!config().deviceEnvEnabled(DeviceEnvKind.EMULATOR_BUILD)) return
                    if (inHook.get() == true) return
                    val key = invocation.getArg(0) as? String ?: return
                    // V37 T2.3:替换值来自生效伪装画像(内置档位 + 用户覆写)
                    val fakeValue = config().activeSpoofProfile().roProperties[key] ?: return
                    val currentValue = readRealProperty(key)
                    if (currentValue == null || isEmulatorPropValue(currentValue)) {
                        invocation.setResult(fakeValue)
                        emit(
                            kind = DeviceEnvKind.EMULATOR_BUILD,
                            operation = com.lioisme.modev.core.model.DeviceEnvOperation.GET_PROPERTY,
                            originalValue = currentValue,
                            maskedValue = fakeValue,
                            targetProperty = key,
                            interventionAction = ACTION_BLOCKED,
                        )
                    }
                }
            },
        )
    }

    /** 守卫内读取真实 JVM 属性(键空间若与画像重叠也不会无限递归——V4.5)。 */
    private fun readRealProperty(key: String): String? {
        if (inHook.get() == true) return null
        inHook.set(true)
        try {
            return runCatching { System.getProperty(key) }.getOrNull()
        } finally {
            inHook.remove()
        }
    }

    private fun isEmulatorPropValue(value: String): Boolean {
        val lower = value.lowercase()
        return EMULATOR_PROP_KEYWORDS.any { lower.contains(it) }
    }

    companion object {
        private const val ACTION_BLOCKED = "BLOCKED"

        private val EMULATOR_PROP_KEYWORDS = listOf(
            "generic", "sdk", "emulator", "goldfish", "ranchu",
            "vbox", "genymotion", "nox", "bluestacks", "ldnews",
            "android_x86", "android_x86_64",
        )
    }
}
