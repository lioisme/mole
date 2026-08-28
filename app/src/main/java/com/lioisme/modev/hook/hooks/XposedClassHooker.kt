package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.DeviceEnvKind
import com.lioisme.modev.core.model.DeviceEnvOperation
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.MethodHook
import com.lioisme.modev.hook.ProbeContext

/**
 * Xposed 类存在性检测 Hooker(V31):拦截 ClassLoader.loadClass /
 * BaseDexClassLoader.findClass,仅记录检测事件,不干预返回值。
 *
 * 设计决策:Class.forName 的 Xposed hook 会导致框架 bridge 改变
 * ClassNotFoundException 的传播路径,使 androidx.startup 等库的异常捕获失效
 * → 目标 App 崩溃。因此不再 hook Class.forName,仅监控 ClassLoader 侧。
 */
class XposedClassHooker(
    context: ProbeContext,
) : DeviceEnvBaseHooker(context) {

    override fun install() {
        hookLoadClass()
        hookFindClass()
    }

    private fun hookLoadClass() {
        val method = runCatching {
            ClassLoader::class.java.getMethod("loadClass", String::class.java)
        }.getOrNull() ?: return
        engine.hook(method, object : MethodHook() {
            override fun after(invocation: Invocation) {
                if (!config().xposedClass) return
                val name = invocation.getArg(0) as? String ?: return
                if (isXposedClassName(name)) {
                    emit(
                        kind = DeviceEnvKind.XPOSED_CLASS,
                        operation = DeviceEnvOperation.LOAD_CLASS,
                        originalValue = name,
                        targetClass = name,
                    )
                }
            }
        })
    }

    private fun hookFindClass() {
        val clazz = runCatching { Class.forName("dalvik.system.BaseDexClassLoader") }.getOrNull() ?: return
        val method = runCatching {
            clazz.getMethod("findClass", String::class.java)
        }.getOrNull() ?: return
        engine.hook(method, object : MethodHook() {
            override fun after(invocation: Invocation) {
                if (!config().xposedClass) return
                val name = invocation.getArg(0) as? String ?: return
                if (isXposedClassName(name)) {
                    emit(
                        kind = DeviceEnvKind.XPOSED_CLASS,
                        operation = DeviceEnvOperation.FIND_CLASS,
                        originalValue = name,
                        targetClass = name,
                    )
                }
            }
        })
    }

    private fun isXposedClassName(name: String): Boolean {
        return XPOSED_PREFIXES.any { name.startsWith(it) }
    }

    companion object {
        private val XPOSED_PREFIXES = listOf(
            "de.robv.android.xposed.",
            "com.topjohnwu.lsposed.",
            "org.lsposed.lspd.",
            "org.lsposed.lsplant.",
            "io.github.lsposed.edxposed.",
            "io.github.lsposed.lspd.",
        )
    }
}
