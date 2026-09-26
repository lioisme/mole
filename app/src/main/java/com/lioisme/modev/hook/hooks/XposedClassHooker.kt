package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.DeviceEnvKind
import com.lioisme.modev.core.model.DeviceEnvOperation
import com.lioisme.modev.hook.ProbeContext

/**
 * Xposed 类存在性检测 Hooker(V43 T2/T3 重构):不再自持 loadClass/findClass 钩子,
 * 改为 [com.lioisme.modev.hook.LoadClassWatcher] 单一监视器的干预型订阅者——
 * 消除与 SSL/okhttp 惰性安装监视器的三处重复注册(Vector 首注册遮蔽,LSPosed 成倍回调)。
 *
 * 干预机制(V43 T3/D-5 修正):检测到 Xposed 类名时经 [com.lioisme.modev.hook.Invocation.setThrowable]
 * 以 ClassNotFoundException 短路加载(异常抵达调用方,原方法不执行)。
 * 旧实现在回调内直接 throw——libxposed PROTECTIVE 与 legacy runSafely 都会吞掉回调异常并
 * 照常执行原方法,拦截静默失效且记录误报 BLOCKED,双框架下均为假干预。
 *
 * 安全设计:ThreadLocal 防递归(本订阅者自身触发的类加载不被二次拦截);
 * BLOCKED 记录仅在 setThrowable 短路真实生效后发出(观测面不再撒谎)。
 */
class XposedClassHooker(
    context: ProbeContext,
) : DeviceEnvBaseHooker(context) {

    private val watcher = context.loadClassWatcher

    private val loadingGuard = ThreadLocal<Boolean>()

    override fun install() {
        watcher.interceptBefore { name, isFindClass, invocation ->
            if (!featureOn(DeviceEnvKind.XPOSED_CLASS)) return@interceptBefore
            if (loadingGuard.get() == true) return@interceptBefore
            if (!isXposedClassName(name)) return@interceptBefore
            emit(
                kind = DeviceEnvKind.XPOSED_CLASS,
                operation = if (isFindClass) {
                    DeviceEnvOperation.FIND_CLASS
                } else {
                    DeviceEnvOperation.LOAD_CLASS
                },
                originalValue = name,
                targetClass = name,
                interventionAction = ACTION_BLOCKED,
            )
            // 短路生效后才可申报 BLOCKED:setThrowable 由框架支持(适配器契约),
            // loadingGuard 覆盖本回调自身引发的类加载(D-17:不钩 forName,接受该缺口)
            loadingGuard.set(true)
            try {
                invocation.setThrowable(
                    ClassNotFoundException(
                        "Xposed class not found: $name",
                        Throwable().apply { stackTrace = emptyArray() },
                    ),
                )
            } finally {
                loadingGuard.remove()
            }
        }
    }

    /** Xposed 类名判定:内置前缀基线 ∪ 用户追加前缀(extraXposedPrefixes)。 */
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
