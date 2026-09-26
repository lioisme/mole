package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.DeviceEnvKind
import com.lioisme.modev.core.model.DeviceEnvOperation
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.MethodHook
import com.lioisme.modev.hook.ProbeContext
import com.lioisme.modev.hook.SharedMethodBroadcast

/**
 * Settings Global/Secure 拦截 Hooker:
 * 拦截 Settings.Global.getInt / Settings.Secure.getInt / ContentResolver.query,
 * 隐藏开发者选项、USB 调试、OEM 解锁等检测信号。
 *
 * momo v4.4.1 检测项覆盖:
 * - "处于调试环境" → Settings.Global.DEVELOPMENT_SETTINGS_ENABLED
 * - "已开启调试模式" → Settings.Global.ADB_ENABLED
 * - OEM unlock → Settings.Secure.OEM_UNLOCK_ENABLED
 * - 未知来源安装 → Settings.Secure.INSTALL_NON_MARKET_APPS
 * - 模拟位置 → Settings.Secure.MOCK_LOCATION
 *
 * 设计决策:hook Settings 的 Java API (getInt/getString) 比 hook ContentResolver.query
 * 更精准——query 返回 Cursor 需要逐行篡改,热路径开销大;getInt/getString 是单值读取,
 * 直接替换返回值即可。
 */
