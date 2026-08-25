package com.lioisme.modev.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.lioisme.modev.AppContainer
import com.lioisme.modev.data.repo.SettingsRepository

/**
 * 设置页(通用)V25:页面全部状态为本地组合态(外观/主题/语言 sheet),
 * 功能区仅剩静态入口(数据管理 / 服务与诊断),VM 无需承载任何流 ——
 * 监听配置入口已于 V25 移交每应用「监听设置」页。
 */
class SettingsViewModel(
    @Suppress("unused") private val settings: SettingsRepository,
) : ViewModel()

class SettingsViewModelFactory(
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return SettingsViewModel(container.settings) as T
    }
}
