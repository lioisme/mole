package com.lioisme.modev.hook.probes

import com.lioisme.modev.core.model.HookSwitch
import com.lioisme.modev.core.model.RecordTypes
import com.lioisme.modev.hook.InterventionProbeMarker
import com.lioisme.modev.hook.Probe
import com.lioisme.modev.hook.ProbeContext
import com.lioisme.modev.hook.hooks.DnsHideHooker
import com.lioisme.modev.hook.hooks.NetworkRequestHooker
import com.lioisme.modev.hook.hooks.ProxyHideHooker
import com.lioisme.modev.hook.hooks.SSLBypassHooker
import com.lioisme.modev.hook.hooks.VpnHideHooker

/**
 * 网络环境监听探针:收敛全部 5 个 Hooker
 * (SSL 绕过 + 代理隐藏 + VPN 隐藏 + 请求监控 + DNS 监控)。
 */
class NetworkProbe : Probe, InterventionProbeMarker {

    override val recordType: String = RecordTypes.NETWORK

    /**
     * V44 T2.4 冷启动裁剪:网络域(含 SSL 绕过 / 代理·VPN 隐藏等干预型 kind)
     * 全部经 `networkListen` master ∧ kind 双门控——master 关 = 零能力可生效,
     * 整组数百个方法钩的装配代价可省。
     */
    override fun enabledIn(switch: HookSwitch): Boolean = switch.networkListen

    override fun install(context: ProbeContext) {
        val hookers = listOf(
            SSLBypassHooker(context),
            ProxyHideHooker(context),
            VpnHideHooker(context),
            NetworkRequestHooker(context),
            DnsHideHooker(context),
        )
        for (hooker in hookers) {
            hooker.install()
        }
    }
}
