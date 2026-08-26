package com.lioisme.modev.domain.capability

import com.lioisme.modev.core.model.HookSwitch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * 行为监听能力(V27):全局配置([HookSwitch])的领域投影。
 * 启用态 = 任一行为类别启用([HookSwitch.anyBehaviorEnabled]),与算法/数据库监听并列。
 */
class BehaviorListenCapability(
    globalSwitch: StateFlow<HookSwitch>,
    scope: CoroutineScope,
) : Capability {

    override val kind: CapabilityKind = CapabilityKind.BEHAVIOR_LISTEN

    override val enabled: StateFlow<Boolean> =
        globalSwitch
            .map { it.anyBehaviorEnabled }
            .stateIn(scope, SharingStarted.Eagerly, globalSwitch.value.anyBehaviorEnabled)
}
