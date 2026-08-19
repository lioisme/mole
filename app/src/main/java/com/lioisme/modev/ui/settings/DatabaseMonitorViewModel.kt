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
 * 数据库监听页(V17 T12):5 类别开关 + 结果集双闸门(行数 / 字节)。
 * 单一事实源 = SettingsRepository.globalSwitch(镜像 switch_global,
 * 读优先已注入作用域客户端,无则回退本地,变更广播全量客户端)。
 * 开关即时生效链路与算法监听共用(注入侧 enabled() 快照)。
 */
class DatabaseMonitorViewModel(
    private val settings: SettingsRepository,
) : ViewModel() {

    val switchState: StateFlow<HookSwitch> = settings.globalSwitch
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HookSwitch.DEFAULT)

    fun setSwitch(transform: (HookSwitch) -> HookSwitch) {
        settings.set(transform(switchState.value))
    }
}

class DatabaseMonitorViewModelFactory(
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return DatabaseMonitorViewModel(container.settings) as T
    }
}