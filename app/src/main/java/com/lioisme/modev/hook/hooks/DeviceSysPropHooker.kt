package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.DeviceEnvKind
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.MethodHook
import com.lioisme.modev.hook.ProbeContext

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

    override fun install() {
        val method = runCatching {
            System::class.java.getMethod("getProperty", String::class.java)
        }.getOrNull() ?: return

        engine.hook(method, object : MethodHook() {
            override fun before(invocation: Invocation) {
                if (!config().deviceEnvEnabled(DeviceEnvKind.EMULATOR_BUILD)) return
                val key = invocation.getArg(0) as? String ?: return
                val fakeValue = PROP_REPLACEMENTS[key] ?: return
                val currentValue = runCatching {
                    System.getProperty(key)
                }.getOrNull()
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
        })
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

        private val PROP_REPLACEMENTS = mapOf(
            "ro.hardware" to "qcom",
            "ro.product.model" to "Pixel 7 Pro",
            "ro.product.brand" to "google",
            "ro.product.device" to "raven",
            "ro.product.board" to "raven",
            "ro.build.display.id" to "UP1A.231105.001",
            "ro.build.host" to "abfarm-release-rbe-64-00069",
            "ro.build.user" to "android-build",
            "ro.product.name" to "raven",
            "ro.product.manufacturer" to "Google",
            "ro.board.platform" to "taro",
            "ro.hardware.chipname" to "qcom",
            "ro.soc.model" to "SM8550",
            "ro.soc.manufacturer" to "Qualcomm",
        )
    }
}
