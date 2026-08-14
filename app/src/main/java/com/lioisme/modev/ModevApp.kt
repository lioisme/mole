package com.lioisme.modev

import android.app.Application
import android.content.Context
import com.lioisme.modev.data.db.ModevDatabase
import com.lioisme.modev.data.repo.LogRepository
import com.lioisme.modev.data.repo.SettingsRepository
import com.lioisme.modev.data.server.ConfigServer

/**
 * 宿主 App 入口:持有手动 DI 容器(单例,进程级)。
 */
class ModevApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/**
 * 手动依赖容器:数据库/配置服务端/仓库按需惰性创建,生命周期随进程。
 */
class AppContainer(private val context: Context) {

    private val database by lazy { ModevDatabase.get(context) }

    private val dao by lazy { database.cryptoRecordDao() }

    val server: ConfigServer by lazy {
        ConfigServer().also { it.start() }
    }

    val settings by lazy {
        SettingsRepository(
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
            server = server,
        ).also { server.configProvider = { pkg -> it.get(pkg) } }
    }

    val logs: LogRepository by lazy {
        LogRepository(dao = dao, server = server)
    }

    init {
        // 进程启动即接线记录回调(并启动服务器),避免 UI 未访问日志页时记录被丢弃
        logs
    }

    companion object {
        private const val PREFS_NAME = "modev_settings"
    }
}