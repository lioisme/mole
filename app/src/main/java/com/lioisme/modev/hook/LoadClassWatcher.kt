package com.lioisme.modev.hook

import java.util.concurrent.CopyOnWriteArrayList

/**
 * 类加载观察单一监视器(V43 T2/D-4)。
 *
 * 背景:`ClassLoader.loadClass` 曾被三处 hooker 各自注册钩子(Xposed 类隐藏、SSL 惰性
 * 安装、okhttp 惰性安装)——AGENTS 已知坑明载「Vector 2.2 同一方法的第二个 hook 不执行」,
 * 多注册点互相静默遮蔽;LSPosed 下则每次类加载付 N 倍回调。本类落实「共享单一监视器」规约:
 * **全进程仅注册一次** loadClass/findClass 钩子,按订阅表广播分派。
 *
 * 订阅模型:
 * - [interceptBefore]:在原始 loadClass/findClass 执行前收到
 *   (className, isFindClass, invocation),干预型订阅者经 [Invocation.setResult]/
 *   [Invocation.setThrowable](T3)短路加载;
 * - [observeAfter]:类加载正常返回后收到 (className, isFindClass),观测型订阅者(惰性安装)消费
 *   (异常路径 after 不执行,与现代/legacy 双适配器语义一致);
 * - 单订阅者异常隔离(runCatching),不连坐其余订阅者。
 *
 * 懒安装:首次订阅才注册框架钩子;无订阅者 = loadClass 零钩挂(开关全关路径的额外红利)。
 * 线程:安装一次(volatile + 锁守卫),订阅表 CopyOnWriteArrayList(回调线程读多写零)。
 */
class LoadClassWatcher(
    private val engine: HookEngine,
) {

    /** 干预型订阅:before 阶段回调(可短路原方法)。 */
    fun interface BeforeSubscriber {
        fun onBefore(className: String, isFindClass: Boolean, invocation: Invocation)
    }

    /** 观测型订阅:after(加载成功返回)阶段回调。 */
    fun interface AfterSubscriber {
        fun onAfter(className: String, isFindClass: Boolean)
    }

    private val beforeSubs = CopyOnWriteArrayList<BeforeSubscriber>()
    private val afterSubs = CopyOnWriteArrayList<AfterSubscriber>()

    @Volatile
    private var installed = false
    private val installLock = Any()

    fun interceptBefore(sub: BeforeSubscriber) {
        ensureInstalled()
        beforeSubs += sub
    }

    fun observeAfter(sub: AfterSubscriber) {
        ensureInstalled()
        afterSubs += sub
    }

    /** 首次订阅时集中注册 loadClass + findClass 钩子(仅此一次;失败不重试,平台缺方法即永久降级)。 */
    private fun ensureInstalled() {
        if (installed) return
        synchronized(installLock) {
            if (installed) return
            installed = true
            val loadClass = runCatching {
                ClassLoader::class.java.getMethod("loadClass", String::class.java)
            }.getOrNull()
            if (loadClass != null) {
                // 干预型注册:订阅者可经 Invocation.setThrowable 把 CNFE 抵达调用方
                engine.hookThrowing(loadClass, object : MethodHook() {
                    override fun before(invocation: Invocation) {
                        val name = invocation.getArg(0) as? String ?: return
                        for (s in beforeSubs) {
                            if (invocation.consumed) break // 首个短路生效,后续订阅者不覆写
                            runCatching { s.onBefore(name, false, invocation) }
                        }
                    }

                    override fun after(invocation: Invocation) {
                        val name = invocation.getArg(0) as? String ?: return
                        for (s in afterSubs) runCatching { s.onAfter(name, false) }
                    }
                })
            }
            val findClass = runCatching {
                Class.forName("dalvik.system.BaseDexClassLoader", false, null)
                    .getDeclaredMethod("findClass", String::class.java)
            }.getOrNull()
            if (findClass != null) {
                engine.hookThrowing(findClass, object : MethodHook() {
                    override fun before(invocation: Invocation) {
                        val name = invocation.getArg(0) as? String ?: return
                        for (s in beforeSubs) {
                            if (invocation.consumed) break // 首个短路生效,后续订阅者不覆写
                            runCatching { s.onBefore(name, true, invocation) }
                        }
                    }

                    override fun after(invocation: Invocation) {
                        val name = invocation.getArg(0) as? String ?: return
                        for (s in afterSubs) runCatching { s.onAfter(name, true) }
                    }
                })
            }
        }
    }
}
