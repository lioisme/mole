package com.lioisme.modev.mcp.tools

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.google.gson.JsonObject
import com.lioisme.modev.core.model.RecordType
import com.lioisme.modev.data.repo.AppCatalogRepository
import com.lioisme.modev.data.repo.AppListSettings
import com.lioisme.modev.data.repo.LogRepository
import com.lioisme.modev.domain.server.ConnectionMonitor
import com.lioisme.modev.domain.scope.ScopeRepository
import com.lioisme.modev.domain.scope.ScopeState
import kotlinx.coroutines.flow.first

/**
 * 应用组工具(V38,6 个):应用列表/监控总览/启动/清空/列表偏好。
 * 上游与 AppsViewModel/AppMonitorViewModel 完全同源(R4)。
 */
class AppTools(
    private val catalog: AppCatalogRepository,
    private val logs: LogRepository,
    private val scopeRepository: ScopeRepository,
    private val connectedPackages: ConnectionMonitor,
    private val launch: (String) -> Boolean,
) {

    val tools: List<McpTool> = listOf(
        appsList(), appMonitor(), appLaunch(), appClearRecords(), applistPrefsGet(), applistPrefsSet(),
    )

    private fun appJson(packageName: String, label: String, system: Boolean, module: Boolean): JsonObject {
        val obj = JsonObject()
        obj.addProperty("packageName", packageName)
        obj.addProperty("label", label)
        obj.addProperty("system", system)
        obj.addProperty("xposedModule", module)
        return obj
    }

    private fun appsList() = schemaTool(
        name = "apps_list",
        description = "List installed apps with label, system/module flags, scoped state and total record " +
            "count. Query filters by substring of label or package name (case-insensitive). " +
            "The showSystem/showModules parameters here are query-level only; persisted list preferences " +
            "are managed via applist_prefs_set.",
        schema = {
            str("query", "Substring filter on label/package (case-insensitive)")
            bool("showSystem", "Include system apps (default true)")
            bool("showModules", "Include Xposed module apps (default false)")
        },
    ) { args ->
        val query = McpJson.str(args, "query")?.lowercase()
        val showSystem = McpJson.bool(args, "showSystem") ?: true
        val showModules = McpJson.bool(args, "showModules") ?: false
        val scoped = (scopeRepository.state.value as? ScopeState.Ready)?.scope ?: emptySet()
        val counts = logs.observeRecordCounts(null).first()
        val apps = catalog.apps.value.filter { app ->
            val passQuery = query == null ||
                app.label.lowercase().contains(query) || app.packageName.lowercase().contains(query)
            val passSystem = showSystem || !app.system
            val passModule = showModules || !app.isXposedModule
            passQuery && passSystem && passModule
        }
        val rows = apps.map { app ->
            appJson(app.packageName, app.label, app.system, app.isXposedModule).apply {
                addProperty("scoped", app.packageName in scoped)
                addProperty("recordCount", counts[app.packageName] ?: 0L)
            }
        }
        McpJson.obj("apps" to McpJson.arr(rows))
    }

    private fun appMonitor() = schemaTool(
        name = "app_monitor",
        description = "Per-app monitor overview: running (connected) state, scoped state and all ten " +
            "record-type counts (same data as the per-app monitor screen).",
        schema = { required(listOf("packageName")); str("packageName", "Target package name") },
    ) { args ->
        val pkg = McpJson.str(args, "packageName") ?: throw McpTool.McpToolException("packageName is required")
        val connected = connectedPackages.connectedPackagesFlow.value
        val scoped = (scopeRepository.state.value as? ScopeState.Ready)?.scope ?: emptySet()
        val types = listOf(
            RecordType.CRYPTO, RecordType.DATABASE, RecordType.BEHAVIOR, RecordType.PREFS,
            RecordType.WEBVIEW, RecordType.NETWORK, RecordType.DEVICE_ENV, RecordType.UI_INTERACTION,
            RecordType.SYSTEM, RecordType.PROCESS_CONTROL,
        )
        val countsObj = JsonObject()
        for (type in types) {
            countsObj.addProperty(type.wire, logs.observeRecordCounts(type.wire).first()[pkg] ?: 0L)
        }
        McpJson.obj(
            "packageName" to com.google.gson.JsonPrimitive(pkg),
            "running" to com.google.gson.JsonPrimitive(pkg in connected),
            "scoped" to com.google.gson.JsonPrimitive(pkg in scoped),
            "counts" to countsObj,
        )
    }

    private fun appLaunch() = schemaTool(
        name = "app_launch",
        description = "Launch an app by package name via its launcher intent. On Android 10+ background " +
            "activity starts may be blocked by the system; launched=false then means the system rejected it.",
        schema = { required(listOf("packageName")); str("packageName", "Target package name") },
    ) { args ->
        val pkg = McpJson.str(args, "packageName") ?: throw McpTool.McpToolException("packageName is required")
        val ok = launch(pkg)
        McpJson.obj("launched" to com.google.gson.JsonPrimitive(ok))
    }

    private fun appClearRecords() = schemaTool(
        name = "app_clear_records",
        description = "DANGER: clear all records of one package. Requires confirm=true.",
        schema = {
            required(listOf("packageName", "confirm"))
            str("packageName", "Target package name")
            bool("confirm", "Must be true to execute this destructive action")
        },
    ) { args ->
        val pkg = McpJson.str(args, "packageName") ?: throw McpTool.McpToolException("packageName is required")
        if (McpJson.bool(args, "confirm") != true) {
            throw McpTool.McpToolException("confirm=true is required for destructive clear")
        }
        logs.clearPackage(pkg)
        McpJson.obj("ok" to com.google.gson.JsonPrimitive(true))
    }

    private fun applistPrefsGet() = schemaTool(
        name = "applist_prefs_get",
        description = "Read persisted app-list preferences (pin scoped on top / show system / show modules).",
        schema = emptySchema(),
    ) { _ ->
        McpJson.obj(
            "pinScopedOnTop" to com.google.gson.JsonPrimitive(AppListSettings.pinScoped.value),
            "showSystem" to com.google.gson.JsonPrimitive(AppListSettings.showSystem.value),
            "showXposedModules" to com.google.gson.JsonPrimitive(AppListSettings.showModules.value),
        )
    }

    private fun applistPrefsSet() = schemaTool(
        name = "applist_prefs_set",
        description = "Write persisted app-list preferences (same write path as the app list filter menu).",
        schema = {
            bool("pinScopedOnTop", "Pin scoped apps on top (optional)")
            bool("showSystem", "Show system apps (optional)")
            bool("showXposedModules", "Show Xposed module apps (optional)")
        },
    ) { args ->
        McpJson.bool(args, "pinScopedOnTop")?.let { AppListSettings.setPinScoped(it) }
        McpJson.bool(args, "showSystem")?.let { AppListSettings.setShowSystem(it) }
        McpJson.bool(args, "showXposedModules")?.let { AppListSettings.setShowModules(it) }
        McpJson.obj(
            "pinScopedOnTop" to com.google.gson.JsonPrimitive(AppListSettings.pinScoped.value),
            "showSystem" to com.google.gson.JsonPrimitive(AppListSettings.showSystem.value),
            "showXposedModules" to com.google.gson.JsonPrimitive(AppListSettings.showModules.value),
        )
    }

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
