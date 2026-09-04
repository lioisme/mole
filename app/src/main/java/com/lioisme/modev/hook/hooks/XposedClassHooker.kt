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
        // D-17 回滚 D-12b:禁止钩取 Class.forName(String) —— 其语义依赖
        // Reflection.getCallerClass(),LSPlant 桥帧替换调用方 → 应用侧单参
        // forName 加载自身类全部 CNFE(androidx.startup/okhttp 启动即崩)。
        // forName 探测面为已知覆盖缺口(Vector 原生解析绕过 Java 钩),接受。
    }

    private fun hookLoadClass() {
        val method = runCatching {
            ClassLoader::class.java.getMethod("loadClass", String::class.java)
        }.getOrNull() ?: return
        engine.hook(method, object : MethodHook() {
            override fun before(invocation: Invocation) {
                if (!featureOn(DeviceEnvKind.XPOSED_CLASS)) return
                if (loadingGuard.get() == true) return
                val name = invocation.getArg(0) as? String ?: return
                if (isXposedClassName(name)) {
                    // D-12 修复:记录必须先于抛出(after() 在异常路径永不执行)
                    emit(
                        kind = DeviceEnvKind.XPOSED_CLASS,
                        operation = DeviceEnvOperation.LOAD_CLASS,
                        originalValue = name,
                        targetClass = name,
                        interventionAction = ACTION_BLOCKED,
                    )
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
        })
    }

    private fun hookFindClass() {
        val clazz = runCatching { Class.forName("dalvik.system.BaseDexClassLoader") }.getOrNull() ?: return
        val method = runCatching {
            clazz.getMethod("findClass", String::class.java)
        }.getOrNull() ?: return
        engine.hook(method, object : MethodHook() {
            override fun before(invocation: Invocation) {
                if (!featureOn(DeviceEnvKind.XPOSED_CLASS)) return
                if (loadingGuard.get() == true) return
                val name = invocation.getArg(0) as? String ?: return
                if (isXposedClassName(name)) {
                    // D-12 修复:记录先于抛出
                    emit(
                        kind = DeviceEnvKind.XPOSED_CLASS,
                        operation = DeviceEnvOperation.FIND_CLASS,
                        originalValue = name,
                        targetClass = name,
                        interventionAction = ACTION_BLOCKED,
                    )
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
        })
    }

    /** Xposed 类名判定(V37 T2.4):内置前缀基线 ∪ 用户追加前缀(extraXposedPrefixes)。 */
    private fun isXposedClassName(name: String): Boolean {
        return XPOSED_PREFIXES.any { name.startsWith(it) } ||
            config().feature.extraXposedPrefixes.any { it.isNotBlank() && name.startsWith(it) }
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
