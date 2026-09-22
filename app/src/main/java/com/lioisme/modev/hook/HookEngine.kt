package com.lioisme.modev.hook

import java.lang.reflect.Executable

/**
 * 统一 Hook 注册入口:委托 [Platform],单点失败返回 false 不阻塞其余 hook。
 * 业务层(Hooker)只依赖本类与 [MethodHook],与具体框架实现完全解耦。
 */
class HookEngine(private val platform: Platform) {

    fun hook(executable: Executable?, hook: MethodHook): Boolean {
        if (executable == null) return false
        return platform.register(executable, hook)
    }

    /**
     * 干预型注册(V43 T3/D-5):钩子可在回调内经 [Invocation.setThrowable] 以异常
     * 结束调用(PASSTHROUGH 语义抵达调用方),同时回调体异常隔离保留——
     * 业务缺陷不会传染宿主。仅供需要「抛异常契约」的干预使用
     * (Xposed 类隐藏/Root exec 拦截/getPackageInfo 隐藏)。
     */
    fun hookThrowing(executable: Executable?, hook: MethodHook): Boolean {
        if (executable == null) return false
        return platform.registerInterventional(executable, hook)
    }
}