package com.lioisme.modev.mcp.tools

import android.content.Context
import com.google.gson.JsonObject
import com.lioisme.modev.domain.app.AppCatalogService
import com.lioisme.modev.domain.app.AppListPreferences
import com.lioisme.modev.domain.asset.KeywordLibraryService
import com.lioisme.modev.domain.config.FeatureService
import com.lioisme.modev.domain.config.ListenConfigService
import com.lioisme.modev.domain.config.SpoofProfileService
import com.lioisme.modev.domain.record.RecordService
import com.lioisme.modev.domain.server.ServerConfigService
import com.lioisme.modev.domain.server.ConnectionMonitor
import com.lioisme.modev.domain.scope.ScopeRepository

/**
 * 工具面装配工厂(V38):全部工具的唯一组装点(AppContainer 惰性调用)。
 * 依赖面 = 方法层契约集合(V45 T2.2:零 `data.*` 类型,与 UI ViewModel 同一上游);
 * 工具数量/名单唯一事实源 = 本工厂产出的 registry(冻结快照见 `McpToolParityTest`)。
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
            "records or changing settings. Use spoof_profile_catalog to discover device-profile field " +
            "keys before spoof_profile_save. Destructive tools require confirm=true."

    /** 组装全部工具(顺序稳定,parity 快照消费)。 */
    fun create(
        context: Context,
        logs: RecordService,
        listenConfig: ListenConfigService,
        featureConfig: FeatureService,
        serverConfig: ServerConfigService,
        keywords: KeywordLibraryService,
        profiles: SpoofProfileService,
        catalog: AppCatalogService,
        listPrefs: AppListPreferences,
        scopeRepository: ScopeRepository,
        connectionMonitor: ConnectionMonitor,
        mcpSnapshot: () -> JsonObject,
    ): List<McpTool> = RecordTools(logs).tools +
        AppTools(catalog, logs, listPrefs, scopeRepository, connectionMonitor, AppTools.launcher(context)).tools +
        ListenConfigTools(listenConfig).tools +
        FeatureConfigTools(listenConfig).tools +
        GlobalAssetTools(keywords, featureConfig, profiles).tools +
        SpoofProfileTools(profiles, listenConfig).tools +
        ServerTools(serverConfig, connectionMonitor, scopeRepository, pipelineStats = { logs.stats.value }, mcpSnapshot = mcpSnapshot).tools +
        ScopeTools(scopeRepository).tools
}
