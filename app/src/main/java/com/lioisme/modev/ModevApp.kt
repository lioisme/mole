package com.lioisme.modev

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.lioisme.modev.data.db.ModevDatabase
import com.lioisme.modev.data.repo.AppCatalogRepository
import com.lioisme.modev.data.repo.KeywordLibraryRepository
import com.lioisme.modev.data.repo.LogRepository
import com.lioisme.modev.data.repo.MirroredConfigWriter
import com.lioisme.modev.data.repo.SettingsRepository
import com.lioisme.modev.data.server.ConfigServer
import com.lioisme.modev.domain.scope.ScopeGateway
import com.lioisme.modev.domain.scope.ScopeRepository
import com.lioisme.modev.domain.scope.ScopeService
import com.lioisme.modev.framework.FrameworkBridge
import com.lioisme.modev.framework.RemotePrefsBridge
import com.lioisme.modev.framework.XposedScopeService
import com.lioisme.modev.i18n.LocaleManager
import com.lioisme.modev.data.repo.AppListSettings
import com.lioisme.modev.ui.theme.ThemeSettingsManager
import io.github.libxposed.service.XposedService
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 宿主 App 入口:持有手动 DI 容器(单例,进程级)。
 */
class ModevApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // 主题配置先于任何 UI 读取持久化(进程级单一事实源)
        ThemeSettingsManager.init(this)
        // 应用列表偏好(排序/置顶开关)进程级单一事实源
        AppListSettings.init(this)
        // 应用语言(系统 per-app locale / App 内 preference 解析)
        LocaleManager.init(this)
        // 宿主进程注册框架服务监听(现代框架投递 XposedService,legacy 下自然降级)
        FrameworkBridge.ensureRegistered()
        // 远程偏好桥:宿主写配置镜像与令牌(legacy 下自然降级)
        RemotePrefsBridge.ensureRegistered()
        container = AppContainer(this)
    }
}

/**
 * 手动依赖容器:数据库/配置服务端/仓库按需惰性创建,生命周期随进程。
 */
class AppContainer(private val context: Context) {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val database by lazy { ModevDatabase.get(context) }

    private val dao by lazy { database.cryptoRecordDao() }

    val catalog: AppCatalogRepository by lazy {
        AppCatalogRepository(context.packageManager, appScope)
    }

    val server: ConfigServer by lazy {
        ConfigServer().also { it.start() }
    }

    val settings by lazy {
        SettingsRepository(
            server = server,
            writer = MirroredConfigWriter(
                prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
                gson = Gson(),
                remotePrefs = { RemotePrefsBridge.prefs() },
            ),
            gson = Gson(),
            localPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
        ).also { server.effectiveResolver = { pkg -> it.effectiveSwitch(pkg) } }
    }

    /** 全局弹窗拦截关键词库(V37 T2.1/T2.2):装配进设置仓库并监听变更重推。 */
    val keywordLibrary: KeywordLibraryRepository by lazy {
        KeywordLibraryRepository(database.keywordLibraryDao(), appScope).also { library ->
            settings.attachKeywordLibrary(library)
            appScope.launch {
                // 首帧空快照不触发重推;库条目任一变化 → 全部在线会话按包重解析
                library.entries.drop(1).collect { settings.repushAllConnected() }
            }
        }
    }

    val logs: LogRepository by lazy {
        LogRepository(
            dao = dao,
            server = server,
            databaseFile = { context.getDatabasePath(ModevDatabase.NAME) },
        )
    }

    /** 框架作用域网关:现代框架服务 → 领域抽象。
     *  map 参数为 Any?(见 FrameworkBridge),转型在非空分支内,legacy 安全。 */
    val scopeGateway: ScopeGateway = object : ScopeGateway {
        override fun serviceFlow(): Flow<ScopeService?> =
            FrameworkBridge.serviceFlow.map { service ->
                if (service == null) null else XposedScopeService(service as XposedService)
            }
    }

    val scopeRepository: ScopeRepository by lazy {
        ScopeRepository(scopeGateway)
    }