class SettingsHooker(
    context: ProbeContext,
) : DeviceEnvBaseHooker(context) {

    private val inHook = ThreadLocal.withInitial { false }

    override fun install() {
        hookSettingsGlobalGetInt()
        hookSettingsSecureGetInt()
        hookSettingsGlobalGetString()
        hookSettingsSecureGetString()
    }

    private fun hookSettingsGlobalGetInt() {
        val clazz = runCatching { Class.forName("android.provider.Settings\$Global") }.getOrNull() ?: return
        val method = runCatching {
            clazz.getMethod("getInt", android.content.ContentResolver::class.java, String::class.java, Int::class.javaPrimitiveType)
        }.getOrNull() ?: return

        engine.hook(method, object : MethodHook() {
            override fun after(invocation: Invocation) {
                if (!featureOn(DeviceEnvKind.ROOT_PROPERTY)) return
                if (inHook.get() == true) return
                inHook.set(true)
                try {
                    val key = invocation.getArg(1) as? String ?: return
                    val replacement = GLOBAL_REPLACEMENTS[key] ?: return
                    val original = invocation.result as? Int ?: return
                    if (original == replacement) return
                    invocation.setResult(replacement)
                    emit(
                        kind = DeviceEnvKind.ROOT_PROPERTY,
                        operation = DeviceEnvOperation.SETTINGS_GLOBAL_GET,
                        originalValue = original.toString(),
                        maskedValue = replacement.toString(),
                        targetClass = "android.provider.Settings\$Global",
                        targetProperty = key,
                        interventionAction = ACTION_BLOCKED,
                    )
                } finally {
                    inHook.remove()
                }
            }
        })
    }

    private fun hookSettingsSecureGetInt() {
        val clazz = runCatching { Class.forName("android.provider.Settings\$Secure") }.getOrNull() ?: return
        val method = runCatching {
            clazz.getMethod("getInt", android.content.ContentResolver::class.java, String::class.java, Int::class.javaPrimitiveType)
        }.getOrNull() ?: return

        engine.hook(method, object : MethodHook() {
            override fun after(invocation: Invocation) {
                if (!featureOn(DeviceEnvKind.ROOT_PROPERTY)) return
                if (inHook.get() == true) return
                inHook.set(true)
                try {
                    val key = invocation.getArg(1) as? String ?: return
                    val replacement = SECURE_REPLACEMENTS[key] ?: return
                    val original = invocation.result as? Int ?: return
                    if (original == replacement) return
                    invocation.setResult(replacement)
                    emit(
                        kind = DeviceEnvKind.ROOT_PROPERTY,
                        operation = DeviceEnvOperation.SETTINGS_SECURE_GET,
                        originalValue = original.toString(),
                        maskedValue = replacement.toString(),
                        targetClass = "android.provider.Settings\$Secure",
                        targetProperty = key,
                        interventionAction = ACTION_BLOCKED,
                    )
                } finally {
                    inHook.remove()
                }
            }
        })
    }

    private fun hookSettingsGlobalGetString() {
        val clazz = runCatching { Class.forName("android.provider.Settings\$Global") }.getOrNull() ?: return
        val method = runCatching {
            clazz.getMethod("getString", android.content.ContentResolver::class.java, String::class.java)
        }.getOrNull() ?: return

        engine.hook(method, object : MethodHook() {
            override fun after(invocation: Invocation) {
                if (!featureOn(DeviceEnvKind.ROOT_PROPERTY)) return
                if (inHook.get() == true) return
                inHook.set(true)
                try {
                    val key = invocation.getArg(1) as? String ?: return
                    val strReplacement = GLOBAL_STRING_REPLACEMENTS[key] ?: return
                    val original = invocation.result as? String
                    if (original == strReplacement) return
                    invocation.setResult(strReplacement)
                    emit(
                        kind = DeviceEnvKind.ROOT_PROPERTY,
                        operation = DeviceEnvOperation.SETTINGS_GLOBAL_GET,
                        originalValue = original,
                        maskedValue = strReplacement,
                        targetClass = "android.provider.Settings\$Global",
                        targetProperty = key,
                        interventionAction = ACTION_BLOCKED,
                    )
                } finally {
                    inHook.remove()
                }
            }
        })
    }

    private fun hookSettingsSecureGetString() {
        val clazz = runCatching { Class.forName("android.provider.Settings\$Secure") }.getOrNull() ?: return
        val method = runCatching {
            clazz.getMethod("getString", android.content.ContentResolver::class.java, String::class.java)
        }.getOrNull() ?: return

        // V43 T3/D-4:Settings$Secure.getString 与 EmulatorNetwork(android_id)共享
        // 单一广播注册点;after 子链按订阅序执行,结果改写对后续订阅者可见(V2.9)
        broadcast.subscribe(
            method,
            object : SharedMethodBroadcast.Sub {
                override fun after(invocation: Invocation, result: Any?) {
                    if (!featureOn(DeviceEnvKind.ROOT_PROPERTY)) return
                    if (inHook.get() == true) return
                    inHook.set(true)
                    try {
                        val key = invocation.getArg(1) as? String ?: return
                        val strReplacement = SECURE_STRING_REPLACEMENTS[key] ?: return
                        val original = result as? String
                        if (original == strReplacement) return
                        invocation.setResult(strReplacement)
                        emit(
                            kind = DeviceEnvKind.ROOT_PROPERTY,
                            operation = DeviceEnvOperation.SETTINGS_SECURE_GET,
                            originalValue = original,
                            maskedValue = strReplacement,
                            targetClass = "android.provider.Settings\$Secure",
                            targetProperty = key,
                            interventionAction = ACTION_BLOCKED,
                        )
                    } finally {
                        inHook.remove()
                    }
                }
            },
        )
    }

    companion object {
        private const val ACTION_BLOCKED = "BLOCKED"

        /** Settings.Global int 值伪造:检测框架读取时返回「关闭」状态。 */
        private val GLOBAL_REPLACEMENTS = mapOf(
            "adb_enabled" to 0,
            "development_settings_enabled" to 0,
            "adb_wifi_enabled" to 0,
            "stay_on_while_plugged_in" to 0,
            "window_animation_scale" to 1,
            "transition_animation_scale" to 1,
            "animator_duration_scale" to 1,
        )

        /** Settings.Secure int 值伪造。 */
        private val SECURE_REPLACEMENTS = mapOf(
            "oem_unlock_enabled" to 0,
            "install_non_market_apps" to 0,
            "mock_location" to 0,
            "location_mode" to 3,
        )

        /** Settings.Global string 值伪造。 */
        private val GLOBAL_STRING_REPLACEMENTS = mapOf(
            "adb_enabled" to "0",
            "development_settings_enabled" to "0",
        )

        /** Settings.Secure string 值伪造。 */
        private val SECURE_STRING_REPLACEMENTS = mapOf(
            "oem_unlock_enabled" to "0",
            "install_non_market_apps" to "0",
        )
    }
}
