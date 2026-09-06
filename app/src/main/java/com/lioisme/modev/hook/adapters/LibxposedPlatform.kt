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

    override fun register(executable: Executable?, hook: MethodHook): Boolean {
        if (executable == null) return false
        return try {
            xposed.hook(executable)
                .setPriority(XposedInterface.PRIORITY_DEFAULT)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    val invocation = ModernInvocation(chain, null)
                    hook.before(invocation)
                    val overrides = invocation.argOverrides
                    val result = when {
                        invocation.shortCircuited -> invocation.shortCircuitResult
                        overrides != null -> chain.proceed(overrides)
                        else -> chain.proceed()
                    }
                    val afterInvocation = ModernInvocation(chain, result)
                    hook.after(afterInvocation)
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
        override val result: Any?,
    ) : Invocation() {

        var shortCircuited = false
            private set
        var shortCircuitResult: Any? = null
            private set

        override val executable: Executable?
            get() = runCatching { chain.executable }.getOrNull()

        override val thisObject: Any?
            get() = runCatching { chain.thisObject }.getOrNull()

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
    }
}