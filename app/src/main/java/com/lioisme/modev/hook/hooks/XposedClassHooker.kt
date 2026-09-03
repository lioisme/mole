package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.DeviceEnvKind
import com.lioisme.modev.core.model.DeviceEnvOperation
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.MethodHook
import com.lioisme.modev.hook.ProbeContext

/**
 * Xposed 类存在性检测 Hooker(V34 增强):拦截 ClassLoader.loadClass /
 * BaseDexClassLoader.findClass,当检测到 Xposed 相关类被加载时,
 * 抛出 ClassNotFoundException 以隐藏 Xposed 框架的存在。
 *
 * 干预机制:检测到 Xposed 类名时,在 before() 中抛出 ClassNotFoundException,
 * 阻止类加载完成,使检测框架认为 Xposed 未安装。
 *
 * 安全设计:使用 ThreadLocal 防止递归 hook(本 hook 自身触发的类加载不会被二次拦截)。
 */
class XposedClassHooker(
    context: ProbeContext,
) : DeviceEnvBaseHooker(context) {

    private val loadingGuard = ThreadLocal<Boolean>()

    override fun install() {
        hookLoadClass()
        hookFindClass()
    }

    private fun hookLoadClass() {
        val method = runCatching {
            ClassLoader::class.java.getMethod("loadClass", String::class.java)
        }.getOrNull() ?: return
        engine.hook(method, object : MethodHook() {
            override fun before(invocation: Invocation) {
                if (!config().xposedClass) return
                if (loadingGuard.get() == true) return
                val name = invocation.getArg(0) as? String ?: return
                if (isXposedClassName(name)) {
                    loadingGuard.set(true)
                    try {
                        throw ClassNotFoundException(
                            "Xposed class not found: $name",
                            Throwable().apply { stackTrace = emptyArray() },
                        )
                    } finally {
                        loadingGuard.remove()
                    }
                }
            }

            override fun after(invocation: Invocation) {
                if (!config().xposedClass) return
                val name = invocation.getArg(0) as? String ?: return
                if (isXposedClassName(name)) {
                    emit(
                        kind = DeviceEnvKind.XPOSED_CLASS,
                        operation = DeviceEnvOperation.LOAD_CLASS,
                        originalValue = name,
                        targetClass = name,
                        interventionAction = ACTION_BLOCKED,
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
            override fun before(invocation: Invocation) {
                if (!config().xposedClass) return
                if (loadingGuard.get() == true) return
                val name = invocation.getArg(0) as? String ?: return
                if (isXposedClassName(name)) {
                    loadingGuard.set(true)
                    try {
                        throw ClassNotFoundException(
                            "Xposed class not found: $name",
                            Throwable().apply { stackTrace = emptyArray() },
                        )
                    } finally {
                        loadingGuard.remove()
                    }
                }
            }

            override fun after(invocation: Invocation) {
                if (!config().xposedClass) return
                val name = invocation.getArg(0) as? String ?: return
                if (isXposedClassName(name)) {
                    emit(
                        kind = DeviceEnvKind.XPOSED_CLASS,
                        operation = DeviceEnvOperation.FIND_CLASS,
                        originalValue = name,
                        targetClass = name,
                        interventionAction = ACTION_BLOCKED,
                    )
                }
            }
        })
    }

    private fun isXposedClassName(name: String): Boolean {
        return XPOSED_PREFIXES.any { name.startsWith(it) }
    }

    companion object {
        private const val ACTION_BLOCKED = "BLOCKED"

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
