package com.lioisme.modev

import com.lioisme.modev.hook.HookManager
import com.lioisme.modev.hook.adapters.LibxposedPlatform
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam

/**
 * 现代 Xposed 入口(注册于 META-INF/xposed/java_init.list),基于 libxposed API 102。
 * 由 Vector 2.x / 含现代加载器的 LSPosed 加载;模块不主动过滤包,由宿主配置 scope
 * (分离进程 processName 保留 ":sub" 后缀以便在日志中区分)。
 */
class ModevModule : XposedModule() {

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        HookManager.install(LibxposedPlatform(this), param.processName, param.processName)
    }

    companion object {
        private const val TAG = "Modev"
    }
}