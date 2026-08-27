package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.DeviceEnvKind
import com.lioisme.modev.core.model.DeviceEnvOperation
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.MethodHook
import com.lioisme.modev.hook.ProbeContext

/**
 * Root 系统属性检测干预 Hooker(V31):拦截 System.getProperty 读取 root 指示属性,
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

        engine.hook(method, object : MethodHook() {
            override fun after(invocation: Invocation) {
                if (!config().rootProperty) return
                val key = invocation.getArg(0) as? String ?: return
                val replacement = PROPERTY_REPLACEMENTS[key] ?: return
                val original = invocation.result as? String
                if (original == replacement) return
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
        })
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
    }
}
