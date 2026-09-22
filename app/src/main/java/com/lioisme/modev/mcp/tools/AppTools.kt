package com.lioisme.modev.mcp.tools

import android.content.Intent
import com.google.gson.JsonObject
import com.lioisme.modev.core.model.RecordType
import com.lioisme.modev.domain.app.AppCatalogService
import com.lioisme.modev.domain.app.AppInfo
import com.lioisme.modev.domain.app.AppListPreferences
import com.lioisme.modev.domain.app.AppSort
import com.lioisme.modev.domain.record.RecordService
import com.lioisme.modev.domain.server.ConnectionMonitor
import com.lioisme.modev.domain.scope.ScopeRepository
import com.lioisme.modev.domain.scope.ScopeState
import kotlinx.coroutines.flow.first

/**
 * 应用组工具(V45 T3.5 收口后 6 个):应用列表 / 目录刷新 / 监控总览 / 启动 / 列表偏好读与写。
 * 上游与 AppsViewModel/AppMonitorViewModel **完全同契约面**
 * (V45 T2.2:catalog/logs/listPrefs 一律 domain 接口,由类型强制而非纪律)。
 *
 * V45 T3.5 / DUAL-11:`app_clear_records` 已整体删除 —— 它与 `records_clear_package` 是
 * **同一上游方法** [RecordService.clearPackage] 的两次广告(双入口双测、工具面虚增)。
 * 「按包 / 按类型 / 全库」清空三元组留在记录组自洽,应用组只保留浏览与启动面;
 * 「会改数据的动作在 MCP 只出现一次」由 `UiMcpCapabilityParityTest` 锁死。
 */