    /**
     * MCP 服务器(V38):Agent 一等公民能力面,43 工具,与 wire ConfigServer 独立生命周期。
     * 自引用快照 lambda 在请求期才解析(server_status 消费),装配期无递归。
     */
    val mcp: com.lioisme.modev.mcp.McpServer by lazy {
        val registry = com.lioisme.modev.mcp.tools.McpToolRegistry(
            com.lioisme.modev.mcp.tools.McpTools.create(
                context = context,
                logs = logs,
                settings = settings,
                keywords = keywordLibrary,
                catalog = catalog,
                scopeRepository = scopeRepository,
                connectionMonitor = server,
                mcpSnapshot = {
                    com.lioisme.modev.mcp.tools.McpJson.obj(
                        "enabled" to com.google.gson.JsonPrimitive(settings.mcpEnabled.value),
                        "port" to com.google.gson.JsonPrimitive(mcp.port),
                        "boundAddress" to com.google.gson.JsonPrimitive(mcp.boundAddress.value),
                        "externalAccess" to com.google.gson.JsonPrimitive(mcp.externalAccess),
                        "tokenAuth" to com.google.gson.JsonPrimitive(mcp.tokenAuthEnabled),
                        "tokenCount" to com.google.gson.JsonPrimitive(settings.mcpTokens.value.size),
                        "sessionCount" to com.google.gson.JsonPrimitive(mcp.sessionCount()),
                    )
                },
            ),
        )
        val built = com.lioisme.modev.mcp.McpServer(
            port = settings.mcpPort.value,
            externalAccess = settings.serverExternalAccess.value,
            tokenAuthEnabled = settings.mcpTokenAuth.value,
            requiredTokens = settings.mcpTokens.value.map { it.value }.toSet(),
            serverInfo = com.lioisme.modev.mcp.tools.McpJson.obj(
                "name" to com.google.gson.JsonPrimitive(com.lioisme.modev.mcp.tools.McpTools.SERVER_NAME),
                "version" to com.google.gson.JsonPrimitive(
                    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "0",
                ),
            ),
            instructions = com.lioisme.modev.mcp.tools.McpTools.SERVER_INSTRUCTIONS,
            registry = registry,
        )
        // 配置变更 → 重启(enabled=false 时停机;仓库侧已保证异步,见 restartMcpAsync)
        settings.mcpRestarter = {
            if (settings.mcpEnabled.value) {
                built.port = settings.mcpPort.value
                built.externalAccess = settings.serverExternalAccess.value
                built.tokenAuthEnabled = settings.mcpTokenAuth.value
                built.requiredTokens = settings.mcpTokens.value.map { it.value }.toSet()
                built.restart()
            } else {
                built.stop()
            }
        }
        // V39:令牌集合变更原地更新校验集(不重启 socket、不踢已连会话)
        settings.mcpTokenSetUpdater = { tokens -> built.requiredTokens = tokens }
        built
    }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            catalog.refresh()
        }
    }

    init {
        // 进程启动即接线记录回调(并启动服务器),避免 UI 未访问日志页时记录被丢弃
        logs.start()
        // 进程启动即初始化设置仓库:effectiveResolver(握手后立即按包推送有效配置)
        // 依赖 settings 惰性实例,若仅等 UI 打开设置页才初始化,
        // 注入进程握手的配置推送将永远缺席(客户端 60s 读超时 → 无限重连)。
        settings
        // V38:MCP 服务随进程常驻(默认启用回环;设置变更经 mcpRestarter 重启)
        if (settings.mcpEnabled.value) {
            appScope.launch { mcp.start() }
        }
        // M3-2: 框架服务就绪后生成并持久化握手令牌,ConfigServer 启用认证
        // serviceFlow 公开为 Any?(见 FrameworkBridge),转型发生在非空分支内,
        // legacy 变体(运行时缺 service 库)空值路径零类解析,自然降级不启用认证。
        appScope.launch {
            RemotePrefsBridge.serviceFlow.collect { service ->
                if (service == null) {
                    server.requireToken = null
                } else {
                    val svc = service as XposedService
                    val prefs = runCatching { svc.getRemotePreferences(RemotePrefsBridge.PREFS_KEY) }.getOrNull()
                    if (prefs == null) {
                        server.requireToken = null
                    } else {
                        var token = prefs.getString(RemotePrefsBridge.TOKEN_KEY, null)
                        if (token.isNullOrBlank()) {
                            token = UUID.randomUUID().toString().replace("-", "")
                            runCatching { prefs.edit().putString(RemotePrefsBridge.TOKEN_KEY, token).commit() }
                        }
                        server.requireToken = token
                    }
                }
            }
        }
        // 应用安装/卸载/变更时刷新应用目录
        ContextCompat.registerReceiver(
            context,
            packageReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addDataScheme("package")
            },
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    companion object {
        private const val PREFS_NAME = "modev_settings"
    }
}