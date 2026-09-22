package com.lioisme.modev.hook.probes

import com.lioisme.modev.core.model.HookSwitch
import com.lioisme.modev.core.model.RecordTypes
import com.lioisme.modev.hook.InterventionProbeMarker
import com.lioisme.modev.hook.Probe
import com.lioisme.modev.hook.ProbeContext
import com.lioisme.modev.hook.hooks.CrashPreventHooker
import com.lioisme.modev.hook.hooks.ExitInterceptHooker
import com.lioisme.modev.hook.hooks.MediaProjectionHooker
import com.lioisme.modev.hook.hooks.ProcessControlBaseHooker
import com.lioisme.modev.hook.hooks.ScreenRecordHooker
import com.lioisme.modev.hook.hooks.SurfaceBypassHooker

/**
 * 进程控制监听探针(V34 增强):装配五 Hooker——退出拦截(System.exit/
 * killProcess)、崩溃预防(UncaughtExceptionHandler)、录屏拦截(FLAG_SECURE
 * 多路径)、MediaProjection 权限自动授权、Surface 截图/录屏绕过。
 * recordType = [RecordTypes.PROCESS_CONTROL]。
 */
class ProcessControlProbe : Probe, InterventionProbeMarker {

    override val recordType: String = RecordTypes.PROCESS_CONTROL

    /** V44 T2.4 冷启动裁剪:进程控制域经 `processControlListen` master ∧ kind 双门控。 */
    override fun enabledIn(switch: HookSwitch): Boolean = switch.processControlListen

    override fun install(context: ProbeContext) {
        val hookers: List<ProcessControlBaseHooker> =
            listOf(
                ExitInterceptHooker(context),
                CrashPreventHooker(context),
                ScreenRecordHooker(context),
                MediaProjectionHooker(context),
                SurfaceBypassHooker(context),
            )
        for (hooker in hookers) {
            hooker.install()
        }
    }
}
