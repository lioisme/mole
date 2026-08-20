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
 * 设置页(通用):仅承载「功能」区入口的启用态副标题。
 * 功能入口 = 监听设置(单一入口,副标题 = 算法/数据库启用态组合文案)/
 * 数据管理 / 服务与诊断(V19 T4 收敛,能力注册表 UI 呈现退场)。
 */
class SettingsViewModel(
    private val settings: SettingsRepository,
) : ViewModel() {

    /** 全局开关镜像:监听设置入口副标题的启用态单一事实源。 */
    val switchState: StateFlow<HookSwitch> = settings.globalSwitch
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HookSwitch.DEFAULT)
}

class SettingsViewModelFactory(
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return SettingsViewModel(container.settings) as T
    }
}