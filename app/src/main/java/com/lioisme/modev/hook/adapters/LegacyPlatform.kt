package com.lioisme.modev.hook.adapters

import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.MethodHook
import com.lioisme.modev.hook.Platform
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.Method

/**
 * legacy Xposed API(82+)适配器:旧版 LSPosed(API ≤93)/ EdXposed 等。
 *
 * 把 [MethodHook.before/after] 直接映射到 XC_MethodHook.beforeHookedMethod / afterHookedMethod;
 * 原方法抛异常时跳过 after(与现代适配器 proceed 抛出即跳过的语义一致);
 * 无框架级 PROTECTIVE 模式,回调分发由本类 try/catch 兜底,异常不传染被 Hook 进程。
 */
object LegacyPlatform : Platform {

    override fun register(executable: Executable?, hook: MethodHook): Boolean {
        if (executable == null) return false
        val callback = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                runSafely("hook before crashed") { hook.before(LegacyInvocation(param, null)) }
            }

            override fun afterHookedMethod(param: MethodHookParam) {
                if (param.hasThrowable()) return
                runSafely("hook after crashed") { hook.after(LegacyInvocation(param, param.getResult())) }
            }
        }
        return try {
            when (executable) {
                is Method -> XposedHelpers.findAndHookMethod(
                    executable.declaringClass,
                    executable.name,
                    *hookArgs(executable.parameterTypes, callback),
                )
                is Constructor<*> -> XposedHelpers.findAndHookConstructor(
                    executable.declaringClass,
                    *hookArgs(executable.parameterTypes, callback),
                )
                else -> {
                    log("unsupported executable type: ${executable::class.java.name}")
                    return false
                }
            }
            true
        } catch (t: Throwable) {
            log("hook FAILED: ${executable.declaringClass.name}.${executable.name}: $t", t)
            false
        }
    }

    override fun log(message: String, throwable: Throwable?) {
        XposedBridge.log("Modev: $message")
        if (throwable != null) {
            XposedBridge.log(throwable)
        }
    }

    private fun runSafely(message: String, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            log(message, t)
        }
    }

    /** findAndHookMethod/findAndHookConstructor 的 vararg 组装:参数类型数组 + 末尾回调。 */
    private fun hookArgs(parameterTypes: Array<Class<*>>, callback: XC_MethodHook): Array<Any?> {
        val args = arrayOfNulls<Any?>(parameterTypes.size + 1)
        System.arraycopy(parameterTypes, 0, args, 0, parameterTypes.size)
        args[parameterTypes.size] = callback
        return args
    }

    /** 调用视图:legacy 参数数组越界安全取值。[setResult] 通过 MethodHookParam.setResult 实现。 */
    private class LegacyInvocation(
        private val param: XC_MethodHook.MethodHookParam,
        override val result: Any?,
    ) : Invocation() {

        override val executable: Executable?
            get() = param.method as? Executable

        override val thisObject: Any?
            get() = param.thisObject

        override val argCount: Int
            get() = param.args?.size ?: 0

        override fun getArgUnsafe(index: Int): Any? = param.args?.get(index)

        override fun setResult(value: Any?) {
            param.result = value
        }
    }
}