package com.lioisme.modev.hook

import java.lang.reflect.Executable

/**
 * 框架适配器契约:注册 hook 与框架日志。
 *
 * 实现方:
 * - [com.lioisme.modev.hook.adapters.LibxposedPlatform]:libxposed API 102(Vector 2.x);
 * - [com.lioisme.modev.hook.adapters.LegacyPlatform]:legacy Xposed API 82+(旧版 LSPosed ≤93)。
 *
 * 业务层(HookEngine / Hooker)只依赖本接口,与具体框架完全解耦。
 */
interface Platform {

    /**
     * 注册拦截;executable 为 null 或注册失败时返回 false(不抛出,单点失败不阻塞其余 hook)。
     */
    fun register(executable: Executable?, hook: MethodHook): Boolean

    /** 写入框架日志(Xposed log),供宿主未运行时的排障兜底。 */
    fun log(message: String, throwable: Throwable? = null)
}