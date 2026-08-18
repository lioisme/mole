package com.lioisme.modev.data.repo

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 应用列表偏好持久化单一入口(2026-08-17 V7 对齐 ThemeSettingsManager 范式;
 * V11 L7 自 ui.apps 移入数据层——持久化基础设施不再停留于 UI 包;
 * V14 R7/R8:置顶默认开 + 筛选项持久化,ViewModel 零 Context 依赖):
 * StateFlow 承载内存态,SharedPreferences 持久化;init 须在进程启动
 * (ModevApp.onCreate)调用一次,此后各 setX 同步写双份。
 * 应用列表排序(名称 Collator / 已作用域置顶)与设置页共享本单一事实源。
 */
object AppListSettings {
    private const val PREF_NAME = "modev_applist"
    private const val KEY_PIN_SCOPED = "pin_scoped_on_top"
    private const val KEY_ONLY_SCOPED = "only_scoped"
    private const val KEY_SHOW_SYSTEM = "show_system"

    private val _pinScoped = MutableStateFlow(true)

    /** 已作用域应用置顶(默认开,V14 R7 通用设置单入口)。 */
    val pinScoped: StateFlow<Boolean> = _pinScoped.asStateFlow()

    private val _onlyScoped = MutableStateFlow(false)

    /** 仅显示已作用域应用(V14 R8 持久化)。 */
    val onlyScoped: StateFlow<Boolean> = _onlyScoped.asStateFlow()

    private val _showSystem = MutableStateFlow(true)

    /** 显示系统应用(V14 R8 持久化)。 */
    val showSystem: StateFlow<Boolean> = _showSystem.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        _pinScoped.value = prefs.getBoolean(KEY_PIN_SCOPED, true)
        _onlyScoped.value = prefs.getBoolean(KEY_ONLY_SCOPED, false)
        _showSystem.value = prefs.getBoolean(KEY_SHOW_SYSTEM, true)
    }

    fun setPinScoped(
        value: Boolean,
        context: Context,
    ) {
        persist(context, KEY_PIN_SCOPED, value)
        _pinScoped.value = value
    }

    fun setOnlyScoped(
        value: Boolean,
        context: Context,
    ) {
        persist(context, KEY_ONLY_SCOPED, value)
        _onlyScoped.value = value
    }

    fun setShowSystem(
        value: Boolean,
        context: Context,
    ) {
        persist(context, KEY_SHOW_SYSTEM, value)
        _showSystem.value = value
    }

    private fun persist(
        context: Context,
        key: String,
        value: Boolean,
    ) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(key, value)
            .apply()
    }
}