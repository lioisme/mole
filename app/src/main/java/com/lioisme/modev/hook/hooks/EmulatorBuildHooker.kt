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

    private fun hookBuildField(fieldName: String, operation: DeviceEnvOperation) {
        val clazz = runCatching { Class.forName("android.os.Build") }.getOrNull() ?: return
        val field = runCatching { clazz.getField(fieldName) }.getOrNull() ?: return
        val getter = runCatching { clazz.getMethod("get$field") }.getOrNull()
        if (getter != null) {
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
        } else {
            val staticGetter = runCatching {
                clazz.getMethod(fieldName)
            }.getOrNull()
            if (staticGetter != null) {
                engine.hook(staticGetter, object : MethodHook() {
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
        }
    }

    private fun isEmulatorIndicator(value: String): Boolean {
        val lower = value.lowercase()
        return EMULATOR_KEYWORDS.any { lower.contains(it) }
    }

    private fun getRealisticValue(field: String, original: String): String {
        val lower = original.lowercase()
        return when {
            field == "FINGERPRINT" -> when {
                lower.contains("generic") -> "samsung/rheroqltetv/rheroqltetv:13/TP1A.220624.014/9586864:userdebug/dev-keys"
                lower.contains("sdk") -> "google/raven/raven:13/TP1A.220624.014/8876468:userdebug/dev-keys"
                else -> "xiaomi/13/13:13/TP1A.220624.014/V816.0.23.0.UNACNXM:user/release-keys"
            }
            field == "MODEL" -> when {
                lower.contains("generic") || lower.contains("sdk") -> "Pixel 7 Pro"
                else -> "M2012K11AC"
            }
            field == "PRODUCT" -> when {
                lower.contains("generic") || lower.contains("sdk") -> "raven"
                else -> "13"
            }
            field == "MANUFACTURER" -> when {
                lower.contains("generic") || lower.contains("sdk") || lower.contains("google") -> "Xiaomi"
                else -> "Xiaomi"
            }
            else -> original
        }
    }

    companion object {
        private val EMULATOR_KEYWORDS = listOf(
            "generic", "sdk", "emulator", "google_sdk",
            "goldfish", "ranchu", "vbox",
        )
    }
}
