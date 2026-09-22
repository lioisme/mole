package com.lioisme.modev.hook.probes

import com.lioisme.modev.core.model.HookSwitch
import com.lioisme.modev.core.model.RecordTypes
import com.lioisme.modev.hook.Probe
import com.lioisme.modev.hook.ProbeContext
import com.lioisme.modev.hook.hooks.WebViewHooker

/**
 * WebView 监听探针(V29 一等公民):装配 WebView 全生命周期 Hooker。
 *
 * 新增监听能力 = 在 [com.lioisme.modev.hook.HookInstaller] 注册本 Probe,
 * 传输/存储/UI 管线零改动。
 */
class WebViewProbe : Probe {

    override val recordType: String = RecordTypes.WEBVIEW

    /** V44 T2.4 冷启动裁剪:WebView 域全部 kind 经 `webviewListen` master ∧ kind 双门控。 */
    override fun enabledIn(switch: HookSwitch): Boolean = switch.webviewListen

    override fun install(context: ProbeContext) {
        val hooker = WebViewHooker(context)
        hooker.install()
    }
}
