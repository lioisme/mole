package com.lioisme.modev.mcp.tools

import com.google.gson.JsonObject
import com.lioisme.modev.domain.scope.ScopeChangeResult
import com.lioisme.modev.domain.scope.ScopeRepository
import com.lioisme.modev.domain.scope.ScopeState

/**
 * 作用域组工具(V38,3 个):框架作用域状态读 / 显式加 / 显式移。
 * 上游与 AppsViewModel/AppMonitorViewModel 同源(ScopeRepository.toggle/remove)。
 * 异步语义:现代框架 requestScope 弹系统确认通知,`requested` ≠ 已生效;
 * 用户批准后状态经 ScopeRepository 自动同步。
 */
class ScopeTools(private val scopeRepository: ScopeRepository) {

    val tools: List<McpTool> = listOf(scopeState(), scopeAdd(), scopeRemove())

    private fun stateJson(): JsonObject {
        val obj = JsonObject()
        when (val state = scopeRepository.state.value) {
            is ScopeState.Connecting -> obj.addProperty("state", "connecting")
            is ScopeState.Ready -> {
                obj.addProperty("state", "ready")
                obj.addProperty("frameworkName", state.frameworkName)
                obj.addProperty("frameworkVersion", state.frameworkVersion)
                obj.add("scopedPackages", McpJson.tree(state.scope.sorted()))
            }
            is ScopeState.Unavailable -> {
                obj.addProperty("state", "unavailable")
                obj.addProperty("reason", state.reason.name)
                state.message?.let { obj.addProperty("message", it) }
            }
        }
        return obj
    }

    private fun resultJson(result: ScopeChangeResult): JsonObject {
        val text = when (result) {
            ScopeChangeResult.ScopeRequested -> "requested"
            ScopeChangeResult.ScopeRemoved -> "removed"
            ScopeChangeResult.Failed.FrameworkNotReady -> "framework_not_ready"
            ScopeChangeResult.Failed.ServiceNotConnected -> "service_not_connected"
            ScopeChangeResult.Failed.UserRejected -> "user_rejected"
            ScopeChangeResult.Failed.Timeout -> "timeout"
            ScopeChangeResult.Failed.RemoveFailed -> "remove_failed"
        }
        return McpJson.obj(
            "result" to com.google.gson.JsonPrimitive(text),
            "state" to stateJson(),
        )
    }

    private fun scopeState() = schemaTool(
        name = "scope_state",
        description = "Framework (LSPosed/Vector) scope state: scoped package list and availability.",
        schema = emptySchema(),
    ) { _ -> stateJson() }

    private fun scopeAdd() = schemaTool(
        name = "scope_add",
        description = "Add an app to the module scope. result=requested means a system confirmation " +
            "notification was posted and user approval is pending; re-check scope_state afterwards.",
        schema = { required(listOf("packageName")); str("packageName", "Target package name") },
    ) { args ->
        val pkg = McpJson.str(args, "packageName") ?: throw McpTool.McpToolException("packageName is required")
        val scoped = (scopeRepository.state.value as? ScopeState.Ready)?.scope ?: emptySet()
        if (pkg in scoped) {
            McpJson.obj("result" to com.google.gson.JsonPrimitive("already_scoped"), "state" to stateJson())
        } else {
            resultJson(kotlinx.coroutines.runBlocking { scopeRepository.toggle(pkg) })
        }
    }

    private fun scopeRemove() = schemaTool(
        name = "scope_remove",
        description = "Remove an app from the module scope (takes effect directly via framework).",
        schema = { required(listOf("packageName")); str("packageName", "Target package name") },
    ) { args ->
        val pkg = McpJson.str(args, "packageName") ?: throw McpTool.McpToolException("packageName is required")
        val scoped = (scopeRepository.state.value as? ScopeState.Ready)?.scope ?: emptySet()
        if (pkg !in scoped) {
            McpJson.obj("result" to com.google.gson.JsonPrimitive("not_scoped"), "state" to stateJson())
        } else {
            resultJson(kotlinx.coroutines.runBlocking { scopeRepository.remove(pkg) })
        }
    }
}
