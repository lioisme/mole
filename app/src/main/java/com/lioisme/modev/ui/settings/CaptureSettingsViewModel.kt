package com.lioisme.modev.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lioisme.modev.AppContainer
import com.lioisme.modev.core.model.HookSwitch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * 捕获设置页(V10 全局化):全局算法捕获开关 + 记录选项(最大字节 / 忽略堆栈)。
 * 单一事实源 = SettingsRepository.globalSwitch(镜像 switch_global,
 * 读优先已注入作用域客户端,无则回退本地,变更广播全量客户端)。
 */
class CaptureSettingsViewModel(
    private val container: AppContainer,
) : ViewModel() {

    val switchState: StateFlow<HookSwitch> = container.settings.globalSwitch
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HookSwitch.DEFAULT)

    fun setSwitch(transform: (HookSwitch) -> HookSwitch) {
        container.settings.set(transform(switchState.value))
    }
}

class CaptureSettingsViewModelFactory(
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return CaptureSettingsViewModel(container) as T
    }
}
