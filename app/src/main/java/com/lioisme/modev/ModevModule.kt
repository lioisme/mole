package com.lioisme.modev

import android.content.SharedPreferences
import com.lioisme.modev.framework.FrameworkBridge
import com.lioisme.modev.framework.RemotePrefsBridge
import com.lioisme.modev.hook.HookInstaller
import com.lioisme.modev.hook.adapters.LibxposedPlatform
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.service.XposedService

/**
 * 现代 Xposed 入口(注册于 META-INF/xposed/java_init.list),基于 libxposed API 102。
 * 由 Vector 2.x / 含现代加载器的 LSPosed 加载;模块不主动过滤包,由宿主配置 scope
 * (分离进程 processName 保留 ":sub" 后缀以便在日志中区分)。
 */
class ModevModule : XposedModule() {

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        // V43 T1:入口级兜底(与 LegacyModuleEntry 对称)——装配残余异常绝不外抛框架
        // 加载器,杜绝「注入失败拖垮宿主启动」;失败仅记日志,宿主按无模块行为运行。
        try {
            install(param)
        } catch (t: Throwable) {
            android.util.Log.e(TAG, "Modev: module init crashed, host continues without hooks", t)
        }
    }

    private fun install(param: ModuleLoadedParam) {
        // 注入进程:远程偏好经框架 API 读取(框架侧按"本进程加载的模块"身份提供,
        // 而非调用者 UID);legacy 无此能力时 runCatching 降级为 null,配置走 TCP 推送
        val modulePackage = runCatching { moduleApplicationInfo.packageName }.getOrNull()
        // Vector 2.2 注入进程不会投递 XposedService binder(XposedProvider 仅在模块自身进程出现),
        // 而 LSPosed 1.9+ 可向注入进程投递;先等 service,再兜底 Vector 上的框架文件 API。
        FrameworkBridge.ensureRegistered()
        val remotePrefsProvider: (() -> SharedPreferences?)? = if (modulePackage != null) {
            {
                val svc = FrameworkBridge.serviceFlow.value
                if (svc != null) {
                    // 本类仅在现代框架(含 service 库)加载,转型安全
                    runCatching { (svc as XposedService).getRemotePreferences(RemotePrefsBridge.PREFS_KEY) }.getOrNull()
                } else {
                    runCatching { getRemotePreferences(modulePackage) }.getOrNull()
                }
            }
        } else {
            null
        }
        // 主包名归并:分离进程(pkg:sub)记录统一归档到主包名下,processName 保留原值区分
        HookInstaller(
            platform = LibxposedPlatform(this),
            packageName = param.processName.substringBefore(':'),
            processName = param.processName,
            remotePrefsProvider = remotePrefsProvider,
        ).install()
    }

    companion object {
        private const val TAG = "Modev"
    }
}
