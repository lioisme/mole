package com.lioisme.modev.domain.app

import kotlinx.coroutines.flow.StateFlow

/**
 * 已安装应用元数据(方法层模型;V45 ISO-2/ML-1:原嵌套于 `data.repo.AppCatalogRepository`
 * 的 `AppInfo` 上移本包 —— 消费方经接口取用,不再点名存储实现类型)。
 *
 * 仅含目录事实:作用域态 / 连接态 / 记录计数由消费方与
 * [com.lioisme.modev.domain.scope.ScopeRepository]、
 * [com.lioisme.modev.domain.record.RecordService] 合并,排序与置顶见
 * [com.lioisme.modev.domain.app.AppSort]。
 */
data class AppInfo(
    val packageName: String,
    val label: String,
    val system: Boolean,
    /** Xposed 模块(V17 R1 双通道识别;列表默认隐藏,偏好可显示)。 */
    val isXposedModule: Boolean = false,
)

/**
 * 应用目录契约(UI 应用列表与 MCP `apps_list`/`apps_refresh`/`app_launch` 唯一上游)。
 *
 * 实现方负责:全量扫描、加载态、按需启动入口解析(惰性 + 缓存,扫描期绝不逐包 binder)。
 */
interface AppCatalogService {

    /** 全量已安装应用(仅元数据)。 */
    val apps: StateFlow<List<AppInfo>>

    /** 扫描进行中(首帧 progress 依据;并发刷新由实现方串行化)。 */
    val loading: StateFlow<Boolean>

    /** 重新扫描(启动 / 包变化广播 / 下拉刷新 / MCP `apps_refresh`)。 */
    fun refresh()

    /** 某包是否存在可启动入口(单次 binder,按包缓存;解析失败按无入口)。 */
    suspend fun resolveLaunchable(packageName: String): Boolean
}

/**
 * 应用列表浏览偏好契约(置顶 / 系统应用 / Xposed 模块显隐)。
 *
 * AGENTS #7/#18 纪律:UI 零 import 持久化实现,变更一律经 ViewModel 下发到本契约;
 * MCP `applist_prefs_get/set` 与 UI 筛选菜单读写**同一** StateFlow 事实源,
 * 故「MCP 默认值 = 用户 UI 持久值」可被门禁断言。
 */
interface AppListPreferences {

    /** 已作用域应用置顶(默认开)。 */
    val pinScoped: StateFlow<Boolean>

    /** 显示系统应用。 */
    val showSystem: StateFlow<Boolean>

    /** 显示 Xposed 模块应用(默认隐藏,含 Modev 自身)。 */
    val showModules: StateFlow<Boolean>

    fun setPinScoped(value: Boolean)

    fun setShowSystem(value: Boolean)

    fun setShowModules(value: Boolean)

    /**
     * 三偏好快照名(避免三次 StateFlow 读取之间的撕裂)。
     * V45 T3.4 / DUAL-11:属性名与本契约的 Flow 名逐字一致
     * (旧 `pinScopedOnTop`/`showXposedModules` 是同一状态的第三套叫法,
     * MCP 键名跟着漂,对账要换算);UI/MCP/契约三名归一后不再需要映射表。
     */
    data class View(val pinScoped: Boolean, val showSystem: Boolean, val showModules: Boolean)

    fun snapshot(): View = View(pinScoped.value, showSystem.value, showModules.value)
}