class AppTools(
    private val catalog: AppCatalogService,
    private val logs: RecordService,
    private val listPrefs: AppListPreferences,
    private val scopeRepository: ScopeRepository,
    private val connectedPackages: ConnectionMonitor,
    private val launch: (String) -> Boolean,
) {

    val tools: List<McpTool> = listOf(
        appsList(), appsRefresh(), appMonitor(), appLaunch(), applistPrefsGet(), applistPrefsSet(),
    )

    /** 作用域包集(三处消费共用;状态未就绪 = 空集)。 */
    private fun scopedPackages(): Set<String> =
        (scopeRepository.state.value as? ScopeState.Ready)?.scope ?: emptySet()

    /** V44 Q5 能力补齐:UI 下拉刷新同上游(重扫已安装应用 + 失效模块识别缓存)。 */
    private fun appsRefresh() = schemaTool(
        name = "apps_refresh",
        description = "Force-refresh the installed-app catalog (rescan + module-recognition cache " +
            "invalidation, same upstream as the app-list pull-to-refresh). Returns the refreshed count.",
        schema = emptySchema(),
    ) { _ ->
        catalog.refresh()
        // V45 T3.4:`appCount`(计数)与 apps_list 的 `apps`(数组)不再同名异型
        McpJson.obj("appCount" to catalog.apps.value.size)
    }

    /** 应用行 DTO(V45 T3.3:单源构造,apps_list 每行恒 6 键)。 */
    private fun appJson(app: AppInfo, scoped: Boolean, recordCount: Long): JsonObject = McpJson.obj(
        "packageName" to app.packageName,
        "label" to app.label,
        "system" to app.system,
        "xposedModule" to app.isXposedModule,
        "scoped" to scoped,
        "recordCount" to recordCount,
    )

    private fun appsList() = schemaTool(
        name = "apps_list",
        description = "List installed apps with label, system/module flags, scoped state and total record " +
            "count. Query filters by substring of label or package name (case-insensitive). Result order " +
            "matches the UI app list: locale-collated name sort (+ scoped pinned when the persisted " +
            "preference is on). showSystem/showModules default to the persisted list preferences " +
            "(managed via applist_prefs_set) when omitted.",
        schema = objSchema {
            str("query", "Substring filter on label/package (case-insensitive)")
            bool("showSystem", "Include system apps (default = persisted preference)")
            bool("showModules", "Include Xposed module apps (default = persisted preference)")
        },
    ) { args ->
        val query = McpJson.str(args, "query")?.lowercase()
        // V44 Q3 双端同源:省略参数默认读持久偏好(与 UI 列表语境一致),显式传入可临时覆盖
        val showSystem = McpJson.bool(args, "showSystem") ?: listPrefs.showSystem.value
        val showModules = McpJson.bool(args, "showModules") ?: listPrefs.showModules.value
        val scoped = scopedPackages()
        val counts = logs.observeRecordCounts(null).first()
        val byName = AppSort.nameComparator<AppInfo>(label = { it.label }, key = { it.packageName })
        val apps = catalog.apps.value.filter { app ->
            val passQuery = query == null ||
                app.label.lowercase().contains(query) || app.packageName.lowercase().contains(query)
            val passSystem = showSystem || !app.system
            val passModule = showModules || !app.isXposedModule
            passQuery && passSystem && passModule
        }.sortedWith(byName)
        val sorted = if (listPrefs.pinScoped.value) AppSort.pinScoped(apps) { it.packageName in scoped } else apps
        McpJson.obj(
            "apps" to McpJson.arr(
                sorted.map { app -> appJson(app, app.packageName in scoped, counts[app.packageName] ?: 0L) },
            ),
        )
    }

    private fun appMonitor() = schemaTool(
        name = "app_monitor",
        description = "Per-app monitor overview: running (connected) state, scoped state and all ten " +
            "record-type counts (same data as the per-app monitor screen).",
        schema = objSchema(required = listOf("packageName")) {
            str("packageName", "Target package name")
        },
    ) { args ->
        val pkg = McpJson.str(args, "packageName") ?: throw McpTool.McpToolException("packageName is required")
        val connected = connectedPackages.connectedPackagesFlow.value
        // V45 ST-6:与监控页同一计数出口(一次 GROUP BY;旧形态逐类型 10 次 COUNT)
        val counts = logs.observeTypeCountsForPackage(pkg).first()
        val countsObj = JsonObject()
        for (type in RecordType.entries) {
            countsObj.addProperty(type.wire, counts[type.wire] ?: 0L)
        }
        McpJson.obj(
            "packageName" to pkg,
            "running" to (pkg in connected),
            "scoped" to (pkg in scopedPackages()),
            "counts" to countsObj,
        )
    }

    private fun appLaunch() = schemaTool(
        name = "app_launch",
        description = "Launch an app by package name via its launcher intent. On Android 10+ background " +
            "activity starts may be blocked by the system; launched=false then means the system rejected it.",
        schema = objSchema(required = listOf("packageName")) {
            str("packageName", "Target package name")
        },
    ) { args ->
        val pkg = McpJson.str(args, "packageName") ?: throw McpTool.McpToolException("packageName is required")
        McpJson.obj("launched" to launch(pkg))
    }

    private fun applistPrefsGet() = schemaTool(
        name = "applist_prefs_get",
        description = "Read persisted app-list preferences (pin scoped on top / show system / show modules). " +
            "Key names are identical to the AppListPreferences contract and the UI filter menu.",
        schema = emptySchema(),
    ) { _ -> prefsJson(listPrefs.snapshot()) }

    private fun applistPrefsSet() = schemaTool(
        name = "applist_prefs_set",
        description = "Write persisted app-list preferences (same upstream as the app list filter menu; " +
            "omitted keys keep their current value).",
        schema = objSchema {
            bool("pinScoped", "Pin scoped apps on top (optional)")
            bool("showSystem", "Show system apps (optional)")
            bool("showModules", "Show Xposed module apps (optional)")
        },
    ) { args ->
        McpJson.bool(args, "pinScoped")?.let { listPrefs.setPinScoped(it) }
        McpJson.bool(args, "showSystem")?.let { listPrefs.setShowSystem(it) }
        McpJson.bool(args, "showModules")?.let { listPrefs.setShowModules(it) }
        prefsJson(listPrefs.snapshot())
    }

    private fun prefsJson(view: AppListPreferences.View): JsonObject = McpJson.obj(
        "pinScoped" to view.pinScoped,
        "showSystem" to view.showSystem,
        "showModules" to view.showModules,
    )

    companion object {
        /** 默认启动实现(经 PackageManagerCompat 解析入口;UI 的 launch 行为同源)。 */
        fun launcher(context: android.content.Context): (String) -> Boolean = { pkg ->
            runCatching {
                val intent = context.packageManager.getLaunchIntentForPackage(pkg)
                if (intent == null) {
                    false
                } else {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    true
                }
            }.getOrDefault(false)
        }
    }
}
