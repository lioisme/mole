package com.lioisme.modev.domain.capability

import com.lioisme.modev.core.model.HookSwitch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * 算法捕获能力:全局捕获配置([HookSwitch])的领域投影。
 * 启用态 = 任一算法类别开启([HookSwitch.anyEnabled])。
 */
class CryptoCaptureCapability(
    globalSwitch: StateFlow<HookSwitch>,
    scope: CoroutineScope,
) : Capability {

    override val kind: CapabilityKind = CapabilityKind.CRYPTO_CAPTURE

    override val enabled: StateFlow<Boolean> =
        globalSwitch
            .map { it.anyEnabled }
            .stateIn(scope, SharingStarted.Eagerly, globalSwitch.value.anyEnabled)
}
