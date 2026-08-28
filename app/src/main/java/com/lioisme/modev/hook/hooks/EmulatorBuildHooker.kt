package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.DeviceEnvKind
import com.lioisme.modev.core.model.DeviceEnvOperation
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.MethodHook
import com.lioisme.modev.hook.ProbeContext

class EmulatorBuildHooker(
    context: ProbeContext,
) : DeviceEnvBaseHooker(context) {

    override fun install() {
        hookBuildField("FINGERPRINT", DeviceEnvOperation.BUILD_FINGERPRINT)
        hookBuildField("MODEL", DeviceEnvOperation.BUILD_MODEL)
        hookBuildField("PRODUCT", DeviceEnvOperation.BUILD_PRODUCT)
        hookBuildField("MANUFACTURER", DeviceEnvOperation.BUILD_MANUFACTURER)
    }

    /**
     * 尝试 hook Build 字段的 getter 方法。
     *
     * 注意:Build 字段(FINGERPRINT/MODEL/PRODUCT/MANUFACTURER)是 static final String
     * 字段,标准 AOSP 没有 getter 方法;某些 OEM ROM 会添加。若无 getter,
     * 本 hooker 静默跳过——干预失败时保守降级(不修改返回值)。
     */
    private fun hookBuildField(fieldName: String, operation: DeviceEnvOperation) {
        val clazz = runCatching { Class.forName("android.os.Build") }.getOrNull() ?: return
        val getter = runCatching { clazz.getMethod("get$fieldName") }.getOrNull() ?: return
        engine.hook(getter, object : MethodHook() {
            override fun after(invocation: Invocation) {
                if (!config().deviceEnvEnabled(DeviceEnvKind.EMULATOR_BUILD)) return
                val value = invocation.result as? String ?: return
                if (isEmulatorIndicator(value)) {
                    val fake = getRealisticValue(fieldName, value)
                    invocation.setResult(fake)
                    emit(
                        kind = DeviceEnvKind.EMULATOR_BUILD,
                        operation = operation,
                        originalValue = value,
                        maskedValue = fake,
                        targetProperty = fieldName,
                    )
                }
            }
        })
    }

    internal fun isEmulatorIndicator(value: String): Boolean {
        val lower = value.lowercase()
        return EMULATOR_KEYWORDS.any { lower.contains(it) }
    }

    internal fun getRealisticValue(field: String, original: String): String {
        val lower = original.lowercase()
        return when {
            // 通用模拟器值(generic/sdk/emulator/goldfish 等) → Google Pixel 7 Pro 组
            lower.contains("generic") || lower.contains("sdk") ||
                lower.contains("emulator") || lower.contains("goldfish") ||
                lower.contains("ranchu") -> when (field) {
                "FINGERPRINT" -> "google/raven/raven:14/UP1A.231105.001/10754064:userdebug/dev-keys"
                "MODEL" -> "Pixel 7 Pro"
                "PRODUCT" -> "raven"
                "MANUFACTURER" -> "Google"
                else -> original
            }
            // 其他含模拟器特征的值 → 小米 13 组(一致配置)
            lower.contains("vbox") || lower.contains("genymotion") -> when (field) {
                "FINGERPRINT" -> "Xiaomi/13/13:14/UP1A.231105.001/V816.0.23.0.UNACNXM:user/release-keys"
                "MODEL" -> "2211133C"
                "PRODUCT" -> "fuxi"
                "MANUFACTURER" -> "Xiaomi"
                else -> original
            }
            // 未知或已匹配真实设备值 → 不干预(保持原值一致性)
            else -> original
        }
    }

    companion object {
        private val EMULATOR_KEYWORDS = listOf(
            "generic", "sdk", "emulator", "google_sdk",
            "goldfish", "ranchu", "vbox", "genymotion",
            "nox", "bluestacks", "ldnews",  // LDPlayer/Nox/BlueStacks 特征
        )
    }
}
