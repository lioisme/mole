package com.lioisme.modev.data.repo

import android.content.SharedPreferences
import com.google.gson.Gson
import com.lioisme.modev.core.model.HookSwitch
import com.lioisme.modev.data.server.ConfigServer
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 按包名的捕获配置仓库:SharedPreferences 持久化 + 内存 StateFlow +
 * 变更即推送 ConfigServer 广播(注入侧即时生效)。
 */
class SettingsRepository(
    private val prefs: SharedPreferences,
    private val server: ConfigServer,
) {

    private val gson = Gson()
    private val flows = ConcurrentHashMap<String, MutableStateFlow<HookSwitch>>()

    private val _configuredPackages = MutableStateFlow(loadConfigured())
    val configuredPackagesFlow: StateFlow<Set<String>> = _configuredPackages

    fun flowFor(packageName: String): StateFlow<HookSwitch> =
        flows.getOrPut(packageName) { MutableStateFlow(load(packageName)) }

    fun get(packageName: String): HookSwitch = flows[packageName]?.value ?: load(packageName)

    fun set(packageName: String, switch: HookSwitch) {
        prefs.edit().putString(key(packageName), gson.toJson(switch)).apply()
        flows.getOrPut(packageName) { MutableStateFlow(switch) }.value = switch
        _configuredPackages.value = _configuredPackages.value + packageName
        server.broadcastConfig(packageName, switch)
    }

    private fun load(packageName: String): HookSwitch =
        prefs.getString(key(packageName), null)
            ?.let { runCatching { gson.fromJson(it, HookSwitch::class.java) }.getOrNull() }
            ?: HookSwitch.DEFAULT

    private fun loadConfigured(): Set<String> =
        prefs.all.keys.filter { it.startsWith(PREFIX) }
            .map { it.removePrefix(PREFIX) }
            .toSet()

    private fun key(packageName: String): String = PREFIX + packageName

    companion object {
        private const val PREFIX = "switch_"
    }
}