package com.lioisme.modev.hook

import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Executable

/**
 * 统一 Hook 入口封装:优先级默认、异常模式 PROTECTIVE(拦截器自身异常不会传染宿主)。
 * 同时支持方法与构造器 hook。
 */
class HookEngine(private val xposed: XposedInterface) {

    fun hook(executable: Executable?, hooker: XposedInterface.Hooker): XposedInterface.HookHandle? {
        if (executable == null) return null
        return try {
            xposed.hook(executable)
                .setPriority(XposedInterface.PRIORITY_DEFAULT)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(hooker)
        } catch (t: Throwable) {
            android.util.Log.w("Modev", "hook FAILED: ${executable.declaringClass.name}.${executable.name}: $t")
            // 单个 hook 注册失败不阻塞其余 hook
            null
        }
    }
}