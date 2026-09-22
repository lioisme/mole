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
import com.lioisme.modev.data.repo.CarrierCatalogRepository
import com.lioisme.modev.data.repo.DeviceCatalogRepository
import com.lioisme.modev.data.repo.KeywordLibraryRepository
import com.lioisme.modev.data.repo.SpoofProfileRepository
import com.lioisme.modev.data.repo.LogRepository
import com.lioisme.modev.data.repo.MirroredConfigWriter
import com.lioisme.modev.data.repo.SettingsRepository
import com.lioisme.modev.data.server.ConfigServer
import com.lioisme.modev.domain.app.AppCatalogService
import com.lioisme.modev.domain.app.AppListPreferences
import com.lioisme.modev.domain.asset.KeywordLibraryService
import com.lioisme.modev.domain.config.CarrierCatalogService
import com.lioisme.modev.domain.config.DeviceCatalogService
import com.lioisme.modev.domain.config.SpoofProfileService
import com.lioisme.modev.domain.config.FeatureService
import com.lioisme.modev.domain.config.ListenConfigService
import com.lioisme.modev.domain.record.RecordService
import com.lioisme.modev.domain.server.ConnectionMonitor
import com.lioisme.modev.domain.server.McpServiceConfig
import com.lioisme.modev.domain.server.ServerConfigService
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
 *
 * V45 T2.2(依赖倒置收口):**具体实现类型仅装配可见**(`internal` 段),对外一律
 * 暴露 `domain.*` 方法层契约(「方法层出口」段)——UI ViewModel 与 MCP 工具组持有的
 * 静态类型即接口,「这一屏只用什么」由类型系统表达,而非命名纪律。
 */
class AppContainer(private val context: Context) {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val database by lazy { ModevDatabase.get(context) }

    private val dao by lazy { database.cryptoRecordDao() }

    // ------------------------------------------------------------------
    // 实现层(仅装配期可见:内部装配需要仓库/服务端的实现成员)
    // ------------------------------------------------------------------

    /** wire 注入配置服务端:观测面对外经 [wireStatus] 契约。 */
    internal val wireServer: ConfigServer by lazy {
        ConfigServer().also { it.start() }
    }

    /** 已安装应用目录实现:对外经 [catalog] 契约。 */
    private val catalogRepo: AppCatalogRepository by lazy {
        AppCatalogRepository(context.packageManager, appScope)
    }

    internal val settingsRepo: SettingsRepository by lazy {
        SettingsRepository(
            server = wireServer,
            writer = MirroredConfigWriter(
                prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
                gson = Gson(),
                remotePrefs = { RemotePrefsBridge.prefs() },
            ),
            gson = Gson(),
            localPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
        ).also { wireServer.effectiveResolver = { pkg -> it.effectiveSwitch(pkg) } }
    }

    /** 全局弹窗拦截关键词库(V37 T2.1/T2.2):装配进设置仓库并监听变更重推。 */
    private val keywordLibraryRepo: KeywordLibraryRepository by lazy {
        KeywordLibraryRepository(database.keywordLibraryDao(), appScope).also { library ->
            settingsRepo.attachKeywordLibrary(library)
            appScope.launch {
                // 首帧空快照不触发重推;库条目任一变化 → 全部在线会话按包重解析
                library.entries.drop(1).collect { settingsRepo.repushAllConnected() }
            }
        }
    }

    /** 设备画像库(V47 T2):装配进设置仓库作展开源,变更即全量重推,首装补内置三档。 */
    private val spoofProfileRepo: SpoofProfileRepository by lazy {
        SpoofProfileRepository(database.spoofProfileDao(), appScope).also { profiles ->
            settingsRepo.attachSpoofProfiles(profiles)
            appScope.launch {
                // 先补内置档再开始重推:否则「首装即绑定内置画像」会解析成 Missing。
                runCatching { profiles.synchronizeBuiltins() }
                runCatching {
                    // 首帧空快照不触发重推;画像任一变化 → 全部在线会话按包重解析
                    profiles.profiles.drop(1).collect { settingsRepo.repushAllConnected() }
                }
            }
        }
    }

    /** 记录摄入/查询仓库实现:对外经 [records] 契约。 */
    internal val logsRepo: LogRepository by lazy {
        LogRepository(
            dao = dao,
            server = wireServer,
            databaseFile = { context.getDatabasePath(ModevDatabase.NAME) },
            // V45 T1.7 / ST-8:大批量删除后的库文件治理出口(PRAGMA 恒经 ModevDatabase 单源)
            maintenance = { database.shrinkStorage() },
            onDbError = ModevDatabase::noteSqliteError,
        )
    }

