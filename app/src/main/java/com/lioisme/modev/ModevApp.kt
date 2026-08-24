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
import com.lioisme.modev.data.repo.LogRepository
import com.lioisme.modev.data.repo.MirroredConfigWriter
import com.lioisme.modev.data.repo.SettingsRepository
import com.lioisme.modev.data.server.ConfigServer
import com.lioisme.modev.domain.capability.CapabilityRegistry
import com.lioisme.modev.domain.capability.AlgorithmListenCapability
import com.lioisme.modev.domain.capability.DatabaseListenCapability
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
        ).also { server.effectiveResolver = { pkg -> it.effectiveSwitch(pkg) } }
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
     * 监听能力注册表(V10 能力化架构):设置页「功能」区与能力设置路由的驱动源。
     * 新增能力 = 实现 [Capability] + 在此注册,不改动导航/设置/通道主链路。
     */
    val capabilityRegistry: CapabilityRegistry by lazy {
        CapabilityRegistry().apply {
            register(AlgorithmListenCapability(settings.globalSwitch, appScope))
            register(DatabaseListenCapability(settings.globalSwitch, appScope))
        }
    }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            catalog.refresh()
        }
    }

    init {
        // 进程启动即接线记录回调(并启动服务器),避免 UI 未访问日志页时记录被丢弃
        logs.start()
        // 进程启动即初始化设置仓库:effectiveResolver(握手后立即按包推送有效配置)与
        // capabilityRegistry 依赖 settings 惰性实例,若仅等 UI 打开设置页才初始化,
        // 注入进程握手的配置推送将永远缺席(客户端 60s 读超时 → 无限重连)。
        settings
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