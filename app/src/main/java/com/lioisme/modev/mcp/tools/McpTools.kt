package com.lioisme.modev.mcp.tools

import android.content.Context
import com.google.gson.JsonObject
import com.lioisme.modev.data.repo.AppCatalogRepository
import com.lioisme.modev.data.repo.KeywordLibraryRepository
import com.lioisme.modev.data.repo.LogRepository
import com.lioisme.modev.data.repo.SettingsRepository
import com.lioisme.modev.domain.server.ConnectionMonitor
import com.lioisme.modev.domain.scope.ScopeRepository

/**
 * 工具面装配工厂(V38):43 个工具的唯一组装点(AppContainer 惰性调用)。
 * [mcpSnapshot] 由 AppContainer 以自引用惰性 lambda 注入(server_status 消费,
 * 请求期调用时 McpServer 必已完成初始化)。
 */
object McpTools {

    const val SERVER_NAME = "modev"
    const val SERVER_INSTRUCTIONS =
        "Modev is an Android Xposed module that passively captures algorithm/database/behavior/" +
            "preference/webview/network/device-env/ui/system/process records of scoped target apps " +
            "and actively intervenes (exit intercept, crash prevention, screen-record unlock, dialog " +
            "keyword intercept, SSL bypass, proxy/VPN hiding, root/xposed/emulator spoofing). " +
            "Use taxonomy_listen / taxonomy_feature to discover valid kind ids before filtering " +
            "records or changing settings. Destructive tools require confirm=true."

    /** 组装全部 43 个工具(顺序稳定,parity 快照消费)。 */
    fun create(
        context: Context,
        logs: LogRepository,
        settings: SettingsRepository,
        keywords: KeywordLibraryRepository,
        catalog: AppCatalogRepository,
        scopeRepository: ScopeRepository,
        connectionMonitor: ConnectionMonitor,
        mcpSnapshot: () -> JsonObject,
    ): List<McpTool> = RecordTools(logs).tools +
        AppTools(catalog, logs, scopeRepository, connectionMonitor, AppTools.launcher(context)).tools +
        ListenConfigTools(settings).tools +
        FeatureConfigTools(settings).tools +
        GlobalAssetTools(keywords, settings).tools +
        ServerTools(settings, connectionMonitor, scopeRepository, pipelineStats = { logs.statsFlow.value }, mcpSnapshot = mcpSnapshot).tools +
        ScopeTools(scopeRepository).tools
}