    /**
     * 真机目录实现(随包 devices.json 的唯一读者):对外经 [deviceCatalog] 契约。
     * 懒建即够 —— 只有机型选择器打开时才读盘,启动路径零 IO。
     */
    private val deviceCatalogRepo: DeviceCatalogRepository by lazy {
        DeviceCatalogRepository(context.assets)
    }

    /** 运营商目录实现(carriers.json 唯一读者):与机型目录同一条懒建口径,启动路径零 IO。 */
    private val carrierCatalogRepo: CarrierCatalogRepository by lazy {
        CarrierCatalogRepository(context.assets)
    }

    // ------------------------------------------------------------------
    // 方法层出口(UI ViewModel 与 MCP 工具组的唯一依赖面)
    // ------------------------------------------------------------------

    /** 记录读写契约面(实现 = [logsRepo])。 */
    val records: RecordService get() = logsRepo

    /** 每应用配置偏差契约面(监听域 + 功能域共用同一存储)。 */
    val listenConfig: ListenConfigService get() = settingsRepo

    /** 全局功能资产契约面(画像 / 黑名单追加)。 */
    val featureConfig: FeatureService get() = settingsRepo

    /** wire 服务与统一外部访问契约面。 */
    val serverConfig: ServerConfigService get() = settingsRepo

    /** MCP 服务配置契约面(端口 / 启停 / 令牌)。 */
    val mcpConfig: McpServiceConfig get() = settingsRepo

    /** 应用目录契约面。 */
    val catalog: AppCatalogService get() = catalogRepo

    /** 关键词库契约面。 */
    val keywordLibrary: KeywordLibraryService get() = keywordLibraryRepo

    /** 画像库对外契约(V47 T2;public 类型必须来自 domain,G2 闸)。 */
    val spoofProfiles: SpoofProfileService get() = spoofProfileRepo

    /** 真机目录契约(品牌 → 机型;public 类型来自 domain,G2 闸,实现 = [deviceCatalogRepo])。 */
    val deviceCatalog: DeviceCatalogService get() = deviceCatalogRepo

    /** 运营商契约(PLMN / 名 / 国家码;public 类型来自 domain,G2 闸,实现 = [carrierCatalogRepo])。 */
    val carrierCatalog: CarrierCatalogService get() = carrierCatalogRepo

    /** 应用列表浏览偏好契约面(进程级单一事实源,init 于 ModevApp.onCreate)。 */
    val appListPrefs: AppListPreferences get() = AppListSettings

    /** wire 服务端观测契约面。 */
    val wireStatus: ConnectionMonitor get() = wireServer

    /** MCP 服务端观测契约面(绑定地址 / 绑定错误)。 */
    val mcpStatus: ConnectionMonitor get() = mcpServer

    /** 框架作用域网关:现代框架服务 → 领域抽象。
     *  map 参数为 Any?(见 FrameworkBridge),转型在非空分支内,legacy 安全。 */
    val scopeGateway: ScopeGateway = object : ScopeGateway {
        override fun serviceFlow(): Flow<ScopeService?> =
            FrameworkBridge.serviceFlow.map { service ->
                if (service == null) null else XposedScopeService(service as XposedService)
            }
    }

    val scopeRepository: ScopeRepository by lazy {
        ScopeRepository(scopeGateway, logger = { msg -> android.util.Log.i("ModevScope", msg) })
    }

