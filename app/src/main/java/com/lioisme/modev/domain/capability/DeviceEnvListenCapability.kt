package com.lioisme.modev.domain.capability

import com.lioisme.modev.core.model.HookSwitch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class DeviceEnvListenCapability(
    globalSwitch: StateFlow<HookSwitch>,
    scope: CoroutineScope,
) : Capability {
    override val kind = CapabilityKind.DEVICE_ENV_LISTEN
    override val enabled = globalSwitch
        .map { it.anyDeviceEnvEnabled }
        .stateIn(scope, SharingStarted.Eagerly, globalSwitch.value.anyDeviceEnvEnabled)
}
