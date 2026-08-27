package com.lioisme.modev.hook.probes

import com.lioisme.modev.core.model.RecordTypes
import com.lioisme.modev.hook.Probe
import com.lioisme.modev.hook.ProbeContext
import com.lioisme.modev.hook.hooks.DetectionMonitorHooker
import com.lioisme.modev.hook.hooks.DeviceEnvBaseHooker
import com.lioisme.modev.hook.hooks.EmulatorBuildHooker
import com.lioisme.modev.hook.hooks.EmulatorFileHooker
import com.lioisme.modev.hook.hooks.EmulatorNetworkHooker
import com.lioisme.modev.hook.hooks.EmulatorSensorHooker
import com.lioisme.modev.hook.hooks.RootExecHooker
import com.lioisme.modev.hook.hooks.RootFileHooker
import com.lioisme.modev.hook.hooks.RootPackageManagerHooker
import com.lioisme.modev.hook.hooks.RootPropertyHooker
import com.lioisme.modev.hook.hooks.XposedClassHooker
import com.lioisme.modev.hook.hooks.XposedMapsHooker
import com.lioisme.modev.hook.hooks.XposedStackHooker

class DeviceEnvProbe : Probe {

    override val recordType: String = RecordTypes.DEVICE_ENV

    override fun install(context: ProbeContext) {
        val hookers: List<DeviceEnvBaseHooker> =
            listOf(
                RootFileHooker(context),
                RootExecHooker(context),
                RootPackageManagerHooker(context),
                RootPropertyHooker(context),
                XposedClassHooker(context),
                XposedStackHooker(context),
                XposedMapsHooker(context),
                EmulatorBuildHooker(context),
                EmulatorFileHooker(context),
                EmulatorSensorHooker(context),
                EmulatorNetworkHooker(context),
                DetectionMonitorHooker(context),
            )
        for (hooker in hookers) {
            hooker.install()
        }
    }
}
