package com.lioisme.modev.domain.diagnostics

import com.lioisme.modev.data.repo.PipelineStats
import com.lioisme.modev.domain.scope.ScopeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 诊断报告装配(V38 D2 双端同源;V39 增补 MCP 两行)JVM 直测:
 * 行序 = 框架 → 端口 → 在线目标 → MCP 服务 → MCP 令牌认证 → 统计 7 行;
 * 令牌值恒不出现(PII 纪律)。
 */
class DiagnosticsReportTest {

    private val labels = DiagnosticsReport.Labels(
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
        portScopeAll = "(all)",
        portScopeLocal = "(local)",
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

    private fun snapshot(
        mcpEnabled: Boolean,
        mcpBound: String?,
        mcpTokenAuth: Boolean,
    ) = DiagnosticsReport.Snapshot(
        frameworkState = ScopeState.Connecting,
        displayAddress = "127.0.0.1",
        port = 1511,
        externalAccess = false,
        connectedCount = 0,
        pipelineStats = PipelineStats(),
        mcpEnabled = mcpEnabled,
        mcpPort = 1510,
        mcpBoundAddress = mcpBound,
        mcpTokenAuth = mcpTokenAuth,
    )

    @Test
    fun `mcp rows render bound address and auth state`() {
        val lines = DiagnosticsReport.build(
            snapshot(mcpEnabled = true, mcpBound = "127.0.0.1", mcpTokenAuth = true),
            labels,
        ) { it.toString() }

        assertTrue(lines.contains("MCP server: 127.0.0.1:1510"))
        assertTrue(lines.contains("MCP token auth: On"))
    }

    @Test
    fun `mcp disabled renders Disabled and unbound falls back loopback`() {
        val disabled = DiagnosticsReport.build(
            snapshot(mcpEnabled = false, mcpBound = null, mcpTokenAuth = false),
            labels,
        ) { it.toString() }
        assertTrue(disabled.contains("MCP server: Disabled"))
        assertTrue(disabled.contains("MCP token auth: Off"))

        val enabledUnbound = DiagnosticsReport.build(
            snapshot(mcpEnabled = true, mcpBound = null, mcpTokenAuth = false),
            labels,
        ) { it.toString() }
        assertTrue(enabledUnbound.contains("MCP server: 127.0.0.1:1510"))
    }

    @Test
    fun `line order is framework port targets mcp stats`() {
        val lines = DiagnosticsReport.build(
            snapshot(mcpEnabled = true, mcpBound = "127.0.0.1", mcpTokenAuth = false),
            labels,
        ) { it.toString() }
        val frameworkIdx = lines.indexOfFirst { it.startsWith("Framework service:") }
        val portIdx = lines.indexOfFirst { it.startsWith("Listen:") }
        val targetsIdx = lines.indexOfFirst { it.startsWith("Online targets:") }
        val mcpIdx = lines.indexOfFirst { it.startsWith("MCP server:") }
        val authIdx = lines.indexOfFirst { it.startsWith("MCP token auth:") }
        val statsIdx = lines.indexOfFirst { it.startsWith("Received:") }
        assertEquals(listOf(frameworkIdx, portIdx, targetsIdx, mcpIdx, authIdx, statsIdx),
            listOf(0, 1, 2, 3, 4, 5))
    }
}
