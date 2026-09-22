package com.lioisme.modev.hook.probes

import com.lioisme.modev.core.model.HookSwitch
import com.lioisme.modev.core.model.RecordTypes
import com.lioisme.modev.hook.InterventionProbeMarker
import com.lioisme.modev.hook.Probe
import com.lioisme.modev.hook.ProbeContext
import com.lioisme.modev.hook.hooks.DetectionMonitorHooker
import com.lioisme.modev.hook.hooks.DeviceBatteryHooker
import com.lioisme.modev.hook.hooks.DeviceCpuHooker
import com.lioisme.modev.hook.hooks.DeviceEnvBaseHooker
import com.lioisme.modev.hook.hooks.DeviceSysPropHooker
import com.lioisme.modev.hook.hooks.DeviceTelephonyHooker
import com.lioisme.modev.hook.hooks.EmulatorBuildHooker
import com.lioisme.modev.hook.hooks.EmulatorFileHooker
import com.lioisme.modev.hook.hooks.EmulatorNetworkHooker
import com.lioisme.modev.hook.hooks.EmulatorSensorHooker
import com.lioisme.modev.hook.hooks.FridaThreadHooker
import com.lioisme.modev.hook.hooks.ProcCgroupHooker
import com.lioisme.modev.hook.hooks.ProcMountInfoHooker
import com.lioisme.modev.hook.hooks.ProcNetHooker
import com.lioisme.modev.hook.hooks.ProcScanBroadcaster
import com.lioisme.modev.hook.hooks.ProcStatusHooker
import com.lioisme.modev.hook.hooks.RootExecHooker
import com.lioisme.modev.hook.hooks.RootFileHooker
import com.lioisme.modev.hook.hooks.RootPackageManagerHooker
import com.lioisme.modev.hook.hooks.RootPropertyHooker
import com.lioisme.modev.hook.hooks.SettingsHooker
import com.lioisme.modev.hook.hooks.SystemPropertiesHooker
import com.lioisme.modev.hook.hooks.XposedClassHooker
import com.lioisme.modev.hook.hooks.XposedMapsHooker
import com.lioisme.modev.hook.hooks.XposedStackHooker

class DeviceEnvProbe : Probe, InterventionProbeMarker {

    override val recordType: String = RecordTypes.DEVICE_ENV

    /**
     * V44 T2.4 冷启动裁剪:设备环境域(含 Root·Xposed·模拟器伪装与检测监控等)
     * 全部经 `deviceEnvListen` master ∧ kind 双门控——master 关 = 零能力可生效。
     */
    override fun enabledIn(switch: HookSwitch): Boolean = switch.deviceEnvListen

    override fun install(context: ProbeContext) {
        // V44 T2.1:proc 扫描单一广播协调器先装(FileReader/BufferedReader/readLine
        // 各注册一次,master 门控先行);六个 proc Hooker 仅 addHandler 登记行处理逻辑。
        val procScan = ProcScanBroadcaster(context)
        procScan.install()
        val hookers: List<DeviceEnvBaseHooker> =
            listOf(
                RootFileHooker(context),
                RootExecHooker(context),
                RootPackageManagerHooker(context),
                RootPropertyHooker(context),
                XposedClassHooker(context),
                XposedStackHooker(context),
                XposedMapsHooker(context, procScan),
                EmulatorBuildHooker(context),
                EmulatorFileHooker(context),
                EmulatorSensorHooker(context),
                EmulatorNetworkHooker(context),
                DeviceTelephonyHooker(context),
                DeviceCpuHooker(context),
                DeviceSysPropHooker(context),
                DeviceBatteryHooker(context),
                // V35 momo 增强
                SettingsHooker(context),
                SystemPropertiesHooker(context),
                ProcStatusHooker(context, procScan),
                ProcMountInfoHooker(context, procScan),
                ProcCgroupHooker(context, procScan),
                ProcNetHooker(context, procScan),
                FridaThreadHooker(context),
                DetectionMonitorHooker(context, procScan),
            )
        for (hooker in hookers) {
            hooker.install()
        }
    }
}
