package com.lioisme.modev.domain.capability

import com.lioisme.modev.core.model.HookSwitch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * 算法监听能力(V14 R5 更名自 CryptoCaptureCapability):全局监听配置
 * ([HookSwitch])的领域投影。
 * 启用态 = 任一算法类别开启([HookSwitch.anyCryptoEnabled],与数据库监听正交)。
 */
class AlgorithmListenCapability(
    globalSwitch: StateFlow<HookSwitch>,
    scope: CoroutineScope,
) : Capability {

    override val kind: CapabilityKind = CapabilityKind.ALGORITHM_LISTEN

    override val enabled: StateFlow<Boolean> =
        globalSwitch
            .map { it.anyCryptoEnabled }
            .stateIn(scope, SharingStarted.Eagerly, globalSwitch.value.anyCryptoEnabled)
}