package com.lioisme.modev.mcp.tools

import com.google.gson.JsonObject
import com.lioisme.modev.core.model.ConfigKeys
import com.lioisme.modev.domain.diagnostics.PipelineStats
import com.lioisme.modev.domain.diagnostics.DiagnosticsReport
import com.lioisme.modev.domain.server.ConnectionMonitor
import com.lioisme.modev.domain.server.PortRejectedException
import com.lioisme.modev.domain.server.PortValidator
import com.lioisme.modev.domain.server.ServerConfigService
import com.lioisme.modev.domain.scope.ScopeRepository
import com.lioisme.modev.domain.scope.ScopeState

/**
 * 服务与诊断组工具(V38,4 个):wire 服务器状态/外部访问/端口 + 诊断报告。
 * 上游与 DiagnosticsViewModel 完全同源(均持 [ServerConfigService]/[ConnectionMonitor]
 * 接口,V45 T2.2 收口;诊断文本装配经 [DiagnosticsReport] 双端同源 D2)。
 * 端口硬拒由方法层 [PortRejectedException] 承载(MCP 业务错误),与 UI 行内错误同判定
 * (V45 DUAL-3:不存在「UI 三闸硬拒、MCP 静默 clamp」的强度分叉)。
 */
class ServerTools(
    private val settings: ServerConfigService,
    private val connectionMonitor: ConnectionMonitor,
    private val scopeRepository: ScopeRepository,
    private val pipelineStats: () -> PipelineStats,
    private val mcpSnapshot: () -> JsonObject,
) {

    val tools: List<McpTool> = listOf(
        serverStatus(), serverSetExternalAccess(), serverSetPort(), diagnostics(),
    )

    private fun serverStatus() = schemaTool(
        name = "server_status",
        description = "Server overview: wire ConfigServer (port/bound address/external access/connected " +
            "packages), MCP server self state (port/bound/token auth/token count/sessions) and framework " +
            "scope state. Token values are never exposed.",
        schema = emptySchema(),
    ) { _ ->
        McpJson.obj(
            "wire" to McpJson.obj(
                "port" to settings.serverPort.value,
                "boundAddress" to connectionMonitor.boundAddress.value,
                "externalAccess" to settings.serverExternalAccess.value,
                "connectedPackages" to connectionMonitor.connectedPackagesFlow.value.sorted(),
            ),
            // MCP 自状态键集由 AppContainer 的 mcpSnapshot 单源承载(与 diagnostics 同源)
            "mcp" to mcpSnapshot(),
            "framework" to scopeStateJson(scopeRepository.state.value),
        )
    }

    private fun serverSetExternalAccess() = schemaTool(
        name = "server_set_external_access",
        description = "Toggle external access for BOTH wire ConfigServer and MCP server (unified switch, " +
            "V41): false = bind 127.0.0.1 (default), true = bind 0.0.0.0 (same-segment devices can " +
            "connect; connected wire sessions recover via inject-side reconnect loop). MCP auth is " +
            "independent: Bearer is enforced only when token auth is enabled.",
        schema = objSchema(required = listOf("enabled")) {
            bool("enabled", "true = bind all interfaces")
        },
    ) { args ->
        val enabled = McpJson.bool(args, "enabled") ?: throw McpTool.McpToolException("enabled is required")
        kotlinx.coroutines.runBlocking { settings.setExternalAccess(enabled) }
        McpJson.obj(
            "ok" to true,
            "externalAccess" to settings.serverExternalAccess.value,
        )
    }

    private fun serverSetPort() = schemaTool(
        name = "server_set_port",
        description = "Change wire ConfigServer listen port (1..65535; default " +
            ConfigKeys.WIRE_DEFAULT_PORT + "). Privileged (<1024), dual-service-conflicting and " +
            "occupied ports are refused (same three-gate verdict as the services screen).",
        schema = objSchema(required = listOf("port")) {
            int("port", "New wire port 1..65535")
        },
    ) { args ->
        val port = McpJson.int(args, "port") ?: throw McpTool.McpToolException("port is required")
        try {
            kotlinx.coroutines.runBlocking { settings.setServerPort(port) }
        } catch (e: PortRejectedException) {
            throw McpTool.McpToolException("port $port rejected: ${rejectReason(e.check)}")
        }
        McpJson.obj(
            "ok" to true,
            "port" to settings.serverPort.value,
        )
    }

    /** 端口拒绝分型 → Agent 可读英文原因(MCP 工具文本英文锁定,B4)。 */
    private fun rejectReason(check: PortValidator.PortCheck): String = when (check) {
        PortValidator.PortCheck.OutOfRange -> "out of range 1..65535"
        PortValidator.PortCheck.Privileged -> "privileged port (<1024) cannot be bound by an app process"
        is PortValidator.PortCheck.Conflict -> "already used by the ${check.otherLabel} server"
        PortValidator.PortCheck.InUse -> "already in use by another process"
        PortValidator.PortCheck.Available -> "available"
    }

    private fun diagnostics() = schemaTool(
        name = "diagnostics",
        description = "Structured capture-pipeline stats plus a plain-text diagnostic report " +
            "(identical data to the diagnostics screen copy action; labels English-locked for agents).",
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
            statsDropped = "Dropped (queue full)",
            statsBytesDropped = "Dropped (payload budget)",
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