    /**
     * MCP 服务器(V38):Agent 一等公民能力面,与 wire ConfigServer 独立生命周期。
     * 工具面数量/名单唯一事实源 = [com.lioisme.modev.mcp.tools.McpTools.create]
     * (冻结快照见 `McpToolParityTest`;消费方一律经 `McpServer.toolCount()` 派生,V45 T0.1 禁写死数字)。
     * 自引用快照 lambda 在请求期才解析(server_status 消费),装配期无递归。
     * 工具装配入参一律取方法层出口属性(V45 T2.2:工具组不见实现类型)。
     */
    internal val mcpServer: com.lioisme.modev.mcp.McpServer by lazy {
        val registry = com.lioisme.modev.mcp.tools.McpToolRegistry(
            com.lioisme.modev.mcp.tools.McpTools.create(
                context = context,
                logs = records,
                listenConfig = listenConfig,
                featureConfig = featureConfig,
                serverConfig = serverConfig,
                keywords = keywordLibrary,
                profiles = spoofProfiles,
                catalog = catalog,
                listPrefs = appListPrefs,
                scopeRepository = scopeRepository,
                connectionMonitor = wireStatus,
                mcpSnapshot = {
                    com.lioisme.modev.mcp.tools.McpJson.obj(
                        "enabled" to com.google.gson.JsonPrimitive(mcpConfig.mcpEnabled.value),
                        "port" to com.google.gson.JsonPrimitive(mcpServer.port),
                        "boundAddress" to com.google.gson.JsonPrimitive(mcpServer.boundAddress.value),
                        "externalAccess" to com.google.gson.JsonPrimitive(mcpServer.externalAccess),
                        "tokenAuth" to com.google.gson.JsonPrimitive(mcpServer.tokenAuthEnabled),
                        "tokenCount" to com.google.gson.JsonPrimitive(mcpConfig.mcpTokens.value.size),
                        "sessionCount" to com.google.gson.JsonPrimitive(mcpServer.sessionCount()),
                        "toolCount" to com.google.gson.JsonPrimitive(mcpServer.toolCount()),
                    )
                },
            ),
        )
        val built = com.lioisme.modev.mcp.McpServer(
            port = mcpConfig.mcpPort.value,
            externalAccess = serverConfig.serverExternalAccess.value,
            tokenAuthEnabled = mcpConfig.mcpTokenAuth.value,
            requiredTokens = mcpConfig.mcpTokens.value.map { it.value }.toSet(),
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
        settingsRepo.mcpRestarter = {
            if (mcpConfig.mcpEnabled.value) {
                built.port = mcpConfig.mcpPort.value
                built.externalAccess = serverConfig.serverExternalAccess.value
                built.tokenAuthEnabled = mcpConfig.mcpTokenAuth.value
                built.requiredTokens = mcpConfig.mcpTokens.value.map { it.value }.toSet()
                built.restart()
            } else {
                built.stop()
            }
        }
        // V39:令牌集合变更原地更新校验集(不重启 socket、不踢已连会话)
        settingsRepo.mcpTokenSetUpdater = { tokens -> built.requiredTokens = tokens }
        built
    }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            catalog.refresh()
        }
    }

    init {
        // 进程启动即接线记录回调(并启动服务器),避免 UI 未访问日志页时记录被丢弃
        logsRepo.start()
        // 进程启动即初始化设置仓库:effectiveResolver(握手后立即按包推送有效配置)
        // 依赖 settings 惰性实例,若仅等 UI 打开设置页才初始化,
        // 注入进程握手的配置推送将永远缺席(客户端 60s 读超时 → 无限重连)。
        settingsRepo
        // 关键词库惰性装配接线(库变更 → 全量重推)必须在进程启动即生效
        keywordLibraryRepo
        // V47 T2:画像库同上 —— 启动即补内置档并接线展开源,
        // 否则握手期推给注入进程的 deviceProfile 快照恒为空。
        spoofProfileRepo
        // V38:MCP 服务随进程常驻(默认启用回环;设置变更经 mcpRestarter 重启)
        if (mcpConfig.mcpEnabled.value) {
            appScope.launch { mcpServer.start() }
        }
        // M3-2: 框架服务就绪后生成并持久化握手令牌,ConfigServer 启用认证
        // serviceFlow 公开为 Any?(见 FrameworkBridge),转型发生在非空分支内,
        // legacy 变体(运行时缺 service 库)空值路径零类解析,自然降级不启用认证。
        appScope.launch {
            RemotePrefsBridge.serviceFlow.collect { service ->
                if (service == null) {
                    wireServer.requireToken = null
                } else {
                    val svc = service as XposedService
                    val prefs = runCatching { svc.getRemotePreferences(RemotePrefsBridge.PREFS_KEY) }.getOrNull()
                    if (prefs == null) {
                        wireServer.requireToken = null
                    } else {
                        var token = prefs.getString(RemotePrefsBridge.TOKEN_KEY, null)
                        if (token.isNullOrBlank()) {
                            token = UUID.randomUUID().toString().replace("-", "")
                            runCatching { prefs.edit().putString(RemotePrefsBridge.TOKEN_KEY, token).commit() }
                        }
                        wireServer.requireToken = token
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