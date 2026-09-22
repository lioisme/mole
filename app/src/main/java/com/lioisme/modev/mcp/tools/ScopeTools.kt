package com.lioisme.modev.mcp.tools

import com.google.gson.JsonObject
import com.lioisme.modev.domain.scope.ScopeChangeResult
import com.lioisme.modev.domain.scope.ScopeRepository
import com.lioisme.modev.domain.scope.ScopeState

/**
 * 框架作用域状态 → JSON(V45 T3.3 / DUAL-6 单源):
 * [ScopeTools] 与 `ServerTools.server_status` 的 `framework` 段共用本函数,
 * 且**键集恒等**(state / frameworkName / frameworkVersion / scopedCount /
 * scopedPackages / reason / message)——不适用的键落 `null` 而非消失,
 * Agent 侧解析不必按 state 分支取键,也不再出现「一处有 scopedCount、一处没有」。
 */
internal fun scopeStateJson(state: ScopeState): JsonObject = McpJson.obj(
    "state" to when (state) {
        is ScopeState.Connecting -> "connecting"
        is ScopeState.Ready -> "ready"
        is ScopeState.Unavailable -> "unavailable"
    },
    "frameworkName" to (state as? ScopeState.Ready)?.frameworkName,
    "frameworkVersion" to (state as? ScopeState.Ready)?.frameworkVersion,
    "scopedCount" to ((state as? ScopeState.Ready)?.scope?.size ?: 0),
    "scopedPackages" to McpJson.arr((state as? ScopeState.Ready)?.scope?.sorted() ?: emptyList()),
    "reason" to (state as? ScopeState.Unavailable)?.reason?.name,
    "message" to (state as? ScopeState.Unavailable)?.message,
)

/**
 * 作用域组工具(V38,3 个):框架作用域状态读 / 显式加 / 显式移。
 * 上游与 AppsViewModel/AppMonitorViewModel 同源(ScopeRepository.toggle/remove)。
 * 异步语义:现代框架 requestScope 弹系统确认通知,`requested` ≠ 已生效;
 * 用户批准后状态经 ScopeRepository 自动同步。
 */
class ScopeTools(private val scopeRepository: ScopeRepository) {

    val tools: List<McpTool> = listOf(scopeState(), scopeAdd(), scopeRemove())

    private fun stateJson(): JsonObject = scopeStateJson(scopeRepository.state.value)

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
        return McpJson.obj("result" to text, "state" to stateJson())
    }

    private fun scopeState() = schemaTool(
        name = "scope_state",
        description = "Framework (LSPosed/Vector) scope state: scoped package list and availability. " +
            "Key set is identical across states (frameworkName/reason = null when not applicable).",
        schema = emptySchema(),
    ) { _ -> stateJson() }

    private fun scopeAdd() = schemaTool(
        name = "scope_add",
        description = "Add an app to the module scope. result=requested means a system confirmation " +
            "notification was posted and user approval is pending; re-check scope_state afterwards.",
        schema = objSchema(required = listOf("packageName")) {
            str("packageName", "Target package name")
        },
    ) { args ->
        val pkg = McpJson.str(args, "packageName") ?: throw McpTool.McpToolException("packageName is required")
        val scoped = (scopeRepository.state.value as? ScopeState.Ready)?.scope ?: emptySet()
        if (pkg in scoped) {
            McpJson.obj("result" to "already_scoped", "state" to stateJson())
        } else {
            resultJson(kotlinx.coroutines.runBlocking { scopeRepository.toggle(pkg) })
        }
    }

    private fun scopeRemove() = schemaTool(
        name = "scope_remove",
        description = "Remove an app from the module scope (takes effect directly via framework).",
        schema = objSchema(required = listOf("packageName")) {
            str("packageName", "Target package name")
        },
    ) { args ->
        val pkg = McpJson.str(args, "packageName") ?: throw McpTool.McpToolException("packageName is required")
        val scoped = (scopeRepository.state.value as? ScopeState.Ready)?.scope ?: emptySet()
        if (pkg !in scoped) {
            McpJson.obj("result" to "not_scoped", "state" to stateJson())
        } else {
            resultJson(kotlinx.coroutines.runBlocking { scopeRepository.remove(pkg) })
        }
    }
}
