package com.lioisme.modev

import com.lioisme.modev.hook.HookManager
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam

/**
 * Xposed 入口(注册于 META-INF/xposed/java_init.list),基于 libxposed API 102。
 * 模块不主动过滤包:由宿主在 LSPosed 中配置 scope(默认进程直接命中,
 * 分离进程 processName 保留 ":sub" 后缀以便在日志中区分)。
 */
class ModevModule : XposedModule() {

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        HookManager.install(this, param.processName, param.processName)
    }

    companion object {
        private const val TAG = "Modev"
    }
}