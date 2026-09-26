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

    /**
     * 干预型注册(V43 T3/D-5):支持 [Invocation.setThrowable] 把异常抛给调用方。
     * legacy 的 MethodHookParam.setThrowable 天然可用(默认转 [register]);
     * libxposed 需 PASSTHROUGH + 回调体内异常隔离,由适配器覆写。
     */
    fun registerInterventional(executable: Executable?, hook: MethodHook): Boolean =
        register(executable, hook)

    /** 写入框架日志(Xposed log),供宿主未运行时的排障兜底。 */
    fun log(message: String, throwable: Throwable? = null)
}
