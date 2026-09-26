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

    /**
     * 运行期 `returnEarly` 字段句柄(V44 T2.6):一次性解析,缺失即 null。
     * 不缓存「已解析失败」之外的状态——句柄本身线程安全(Field.get 无状态)。
     */
    private val returnEarlyField: java.lang.reflect.Field? by lazy {
        runCatching {
            XC_MethodHook.MethodHookParam::class.java
                .getDeclaredField("returnEarly")
                .also { it.isAccessible = true }
        }.getOrNull()
    }

    private fun isReturnEarly(param: XC_MethodHook.MethodHookParam): Boolean {
        val field = returnEarlyField ?: return false
        return runCatching { field.getBoolean(param) }.getOrDefault(false)
    }

    /** 调用视图:legacy 参数数组越界安全取值。[setResult] 通过 MethodHookParam.setResult 实现。 */
    private class LegacyInvocation(
        private val param: XC_MethodHook.MethodHookParam,
        @Suppress("UNUSED_PARAMETER") initialResult: Any?,
    ) : Invocation() {

        /**
         * 现读 param 结果(V43 T3 广播链一致性):before 阶段恒 null(未执行),
         * after 阶段反映前序订阅者的 setResult 替换(与 ModernInvocation currentResult 对齐)。
         */
        override val result: Any? get() = runCatching { param.result }.getOrNull()

        override val executable: Executable?
            get() = param.method as? Executable

        override val thisObject: Any?
            get() = param.thisObject

        override val token: Any? get() = param

        override val consumed: Boolean
            // V44 T2.6(审计 P14):API 82 的 MethodHookParam 无 hasResult(),旧形态以
            // `result != null` 近似短路判定 —— 对 `setResult(null)`(吞行/清空结果类
            // 干预)完全失明,后续订阅者会覆写前者的 null 短路。运行期实现
            // (LSPosed/EdXposed 的 XposedBridge)持有 `returnEarly` 布尔字段
            // (setResult 置位、setThrowable 不置位),经反射读取;字段缺失
            // (非常规实现/单测桩 jar)退回旧近似语义。
            get() = isReturnEarly(param) || param.result != null || param.hasThrowable()

        override val argCount: Int
            get() = param.args?.size ?: 0

        override fun getArgUnsafe(index: Int): Any? = param.args?.get(index)

        override fun setArg(index: Int, value: Any?) {
            param.args?.set(index, value)
        }

        override fun setResult(value: Any?) {
            param.result = value
        }

        /**
         * V43 T3/D-5:legacy 的 MethodHookParam.setThrowable 天然抵达调用方
         * (before 阶段设置即跳过原方法),故干预型注册无需特殊模式,仅需本方法可用。
         */
        override fun setThrowable(t: Throwable) {
            param.setThrowable(t)
        }
    }
}
