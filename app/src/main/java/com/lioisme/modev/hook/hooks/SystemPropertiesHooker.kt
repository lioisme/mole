package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.DeviceEnvKind
import com.lioisme.modev.core.model.DeviceEnvOperation
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.MethodHook
import com.lioisme.modev.hook.ProbeContext

/**
 * android.os.SystemProperties.get() 拦截 Hooker(V35 momo 增强):
 * 拦截 framework 层 SystemProperties.get(String) 和 get(String, String),
 * 对 root/emulator 指示属性返回安全值。
 *
 * 与 RootPropertyHooker 的分工:
 * - RootPropertyHooker: hook java.lang.System.getProperty (Java 标准 API)
 * - 本 hooker: hook android.os.SystemProperties.get (Android framework API,
 *   momo/牛头人 等检测框架的实际调用路径)
 *
 * 合并 RootPropertyHooker + DeviceSysPropHooker 的属性映射,
 * 消除重复维护成本。
 */
class SystemPropertiesHooker(
    context: ProbeContext,
) : DeviceEnvBaseHooker(context) {

    private val inHook = ThreadLocal.withInitial { false }

    override fun install() {
        hookSystemPropertiesGet()
        hookSystemPropertiesGetWithDefault()
    }

    private fun hookSystemPropertiesGet() {
        val clazz = runCatching { Class.forName("android.os.SystemProperties") }.getOrNull() ?: return
        val method = runCatching {
            clazz.getMethod("get", String::class.java)
        }.getOrNull() ?: return

        engine.hook(method, object : MethodHook() {
            override fun after(invocation: Invocation) {
                if (!config().systemProperties) return
                if (inHook.get() == true) return
                inHook.set(true)
                try {
                    val key = invocation.getArg(0) as? String ?: return
                    val replacement = ALL_REPLACEMENTS[key] ?: return
                    val original = invocation.result as? String
                    if (original == null || original == replacement) return
                    if (!isIndicativeValue(key, original)) return
                    invocation.setResult(replacement)
                    emit(
                        kind = DeviceEnvKind.SYSTEM_PROPERTIES,
                        operation = DeviceEnvOperation.SYS_PROP_GET,
                        originalValue = original,
                        maskedValue = replacement,
                        targetClass = "android.os.SystemProperties",
                        targetProperty = key,
                        interventionAction = ACTION_BLOCKED,
                    )
                } finally {
                    inHook.remove()
                }
            }
        })
    }

    private fun hookSystemPropertiesGetWithDefault() {
        val clazz = runCatching { Class.forName("android.os.SystemProperties") }.getOrNull() ?: return
        val method = runCatching {
            clazz.getMethod("get", String::class.java, String::class.java)
        }.getOrNull() ?: return

        engine.hook(method, object : MethodHook() {
            override fun after(invocation: Invocation) {
                if (!config().systemProperties) return
                if (inHook.get() == true) return
                inHook.set(true)
                try {
                    val key = invocation.getArg(0) as? String ?: return
                    val replacement = ALL_REPLACEMENTS[key] ?: return
                    val original = invocation.result as? String
                    if (original == null || original == replacement) return
                    if (!isIndicativeValue(key, original)) return
                    invocation.setResult(replacement)
                    emit(
                        kind = DeviceEnvKind.SYSTEM_PROPERTIES,
                        operation = DeviceEnvOperation.SYS_PROP_GET,
                        originalValue = original,
                        maskedValue = replacement,
                        targetClass = "android.os.SystemProperties",
                        targetProperty = key,
                        interventionAction = ACTION_BLOCKED,
                    )
                } finally {
                    inHook.remove()
                }
            }
        })
    }

    /**
     * 判断属性原始值是否确实是 root/emulator 指示值。
     * 仅在原始值确实为异常值时才干预,避免修改正常值导致 App 初始化路径断裂。
     */
    private fun isIndicativeValue(key: String, value: String): Boolean {
        val lower = value.lowercase()
        return when (key) {
            // Root 属性
            "ro.debuggable" -> value == "1"
            "ro.secure" -> value == "0"
            "ro.build.selinux" -> value == "0"
            "ro.build.type" -> value == "eng" || value == "userdebug"
            "ro.build.tags" -> !value.contains("release-keys")
            // 模拟器属性
            "ro.hardware" -> EMULATOR_KEYWORDS.any { lower.contains(it) }
            "ro.product.model" -> EMULATOR_KEYWORDS.any { lower.contains(it) }
            "ro.product.brand" -> EMULATOR_KEYWORDS.any { lower.contains(it) }
            "ro.product.device" -> EMULATOR_KEYWORDS.any { lower.contains(it) }
            "ro.product.board" -> EMULATOR_KEYWORDS.any { lower.contains(it) }
            "ro.build.display.id" -> EMULATOR_KEYWORDS.any { lower.contains(it) }
            "ro.build.host" -> EMULATOR_KEYWORDS.any { lower.contains(it) }
            "ro.product.name" -> EMULATOR_KEYWORDS.any { lower.contains(it) }
            "ro.product.manufacturer" -> EMULATOR_KEYWORDS.any { lower.contains(it) }
            "ro.board.platform" -> lower.contains("goldfish") || lower.contains("ranchu")
            "ro.hardware.chipname" -> EMULATOR_KEYWORDS.any { lower.contains(it) }
            "ro.soc.model" -> lower.contains("goldfish") || lower.contains("ranchu")
            "ro.soc.manufacturer" -> lower.contains("google") && lower.contains("sdk")
            else -> false
        }
    }

    companion object {
        private const val ACTION_BLOCKED = "BLOCKED"

        private val EMULATOR_KEYWORDS = listOf(
            "generic", "sdk", "emulator", "goldfish", "ranchu",
            "vbox", "genymotion", "nox", "bluestacks", "ldnews",
            "android_x86", "android_x86_64",
        )

        /** 合并 root 属性 + 模拟器属性的完整映射表。 */
        private val ALL_REPLACEMENTS = mapOf(
            // Root 属性
            "ro.debuggable" to "0",
            "ro.secure" to "1",
            "ro.build.selinux" to "1",
            "ro.build.type" to "user",
            "ro.build.tags" to "release-keys",
            // 模拟器属性(Pixel 7 Pro 组)
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
