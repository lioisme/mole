package com.lioisme.modev.data.repo

import kotlinx.coroutines.flow.StateFlow

/**
 * 应用目录数据源(V16 R4 接口化):仓库契约,供 ViewModel 依赖注入与测试替身。
 * 实现者负责:全量应用列表、加载态、按需启动入口解析(惰性 + 缓存)。
 */
interface AppCatalogSource {

    /** 全量已安装应用(仅元数据;作用域/连接/计数由上层合并)。 */
    val apps: StateFlow<List<AppCatalogRepository.AppInfo>>

    /** 扫描进行中(首帧 progress 依据;并发刷新由实现方串行化)。 */
    val loading: StateFlow<Boolean>

    /** 重新扫描已安装应用(启动 / 包变化广播 / 下拉刷新)。 */
    fun refresh()

    /**
     * 按需解析某包是否存在可启动入口(getLaunchIntentForPackage,单次 binder 调用),
     * 结果按包缓存(列表扫描期间绝不逐包调用)。解析失败按无入口处理。
     */
    suspend fun resolveLaunchable(packageName: String): Boolean
}