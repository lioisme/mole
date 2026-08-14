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
}