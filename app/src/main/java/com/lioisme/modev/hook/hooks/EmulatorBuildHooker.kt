package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.DeviceEnvKind
import com.lioisme.modev.core.model.DeviceEnvOperation
import com.lioisme.modev.core.model.ProfileHookPlan
import com.lioisme.modev.core.model.SpoofProfiles
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.MethodHook
import com.lioisme.modev.hook.ProbeContext

/**
 * 模拟器 Build 属性伪造 Hooker:覆盖全 Build 字段伪造,
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

    /** 静态字段改写幂等标记(V37 T0.2:全生命周期仅执行一次;internal 供门控回归测试)。 */
    internal val fieldsMasked = java.util.concurrent.atomic.AtomicBoolean(false)

    override fun install() {
        // V37 T0.2 FeatureGate 红线:Build 静态字段改写受 deviceEnvEnabled(EMULATOR_BUILD)
        // 门控;安装期开关未开时经 ConfigWatcher 在配置变更后一次性补执行,
        // 开关全关则永不篡改(修复「注入即改字段」旁路)。
        applyBuildFieldMasking()
        configWatcher.register { applyBuildFieldMasking() }
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
     * 条件档是否生效 = 自身门控开 **且** 画像强制档不在场(V49 R-D 裁决,判据唯一落在
     * [ProfileHookPlan.adaptiveMaskShouldYield])。
     *
     * 理由:两档写的是同一批 `android.os.Build` 静态字段。画像填了身份值时若条件档仍在跑,
     * 谁先谁后取决于装钩顺序而非用户意图 —— 会出现在真机值上「画像说 Pixel,掩蔽说 emulator」
     * 的互相覆写形态。用户显式填了值,就以值为准。
     */
    private fun maskActive(): Boolean =
        featureOn(DeviceEnvKind.EMULATOR_BUILD) &&
            !ProfileHookPlan.adaptiveMaskShouldYield(config().feature.deviceProfile)

    /** 幂等补写入口:开关开启前零副作用,开启后一次性改写(internal 供门控回归测试)。 */
    internal fun applyBuildFieldMasking() {
        if (fieldsMasked.get()) return
        if (!maskActive()) return
        if (!fieldsMasked.compareAndSet(false, true)) return
        hookBuildStaticFields()
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
                if (!maskActive()) return
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

    /**
     * 伪造值解析(V37 T2.3 画像化):
     * - 档位 = [SpoofProfiles.AUTO]:按模拟器特征族路由内置组(generic 系 → Pixel 组,
     *   vbox/nox 系 → 小米组),保持 V34 既有行为;
     * - 显式档位:统一取所选画像的 buildFields(含用户覆写)。
     */
    internal fun getRealisticValue(field: String, original: String): String {
        val feature = config().feature
        if (feature.spoofProfileId != SpoofProfiles.AUTO) {
            val profile = SpoofProfiles.resolve(feature.spoofProfileId, feature.spoofOverrides)
            return profile.buildFields[field] ?: original
        }
        val lower = original.lowercase()
        return when {
            // 通用模拟器值 → Google Pixel 7 Pro 组
            lower.contains("generic") || lower.contains("sdk") ||
                lower.contains("emulator") || lower.contains("goldfish") ||
                lower.contains("ranchu") -> SpoofProfiles.PIXEL_7_PRO_BUILD[field] ?: original
            // 其他含模拟器特征的值 → 小米 13 组(一致配置)
            lower.contains("vbox") || lower.contains("genymotion") ||
                lower.contains("nox") || lower.contains("bluestacks") ||
                lower.contains("ldnews") -> SpoofProfiles.XIAOMI_13_BUILD[field] ?: original
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
                    if (!maskActive()) return
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
                    if (!maskActive()) return
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
    }
}
