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
 * 算法监听页(V10 全局化;V14 R5 更名自「捕获设置」):全局算法监听开关 +
 * 记录选项(最大字节 / 忽略堆栈)。
 * 单一事实源 = SettingsRepository.globalSwitch(镜像 switch_global,
 * 读优先已注入作用域客户端,无则回退本地,变更广播全量客户端)。
 * V11 M4:依赖收窄为 [SettingsRepository],不再持有整个容器。
 */
class AlgorithmMonitorViewModel(
    private val settings: SettingsRepository,
) : ViewModel() {

    val switchState: StateFlow<HookSwitch> = settings.globalSwitch
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HookSwitch.DEFAULT)

    fun setSwitch(transform: (HookSwitch) -> HookSwitch) {
        settings.set(transform(switchState.value))
    }
}

class AlgorithmMonitorViewModelFactory(
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return AlgorithmMonitorViewModel(container.settings) as T
    }
}