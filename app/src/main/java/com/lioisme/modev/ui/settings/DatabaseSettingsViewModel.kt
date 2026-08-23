package com.lioisme.modev.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lioisme.modev.AppContainer
import com.lioisme.modev.core.model.HookSwitch
import com.lioisme.modev.data.repo.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * 数据库监听设置页(V21 T3,自合并页拆分还原):总开关 + 5 类别 + 结果闸门。
 * 单一事实源 = SettingsRepository.globalSwitch(镜像 switch_global,
 * 读优先已注入作用域客户端,无则回退本地,变更广播全量客户端)。
 */
class DatabaseSettingsViewModel(
    private val settings: SettingsRepository,
) : ViewModel() {

    val switchState: StateFlow<HookSwitch> = settings.globalSwitch
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HookSwitch.DEFAULT)

    fun setSwitch(transform: (HookSwitch) -> HookSwitch) {
        settings.set(transform(switchState.value))
    }
}

class DatabaseSettingsViewModelFactory(
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return DatabaseSettingsViewModel(container.settings) as T
    }
}