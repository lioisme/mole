package com.lioisme.modev.mcp.tools

import com.google.gson.JsonObject
import com.lioisme.modev.data.repo.PipelineStats
import com.lioisme.modev.data.repo.SettingsRepository
import com.lioisme.modev.domain.diagnostics.DiagnosticsReport
import com.lioisme.modev.domain.server.ConnectionMonitor
import com.lioisme.modev.domain.scope.ScopeRepository
import com.lioisme.modev.domain.scope.ScopeState

/**
 * 服务与诊断组工具(V38,4 个):wire 服务器状态/外部访问/端口 + 诊断报告。
 * 上游与 DiagnosticsViewModel 完全同源(ConnectionMonitor/SettingsRepository/statsFlow);
 * 诊断文本装配经 [DiagnosticsReport] 双端同源(D2)。
 */
class ServerTools(
    private val settings: SettingsRepository,
    private val connectionMonitor: ConnectionMonitor,
    private val scopeRepository: ScopeRepository,
    private val pipelineStats: () -> PipelineStats,
    private val mcpSnapshot: () -> JsonObject,
) {

    val tools: List<McpTool> = listOf(
        serverStatus(), serverSetExternalAccess(), serverSetPort(), diagnostics(),
    )

    private fun frameworkJson(): JsonObject {
        val obj = JsonObject()
        when (val state = scopeRepository.state.value) {
            is ScopeState.Connecting -> obj.addProperty("state", "connecting")
            is ScopeState.Ready -> {
                obj.addProperty("state", "ready")
                obj.addProperty("frameworkName", state.frameworkName)
                obj.addProperty("frameworkVersion", state.frameworkVersion)
                obj.addProperty("scopedCount", state.scope.size)
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

    private fun serverStatus() = schemaTool(
        name = "server_status",
        description = "Server overview: wire ConfigServer (port/bound address/external access/connected " +
            "packages), MCP server self state (port/bound/token auth/token count/sessions) and framework " +
            "scope state. Token values are never exposed.",
        schema = emptySchema(),
    ) { _ ->
        McpJson.obj(
            "wire" to McpJson.obj(
                "port" to com.google.gson.JsonPrimitive(settings.serverPort.value),
                "boundAddress" to McpJson.tree(connectionMonitor.boundAddress.value),
                "externalAccess" to com.google.gson.JsonPrimitive(settings.serverExternalAccess.value),
                "connectedPackages" to McpJson.tree(connectionMonitor.connectedPackagesFlow.value.sorted()),            ),
            "mcp" to mcpSnapshot(),
            "framework" to frameworkJson(),
        )
    }

    private fun serverSetExternalAccess() = schemaTool(
        name = "server_set_external_access",
        description = "Toggle external access for BOTH wire ConfigServer and MCP server (unified switch, " +
            "V41): false = bind 127.0.0.1 (default), true = bind 0.0.0.0 (same-segment devices can " +
            "connect; connected wire sessions recover via inject-side reconnect loop). MCP auth is " +
            "independent: Bearer is enforced only when token auth is enabled.",
        schema = { required(listOf("enabled")); bool("enabled", "true = bind all interfaces") },
    ) { args ->
        val enabled = McpJson.bool(args, "enabled") ?: throw McpTool.McpToolException("enabled is required")
        kotlinx.coroutines.runBlocking { settings.setExternalAccess(enabled) }
        McpJson.obj(
            "ok" to com.google.gson.JsonPrimitive(true),
            "externalAccess" to com.google.gson.JsonPrimitive(settings.serverExternalAccess.value),
        )
    }

    private fun serverSetPort() = schemaTool(
        name = "server_set_port",
        description = "Change wire ConfigServer listen port (1..65535; default 1511). Restart rebinds; " +
            "connected sessions recover via reconnect loop.",
        schema = { required(listOf("port")); int("port", "New wire port 1..65535") },
    ) { args ->
        val port = McpJson.int(args, "port") ?: throw McpTool.McpToolException("port is required")
        kotlinx.coroutines.runBlocking { settings.setServerPort(port) }
        McpJson.obj(
            "ok" to com.google.gson.JsonPrimitive(true),
            "port" to com.google.gson.JsonPrimitive(settings.serverPort.value),
        )
    }

    private fun diagnostics() = schemaTool(
        name = "diagnostics",
        description = "Structured capture-pipeline stats plus a plain-text diagnostic report " +
            "(identical content to the diagnostics screen copy action).",
        schema = emptySchema(),
    ) { _ ->
        val stats = pipelineStats()
        val mcp = mcpSnapshot()
        val snapshot = DiagnosticsReport.Snapshot(
            frameworkState = scopeRepository.state.value,
            displayAddress = connectionMonitor.boundAddress.value ?: ConnectionMonitor.BIND_LOOPBACK,
            port = settings.serverPort.value,
            externalAccess = settings.serverExternalAccess.value,
            connectedCount = connectionMonitor.connectedPackagesFlow.value.size,
            pipelineStats = stats,
            mcpEnabled = mcp.get("enabled")?.asBoolean ?: false,
            mcpPort = mcp.get("port")?.asInt ?: 0,
            mcpBoundAddress = mcp.get("boundAddress")?.takeIf { it.isJsonNull.not() }?.asString,
            mcpTokenAuth = mcp.get("tokenAuth")?.asBoolean ?: false,
        )
        val labels = DiagnosticsReport.Labels(
            frameworkService = "Framework service",
            framework = "Framework",
            connecting = "Connecting",
            notConnected = "Not connected",
            scopedCount = "Scoped apps",
            scopedCountValue = "%1\$d",
            frameworkError = "Error: %1\$s",
            unknownError = "Unknown error",
            port = "Listen",
            portValue = "%1\$s:%2\$d",
            portScopeAll = "(all interfaces)",
            portScopeLocal = "(local only)",
            onlineTargets = "Online targets",
            onlineTargetsValue = "%1\$d",
            mcpServer = "MCP server",
            mcpDisabled = "Disabled",
            mcpTokenAuth = "MCP token auth",
            stateOn = "On",
            stateOff = "Off",
            statsReceived = "Received",
            statsDropped = "Dropped",
            statsWriteFailures = "Write failures",
            statsTrimmed = "Trimmed",
            statsRejectedHellos = "Rejected hellos",
            statsDroppedRecords = "Dropped records",
            statsRejectedRecords = "Rejected records",
        )
        val lines = DiagnosticsReport.build(snapshot, labels) { it.toString() }
        McpJson.obj(
            "stats" to McpJson.tree(stats),
            "report" to McpJson.arr(lines.map { com.google.gson.JsonPrimitive(it) }),
        )
    }
}
