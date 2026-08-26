package com.lioisme.modev.domain.capability

import com.lioisme.modev.core.model.HookSwitch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * 偏好监听能力(V28):全局配置([HookSwitch])的领域投影。
 * 启用态 = 任一偏好类别启用([HookSwitch.anyPrefsEnabled]),与算法/数据库/行为监听并列。
 */
class PrefsListenCapability(
    globalSwitch: StateFlow<HookSwitch>,
    scope: CoroutineScope,
) : Capability {

    override val kind: CapabilityKind = CapabilityKind.PREFS_LISTEN

    override val enabled: StateFlow<Boolean> =
        globalSwitch
            .map { it.anyPrefsEnabled }
            .stateIn(scope, SharingStarted.Eagerly, globalSwitch.value.anyPrefsEnabled)
}
