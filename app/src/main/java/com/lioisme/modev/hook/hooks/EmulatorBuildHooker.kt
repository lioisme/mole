package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.DeviceEnvKind
import com.lioisme.modev.core.model.DeviceEnvOperation
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.MethodHook
import com.lioisme.modev.hook.ProbeContext

/**
 * 模拟器 Build 属性伪造 Hooker(V34 增强):覆盖全 Build 字段伪造,
 * 通过反射修改 android.os.Build 静态字段 + OEM getter 双路径。
 *
 * 标准 AOSP Build 字段为 static final String, Xposed 无法直接 hook 字段访问,
 * 采用反射在 hook 安装阶段修改字段值(attachBaseContext 后首次读取前生效)。
 * OEM ROM 添加的 getter 方法由 hook 额外覆盖,确保运行时读取一致性。
 *
 * 伪造值组:
 * - Pixel 7 Pro 组:通用模拟器(generic/sdk/emulator/goldfish/ranchu)
 * - 小米 13 组:VBox/Genymotion/Nox/BlueStacks/LDPlayer
 */
class EmulatorBuildHooker(
    context: ProbeContext,
) : DeviceEnvBaseHooker(context) {

    override fun install() {
        hookBuildStaticFields()
        hookBuildGetters("FINGERPRINT", DeviceEnvOperation.BUILD_FINGERPRINT)
        hookBuildGetters("MODEL", DeviceEnvOperation.BUILD_MODEL)
        hookBuildGetters("PRODUCT", DeviceEnvOperation.BUILD_PRODUCT)
        hookBuildGetters("MANUFACTURER", DeviceEnvOperation.BUILD_MANUFACTURER)
        hookBuildGetters("DEVICE", DeviceEnvOperation.BUILD_DEVICE)
        hookBuildGetters("BOARD", DeviceEnvOperation.BUILD_BOARD)
        hookBuildGetters("HARDWARE", DeviceEnvOperation.BUILD_HARDWARE)
        hookBuildGetters("BRAND", DeviceEnvOperation.BUILD_BRAND)
        hookBuildGetters("HOST", DeviceEnvOperation.BUILD_HOST)
        hookBuildGetters("USER", DeviceEnvOperation.BUILD_USER)
        hookBuildGetters("DISPLAY", DeviceEnvOperation.BUILD_DISPLAY)
        hookBuildGetters("SERIAL", DeviceEnvOperation.BUILD_SERIAL)
        hookBuildGetters("TYPE", DeviceEnvOperation.BUILD_TYPE)
        hookBuildFieldAccess()
    }

    /**
     * 通过反射修改 Build 静态字段(安装阶段一次性修改)。
     * 仅在字段值含模拟器特征时修改,保持真实设备值不变。
     */
    private fun hookBuildStaticFields() {
        val buildClass = runCatching { Class.forName("android.os.Build") }.getOrNull() ?: return
        val fieldMap = mapOf(
            "FINGERPRINT" to DeviceEnvOperation.BUILD_FINGERPRINT,
            "MODEL" to DeviceEnvOperation.BUILD_MODEL,
            "PRODUCT" to DeviceEnvOperation.BUILD_PRODUCT,
            "MANUFACTURER" to DeviceEnvOperation.BUILD_MANUFACTURER,
            "DEVICE" to DeviceEnvOperation.BUILD_DEVICE,
            "BOARD" to DeviceEnvOperation.BUILD_BOARD,
            "HARDWARE" to DeviceEnvOperation.BUILD_HARDWARE,
            "BRAND" to DeviceEnvOperation.BUILD_BRAND,
            "HOST" to DeviceEnvOperation.BUILD_HOST,
            "USER" to DeviceEnvOperation.BUILD_USER,
            "DISPLAY" to DeviceEnvOperation.BUILD_DISPLAY,
            "SERIAL" to DeviceEnvOperation.BUILD_SERIAL,
            "TYPE" to DeviceEnvOperation.BUILD_TYPE,
        )
        for ((fieldName, operation) in fieldMap) {
            val field = runCatching { buildClass.getDeclaredField(fieldName) }.getOrNull() ?: continue
            field.isAccessible = true
            val currentValue = runCatching { field.get(null) as? String }.getOrNull() ?: continue
            if (isEmulatorIndicator(currentValue)) {
                val fake = getRealisticValue(fieldName, currentValue)
                runCatching { field.set(null, fake) }
                emit(
                    kind = DeviceEnvKind.EMULATOR_BUILD,
                    operation = operation,
                    originalValue = currentValue,
                    maskedValue = fake,
                    targetProperty = "Build.$fieldName",
                    interventionAction = ACTION_BLOCKED,
                )
            }
        }
    }

    /**
     * 尝试 hook Build 字段的 getter 方法(OEM ROM 扩展)。
     */
    private fun hookBuildGetters(fieldName: String, operation: DeviceEnvOperation) {
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
                        targetProperty = "Build.get$fieldName",
                        interventionAction = ACTION_BLOCKED,
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
            // 通用模拟器值 → Google Pixel 7 Pro 组
            lower.contains("generic") || lower.contains("sdk") ||
                lower.contains("emulator") || lower.contains("goldfish") ||
                lower.contains("ranchu") -> PIXEL_7_PRO_MAP[field] ?: original
            // 其他含模拟器特征的值 → 小米 13 组(一致配置)
            lower.contains("vbox") || lower.contains("genymotion") ||
                lower.contains("nox") || lower.contains("bluestacks") ||
                lower.contains("ldnews") -> XIAOMI_13_MAP[field] ?: original
            // 未知或已匹配真实设备值 → 不干预(保持原值一致性)
            else -> original
        }
    }

    /**
     * V35 momo 增强:hook Class.getField / Class.getDeclaredField,
     * 当检测框架通过反射访问 Build 字段时拦截(如 `Class.forName("android.os.Build").getField("TAGS")`)。
     * 覆盖 momo "设备正在运行非原厂系统" 检测路径。
     */
    private fun hookBuildFieldAccess() {
        val classClass = runCatching { Class.forName("java.lang.Class") }.getOrNull() ?: return

        // hook getField(String)
        val getField = runCatching {
            classClass.getMethod("getField", String::class.java)
        }.getOrNull()
        if (getField != null) {
            engine.hook(getField, object : MethodHook() {
                override fun after(invocation: Invocation) {
                    if (!config().emulatorBuild) return
                    val name = invocation.getArg(0) as? String ?: return
                    if (name !in INTERCEPTED_FIELD_NAMES) return
                    val declaringClass = invocation.thisObject as? Class<*> ?: return
                    if (declaringClass.name != "android.os.Build") return
                    // 字段已由 hookBuildStaticFields 修改,此处仅记录检测行为
                    emit(
                        kind = DeviceEnvKind.EMULATOR_BUILD,
                        operation = DeviceEnvOperation.BUILD_TAGS,
                        originalValue = name,
                        targetProperty = "Class.getField($name)",
                        detectionPattern = "reflect-field-access",
                    )
                }
            })
        }

        // hook getDeclaredField(String)
        val getDeclaredField = runCatching {
            classClass.getMethod("getDeclaredField", String::class.java)
        }.getOrNull()
        if (getDeclaredField != null) {
            engine.hook(getDeclaredField, object : MethodHook() {
                override fun after(invocation: Invocation) {
                    if (!config().emulatorBuild) return
                    val name = invocation.getArg(0) as? String ?: return
                    if (name !in INTERCEPTED_FIELD_NAMES) return
                    val declaringClass = invocation.thisObject as? Class<*> ?: return
                    if (declaringClass.name != "android.os.Build") return
                    emit(
                        kind = DeviceEnvKind.EMULATOR_BUILD,
                        operation = DeviceEnvOperation.BUILD_TAGS,
                        originalValue = name,
                        targetProperty = "Class.getDeclaredField($name)",
                        detectionPattern = "reflect-field-access",
                    )
                }
            })
        }
    }

    companion object {
        private const val ACTION_BLOCKED = "BLOCKED"

        private val EMULATOR_KEYWORDS = listOf(
            "generic", "sdk", "emulator", "google_sdk",
            "goldfish", "ranchu", "vbox", "genymotion",
            "nox", "bluestacks", "ldnews",
        )

        /** 通过 Class.getField/getDeclaredField 反射访问 Build 字段时需要拦截的字段名。 */
        private val INTERCEPTED_FIELD_NAMES = setOf(
            "TAGS", "TYPE", "FINGERPRINT", "MODEL", "PRODUCT", "MANUFACTURER",
            "DEVICE", "BOARD", "HARDWARE", "BRAND", "HOST", "USER", "DISPLAY", "SERIAL",
        )

        private val PIXEL_7_PRO_MAP = mapOf(
            "FINGERPRINT" to "google/raven/raven:14/UP1A.231105.001/10754064:userdebug/dev-keys",
            "MODEL" to "Pixel 7 Pro",
            "PRODUCT" to "raven",
            "MANUFACTURER" to "Google",
            "DEVICE" to "raven",
            "BOARD" to "raven",
            "HARDWARE" to "raven",
            "BRAND" to "google",
            "HOST" to "abfarm-release-rbe-64-00069",
            "USER" to "android-build",
            "DISPLAY" to "UP1A.231105.001",
            "SERIAL" to "unknown",
            "TYPE" to "userdebug",
        )

        private val XIAOMI_13_MAP = mapOf(
            "FINGERPRINT" to "Xiaomi/13/13:14/UP1A.231105.001/V816.0.23.0.UNACNXM:user/release-keys",
            "MODEL" to "2211133C",
            "PRODUCT" to "fuxi",
            "MANUFACTURER" to "Xiaomi",
            "DEVICE" to "fuxi",
            "BOARD" to "taro",
            "HARDWARE" to "qcom",
            "BRAND" to "Xiaomi",
            "HOST" to "xiaomi.eu",
            "USER" to "builder",
            "DISPLAY" to "V816.0.23.0.UNACNXM",
            "SERIAL" to "unknown",
            "TYPE" to "user",
        )
    }
}
