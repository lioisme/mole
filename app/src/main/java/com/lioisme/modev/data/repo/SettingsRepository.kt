package com.lioisme.modev.data.repo

import android.content.SharedPreferences
import com.google.gson.Gson
import com.lioisme.modev.core.model.HookSwitch
import com.lioisme.modev.data.server.ConfigServer
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 按包名的捕获配置持久化出口。
 *
 * 现代变体双写:RemotePreferences(框架 DB,单一事实源,注入侧直读)+ SharedPreferences 兜底缓存;
 * legacy 变体仅本地([remotePrefs] 恒为 null,行为与重构前一致)。
 */
interface ConfigWriter {
    fun set(packageName: String, switch: HookSwitch)
    fun get(packageName: String): HookSwitch?
    fun configuredPackages(): Set<String>
}

/**
 * 本地 SharedPreferences + 现代 RemotePreferences 镜像双写:
 * 读优先远程(与注入侧所见一致),远程不可用(legacy/服务未绑定)时回退本地。
 */
class MirroredConfigWriter(
    private val prefs: SharedPreferences,
    private val gson: Gson,
    private val remotePrefs: () -> SharedPreferences?,
) : ConfigWriter {

    override fun set(packageName: String, switch: HookSwitch) {
        val json = gson.toJson(switch)
        prefs.edit().putString(key(packageName), json).apply()
        // 远程写失败(legacy/服务未绑定)静默,本地已兜底
        runCatching {
            remotePrefs()?.edit()?.putString(key(packageName), json)?.commit()
        }
    }

    override fun get(packageName: String): HookSwitch? {
        val fromRemote = runCatching { remotePrefs()?.getString(key(packageName), null) }.getOrNull()
            ?.let { parse(it) }
        if (fromRemote != null) return fromRemote
        return prefs.getString(key(packageName), null)?.let { parse(it) }
    }

    override fun configuredPackages(): Set<String> {
        val local = prefs.all.keys.filter { it.startsWith(PREFIX) }
            .map { it.removePrefix(PREFIX) }
        val remote = runCatching { remotePrefs()?.all?.keys }.getOrNull()
            ?.filter { it.startsWith(PREFIX) }
            ?.map { it.removePrefix(PREFIX) }
            .orEmpty()
        return (local + remote).toSet()
    }

    private fun parse(json: String): HookSwitch? =
        runCatching { gson.fromJson(json, HookSwitch::class.java) }.getOrNull()

    private fun key(packageName: String): String = PREFIX + packageName

    companion object {
        private const val PREFIX = "switch_"
    }
}

/**
 * 按包名的捕获配置仓库:ConfigWriter 持久化 + 内存 StateFlow +
 * 变更即推送 ConfigServer 广播(注入侧即时生效)。
 */
class SettingsRepository(
    private val server: ConfigServer,
    private val writer: ConfigWriter,
) {

    private val flows = ConcurrentHashMap<String, MutableStateFlow<HookSwitch>>()

    private val _configuredPackages = MutableStateFlow(loadConfigured())
    val configuredPackagesFlow: StateFlow<Set<String>> = _configuredPackages

    fun flowFor(packageName: String): StateFlow<HookSwitch> =
        flows.getOrPut(packageName) { MutableStateFlow(load(packageName)) }

    fun get(packageName: String): HookSwitch = flows[packageName]?.value ?: load(packageName)

    fun set(packageName: String, switch: HookSwitch) {
        writer.set(packageName, switch)
        flows.getOrPut(packageName) { MutableStateFlow(switch) }.value = switch
        _configuredPackages.value = _configuredPackages.value + packageName
        server.broadcastConfig(packageName, switch)
    }

    private fun load(packageName: String): HookSwitch =
        writer.get(packageName) ?: HookSwitch.DEFAULT

    private fun loadConfigured(): Set<String> = writer.configuredPackages()
}