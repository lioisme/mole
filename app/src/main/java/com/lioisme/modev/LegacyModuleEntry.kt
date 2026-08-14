package com.lioisme.modev

import com.lioisme.modev.hook.HookManager
import com.lioisme.modev.hook.adapters.LegacyPlatform
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * legacy Xposed 入口(注册于 assets/xposed_init):旧版 LSPosed(API ≤93)/ EdXposed 加载本入口。
 *
 * 与 [ModevModule](libxposed API 102)共用同一套 HookManager 中性引擎;
 * 任一框架下只会加载一个入口(现代加载器优先 java_init.list,legacy 加载器只读 xposed_init)。
 * 初始化全程 try/catch 兜底:legacy 框架无 PROTECTIVE 模式,异常不得传染被注入进程。
 */
class LegacyModuleEntry : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            HookManager.install(LegacyPlatform, lpparam.processName, lpparam.processName)
        } catch (t: Throwable) {
            XposedBridge.log("Modev: module init crashed: $t")
            XposedBridge.log(t)
        }
    }
}