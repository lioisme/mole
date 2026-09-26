package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.DeviceEnvKind
import com.lioisme.modev.core.model.DeviceEnvOperation
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.ProbeContext
import com.lioisme.modev.hook.SharedMethodBroadcast

/**
 * Root 系统属性检测干预 Hooker:拦截 System.getProperty 读取 root 指示属性,
 * 伪造返回值,阻止应用通过系统属性检测 root。
 *
 * Build.TAGS 字段访问无法通过 MethodHook 拦截(Xposed 仅支持方法/构造器 hook),
 * 但多数 root 检测框架通过 System.getProperty("ro.build.tags") 间接读取,
 * 已被本 hooker 覆盖。
 */
class RootPropertyHooker(
    context: ProbeContext,
) : DeviceEnvBaseHooker(context) {

    override fun install() {
        hookSystemGetProperty()
    }

    private fun hookSystemGetProperty() {
        val clazz = runCatching { Class.forName("java.lang.System") }.getOrNull() ?: return
        val method = runCatching {
            clazz.getMethod("getProperty", String::class.java)
        }.getOrNull() ?: return

        // V43 T3/D-4:System.getProperty 与 ProxyHide/DeviceSysProp 三 hooker 共享,
        // 经单一广播注册点分派(Vector 首注册静默遮蔽 / LSPosed 回调倍增双防)
        broadcast.subscribe(
            method,
            object : SharedMethodBroadcast.Sub {
                override fun after(invocation: Invocation, result: Any?) {
                    if (!featureOn(DeviceEnvKind.ROOT_PROPERTY)) return
                    val key = invocation.getArg(0) as? String ?: return
                    val replacement = PROPERTY_REPLACEMENTS[key] ?: return
                    val original = result as? String
                    if (original == null || original == replacement) return
                    // 仅在原始值确实是 root 指示值时才干预,
                    // 避免修改非 root 环境的合法值导致 App 初始化路径断裂。
                    if (!isRootIndicativeValue(key, original)) return
                    invocation.setResult(replacement)
                    emit(
                        kind = DeviceEnvKind.ROOT_PROPERTY,
                        operation = DeviceEnvOperation.GET_PROPERTY,
                        originalValue = original,
                        maskedValue = replacement,
                        targetClass = "java.lang.System",
                        targetProperty = key,
                        detectionPattern = ROOT_PROPERTY_PATTERN,
                    )
                }
            },
        )
    }

    companion object {

        private const val ROOT_PROPERTY_PATTERN = "root-property"

        private val PROPERTY_REPLACEMENTS = mapOf(
            "ro.debuggable" to "0",
            "ro.secure" to "1",
            "ro.build.selinux" to "1",
            "ro.build.type" to "user",
            "ro.build.tags" to "release-keys",
        )

        /**
         * 判断属性原始值是否确实是 root 指示值:
         * - ro.debuggable = "1" 表示开启了调试(非 user 构建/root 环境)
         * - ro.secure = "0" 表示安全模式关闭(root 环境)
         * - ro.build.selinux = "0" 表示 SELinux 关闭(root 环境)
         * - ro.build.type = "eng"/"userdebug" 表示非 user 构建
         * - ro.build.tags 不含 "release-keys" 表示非 release 构建
         *
         * 仅在原始值确实为 root 指示值时才进行干预,
         * 避免修改已在正常状态的属性导致 App 初始化路径断裂。
         */
        internal fun isRootIndicativeValue(key: String, value: String): Boolean = when (key) {
            "ro.debuggable" -> value == "1"
            "ro.secure" -> value == "0"
            "ro.build.selinux" -> value == "0"
            "ro.build.type" -> value == "eng" || value == "userdebug"
            "ro.build.tags" -> !value.contains("release-keys")
            else -> false
        }
    }
}
