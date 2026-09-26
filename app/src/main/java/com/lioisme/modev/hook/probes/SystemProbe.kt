package com.lioisme.modev.hook.probes

import com.lioisme.modev.core.model.HookSwitch
import com.lioisme.modev.core.model.RecordTypes
import com.lioisme.modev.hook.Probe
import com.lioisme.modev.hook.ProbeContext
import com.lioisme.modev.hook.hooks.AppInfoHooker
import com.lioisme.modev.hook.hooks.LogcatHooker
import com.lioisme.modev.hook.hooks.SignatureMonitorHooker
import com.lioisme.modev.hook.hooks.SystemBaseHooker

/**
 * 系统信息监听探针:装配三 Hooker——Logcat 捕获、
 * 应用信息采集、签名读取监控。
 * recordType = [RecordTypes.SYSTEM]。
 */
class SystemProbe : Probe {

    override val recordType: String = RecordTypes.SYSTEM

    /** V44 T2.4 冷启动裁剪:系统信息域全部 kind 经 `systemListen` master ∧ kind 双门控。 */
    override fun enabledIn(switch: HookSwitch): Boolean = switch.systemListen

    override fun install(context: ProbeContext) {
        val hookers: List<SystemBaseHooker> =
            listOf(
                LogcatHooker(context),
                AppInfoHooker(context),
                SignatureMonitorHooker(context),
            )
        for (hooker in hookers) {
            hooker.install()
        }
    }
}
