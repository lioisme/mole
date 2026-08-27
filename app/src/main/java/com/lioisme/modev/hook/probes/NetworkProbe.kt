package com.lioisme.modev.hook.probes

import com.lioisme.modev.core.model.RecordTypes
import com.lioisme.modev.hook.Probe
import com.lioisme.modev.hook.ProbeContext
import com.lioisme.modev.hook.hooks.NetworkRequestHooker
import com.lioisme.modev.hook.hooks.ProxyHideHooker
import com.lioisme.modev.hook.hooks.SSLBypassHooker
import com.lioisme.modev.hook.hooks.VpnHideHooker

/**
 * 网络环境监听探针(V30 一等公民):收敛全部 4 个 Hooker
 * (SSL 绕过 + 代理隐藏 + VPN 隐藏 + 请求监控)。
 */
class NetworkProbe : Probe {

    override val recordType: String = RecordTypes.NETWORK

    override fun install(context: ProbeContext) {
        val hookers = listOf(
            SSLBypassHooker(context),
            ProxyHideHooker(context),
            VpnHideHooker(context),
            NetworkRequestHooker(context),
        )
        for (hooker in hookers) {
            hooker.install()
        }
    }
}
