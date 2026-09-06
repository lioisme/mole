package com.lioisme.modev.domain.diagnostics

import com.lioisme.modev.data.repo.PipelineStats
import com.lioisme.modev.domain.scope.ScopeState

/**
 * 诊断报告装配(V38 D2 解耦):原困在 `DiagnosticsScreen.copyDiagnostics` 的
 * 文案装配抽取为纯函数,诊断页「复制诊断信息」与 MCP `diagnostics` 工具双端同源。
 *
 * - 数据入参(framework/port/统计)+ 标签入参([Labels],UI 侧由资源解析注入,
 *   MCP 侧注入英文字面量)——本类零 Android 依赖,可 JVM 直测;
 * - 数值格式化经 [formatCount](UI 侧注入 locale 千分位,MCP 侧注入 plain);
 * - 行序与诊断页展示行严格同序(V21 契约:复制快照与页面行同源)。
 */
object DiagnosticsReport {

    /** 诊断文案标签集(资源驱动;调用方解析后注入)。 */
    data class Labels(
        val frameworkService: String,
        val framework: String,
        val connecting: String,
        val notConnected: String,
        val scopedCount: String,
        val scopedCountValue: String,
        val frameworkError: String,
        val unknownError: String,
        val port: String,
        val portValue: String,
        val portScopeAll: String,
        val portScopeLocal: String,
        val onlineTargets: String,
        val onlineTargetsValue: String,
        val statsReceived: String,
        val statsDropped: String,
        val statsWriteFailures: String,
        val statsTrimmed: String,
        val statsRejectedHellos: String,
        val statsDroppedRecords: String,
        val statsRejectedRecords: String,
    )

    /** 诊断快照数据(标签无关;UI 由 VM 状态快照,MCP 由同源服务快照)。 */
    data class Snapshot(
        val frameworkState: ScopeState,
        val displayAddress: String,
        val port: Int,
        val externalAccess: Boolean,
        val connectedCount: Int,
        val pipelineStats: PipelineStats,
    )

    /**
     * 装配诊断文本(行序 = 诊断页行序):
     * 框架态 → (服务错误行) → 端口行(带作用域后缀) → 在线目标 → 管道统计 7 行。
     */
    fun build(snapshot: Snapshot, labels: Labels, formatCount: (Long) -> String): List<String> {
        val lines = mutableListOf<String>()
        fun line(label: String, value: String) = lines.add("$label: $value")
        when (val framework = snapshot.frameworkState) {
            is ScopeState.Connecting ->
                line(labels.frameworkService, labels.connecting)
            is ScopeState.Ready -> {
                line(labels.framework, "${framework.frameworkName} ${framework.frameworkVersion}")
                line(labels.scopedCount, labels.scopedCountValue.format(framework.scope.size))
            }
            is ScopeState.Unavailable -> {
                line(labels.frameworkService, labels.notConnected)
                if (framework.reason == ScopeState.UnavailableReason.SERVICE_ERROR) {
                    lines.add(
                        framework.message?.let { labels.frameworkError.format(it) }
                            ?: labels.unknownError,
                    )
                }
            }
        }
        val scopeSuffix = if (snapshot.externalAccess) labels.portScopeAll else labels.portScopeLocal
        line(labels.port, labels.portValue.format(snapshot.displayAddress, snapshot.port) + " " + scopeSuffix)
        line(labels.onlineTargets, labels.onlineTargetsValue.format(snapshot.connectedCount))
        val stats = snapshot.pipelineStats
        line(labels.statsReceived, formatCount(stats.received))
        line(labels.statsDropped, formatCount(stats.dropped))
        line(labels.statsWriteFailures, formatCount(stats.writeFailures))
        line(labels.statsTrimmed, formatCount(stats.trimmed))
        line(labels.statsRejectedHellos, formatCount(stats.channel.rejectedHellos))
        line(labels.statsDroppedRecords, formatCount(stats.channel.droppedRecords))
        line(labels.statsRejectedRecords, formatCount(stats.channel.rejectedRecords))
        return lines
    }
}
