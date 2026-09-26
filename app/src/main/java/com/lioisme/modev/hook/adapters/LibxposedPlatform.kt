package com.lioisme.modev.hook.adapters

import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.MethodHook
import com.lioisme.modev.hook.Platform
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Executable

/**
 * libxposed(API 102)适配器:Vector 2.x / 含现代加载器的 LSPosed 1.9+。
 *
 * 把 [MethodHook.before/after] 映射到 intercept 内 proceed 的前后;
 * ExceptionMode.PROTECTIVE 保证拦截器自身异常不传染被 Hook 进程。
 */
class LibxposedPlatform(private val xposed: XposedInterface) : Platform {

    override fun register(executable: Executable?, hook: MethodHook): Boolean =
        doRegister(executable, hook, interventional = false)

    /**
     * 干预型注册(V43 T3/D-5):PASSTHROUGH 使回调显式 [Invocation.setThrowable] 设置的
     * 异常抵达调用方;回调体内业务代码全程 runSafely 隔离——缺陷异常绝不外泄,
     * 唯有受控的短路异常(以及原方法自身 proceed() 抛出的真实业务异常)向上传播。
     */
    override fun registerInterventional(executable: Executable?, hook: MethodHook): Boolean =
        doRegister(executable, hook, interventional = true)

    private fun doRegister(executable: Executable?, hook: MethodHook, interventional: Boolean): Boolean {
        if (executable == null) return false
        return try {
            xposed.hook(executable)
                .setPriority(XposedInterface.PRIORITY_DEFAULT)
                .setExceptionMode(
                    if (interventional) {
                        XposedInterface.ExceptionMode.PASSTHROUGH
                    } else {
                        XposedInterface.ExceptionMode.PROTECTIVE
                    },
                )
                .intercept { chain ->
                    val invocation = ModernInvocation(chain, null)
                    // V44 T2.6(审计 P12 双框架对称):非干预回调同样显式 runSafely——
                    // 旧形态把「不崩宿主」全押在框架 ExceptionMode.PROTECTIVE 上,而
                    // Vector 2.x 对 PROTECTIVE 的实现存在语义漂移史(回调异常是否吞掉
                    // 与版本相关);legacy 侧一直是显式包裹。此处统一为双适配器同语义,
                    // 与框架实现解耦。
                    runSafely("hook before crashed") { hook.before(invocation) }
                    if (interventional) {
                        invocation.pendingThrowable?.let { throw it }
                    }
                    val overrides = invocation.argOverrides
                    val result = when {
                        invocation.shortCircuited -> invocation.shortCircuitResult
                        overrides != null -> chain.proceed(overrides)
                        else -> chain.proceed()
                    }
                    val afterInvocation = ModernInvocation(chain, result)
                    runSafely("hook after crashed") { hook.after(afterInvocation) }
                    if (interventional) {
                        afterInvocation.pendingThrowable?.let { throw it }
                    }
                    // V38 修复:after 回调的 setResult 曾被丢弃(拦截器恒返回原
                    // result)→ 现代路径所有「结果改写型」干预(maps 掩蔽/传感器
                    // 伪装/VPN·DNS 隐藏/SSL 结果替换)静默失效。after 短路值优先。
                    if (afterInvocation.shortCircuited) afterInvocation.shortCircuitResult else result
                }
            true
        } catch (t: Throwable) {
            log("hook FAILED: ${executable.declaringClass.name}.${executable.name}: $t", t)
            false
        }
    }

    private fun runSafely(message: String, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            // 业务缺陷不得成为宿主异常(与 PROTECTIVE 同等保护,干预型注册下手工补齐)
            log(message, t)
        }
    }

    override fun log(message: String, throwable: Throwable?) {
        val tag = "Modev"
        if (throwable != null) {
            xposed.log(android.util.Log.WARN, tag, message, throwable)
        } else {
            xposed.log(android.util.Log.WARN, tag, message)
        }
    }

    /**
     * 调用视图。注意:libxposed 的 getThisObject() 对静态方法会抛异常,
     * 统一捕获转为 null,保证业务层空安全。
     *
     * [setResult] 通过拦截器短路实现:记录短路值并标记,
     * 拦截器 lambda 根据标记跳过 [XposedInterface.Chain.proceed]。
     */
    private class ModernInvocation(
        private val chain: XposedInterface.Chain,
        result: Any?,
    ) : Invocation() {

        /** after 阶段可变结果:after 侧 setResult 更新本值,使同一调用的后续读取看到替换(V43 T3 广播一致性)。 */
        private var currentResult: Any? = result
        override val result: Any? get() = currentResult

        var shortCircuited = false
            private set
        var shortCircuitResult: Any? = null
            private set

        /** 干预型注册的异常短路通道(V43 T3):非空 = 以该异常结束调用(仅 interventional 生效)。 */
        var pendingThrowable: Throwable? = null
            private set

        override val executable: Executable?
            get() = runCatching { chain.executable }.getOrNull()

        override val thisObject: Any?
            get() = runCatching { chain.thisObject }.getOrNull()

        override val token: Any? get() = chain

        override val consumed: Boolean
            get() = shortCircuited || pendingThrowable != null

        override val argCount: Int
            get() = chain.args.size

        override fun getArgUnsafe(index: Int): Any? = runCatching { chain.getArg(index) }.getOrNull()

        /** D-11:入参改写缓冲(Chain 无 setArg,经 proceed(Array) 重入生效)。 */
        var argOverrides: Array<Any?>? = null

        override fun setArg(index: Int, value: Any?) {
            val args = argOverrides ?: chain.args.toTypedArray().also { argOverrides = it }
            if (index in args.indices) args[index] = value
        }

        override fun setResult(value: Any?) {
            shortCircuited = true
            shortCircuitResult = value
        }

        override fun setThrowable(t: Throwable) {
            pendingThrowable = t
        }
    }
}
