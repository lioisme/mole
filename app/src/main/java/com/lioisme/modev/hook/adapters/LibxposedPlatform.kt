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
                    hook.before(ModernInvocation(chain, null))
                    val result = chain.proceed()
                    hook.after(ModernInvocation(chain, result))
                    result
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
     */
    private class ModernInvocation(
        private val chain: XposedInterface.Chain,
        override val result: Any?,
    ) : Invocation() {

        override val executable: Executable?
            get() = runCatching { chain.executable }.getOrNull()

        override val thisObject: Any?
            get() = runCatching { chain.thisObject }.getOrNull()

        override val argCount: Int
            get() = chain.args.size

        override fun getArgUnsafe(index: Int): Any? = runCatching { chain.getArg(index) }.getOrNull()
    }
}