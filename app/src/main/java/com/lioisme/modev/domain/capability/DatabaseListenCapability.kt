package com.lioisme.modev.domain.capability

import com.lioisme.modev.core.model.HookSwitch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * 数据库监听能力(V17 数据库监听):全局监听配置([HookSwitch])的领域投影。
 * 启用态 = 任一数据库类别开启([HookSwitch.anyDbEnabled],与算法监听正交)。
 */
class DatabaseListenCapability(
    globalSwitch: StateFlow<HookSwitch>,
    scope: CoroutineScope,
) : Capability {

    override val kind: CapabilityKind = CapabilityKind.DATABASE_LISTEN

    override val enabled: StateFlow<Boolean> =
        globalSwitch
            .map { it.anyDbEnabled }
            .stateIn(scope, SharingStarted.Eagerly, globalSwitch.value.anyDbEnabled)
}