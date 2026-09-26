package com.lioisme.modev.hook

import java.lang.reflect.Executable
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 框架方法单一注册点 + 广播分派(V43 T3/D-4 通用化)。
 *
 * 背景(V2.9):`Settings$Secure.getString`、`System.getProperty`、`getPackageInfo`
 * 等热门平台方法被多个 hooker 各自注册钩子——AGENTS 已知坑实锤 Vector「同一方法仅
 * 首注册生效」,后注册者静默失声(干预型后注册 = 安全功能失效;观测型后注册 = 记录缺失);
 * LSPosed 下则每次调用 N 倍回调分发。
 *
 * 模型:每个 [Executable] 全进程**只注册一个**框架钩子(干预型,异常隔离见适配器
 * runSafely);订阅者按注册序依次收到 before/after 事件:
 * - before 子链:任一订阅者 `setResult`/`setThrowable` 短路后,后续订阅者 before 仍执行
 *   (各订阅者首行门控 + 键空间判定,与多注册时代 LSPosed 行为等价;短路值以最后一次
 *   setResult 为准,键空间不相交的订阅者互不影响);
 * - after 子链:仅原方法正常返回后执行;
 * - 单订阅者异常隔离(runCatching),不连坐。
 *
 * 订阅表 CopyOnWriteArrayList(回调线程读多写零);注册表 synchronized。
 */
class SharedMethodBroadcast(
    private val engine: HookEngine,
) {

    /** 订阅回调:与 [MethodHook] 同形,方法级共享。 */
    interface Sub {
        fun before(invocation: Invocation) {}
        fun after(invocation: Invocation, result: Any?) {}
    }

    private class Registration {
        val subs = CopyOnWriteArrayList<Sub>()
    }

    /**
     * 注册表按**方法签名**索引:`Class.getMethod` 不保证返回同一实例
     * (反射对象可能为拷贝),Executable 身份比较会漏合并;签名键跨 hooker 稳定。
     */
    private val registrations = HashMap<String, Registration>()

    private fun keyOf(method: Executable): String {
        val name = if (method is java.lang.reflect.Constructor<*>) "<init>" else method.name
        return buildString {
            append(method.declaringClass.name).append('#').append(name)
            method.parameterTypes.forEach { append('#').append(it.name) }
        }
    }

    /**
     * 订阅 [method] 的 before/after 事件;该方法的框架钩子仅注册一次(干预型),
     * 后续订阅仅追加回调。method 为 null(平台缺方法)静默跳过。
     */
    fun subscribe(method: Executable?, sub: Sub) {
        if (method == null) return
        val reg = synchronized(this) {
            registrations.getOrPut(keyOf(method)) {
                Registration().also { r ->
                    engine.hookThrowing(
                        method,
                        object : MethodHook() {
                            override fun before(invocation: Invocation) {
                                for (s in r.subs) {
                                    if (invocation.consumed) break // 首个短路生效,后续订阅者不覆写
                                    runCatching { s.before(invocation) }
                                }
                            }

                            override fun after(invocation: Invocation) {
                                // 每订阅者现读 result:前序 after 侧 setResult 对后续可见(链式语义)
                                for (s in r.subs) runCatching { s.after(invocation, invocation.result) }
                            }
                        },
                    )
                }
            }
        }
        reg.subs += sub
    }

    /** 测试/诊断:已建立共享注册的方法数。 */
    internal val registeredCount: Int get() = synchronized(this) { registrations.size }
}
