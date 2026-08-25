package com.lioisme.modev.data.repo

import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.gson.Gson
import com.lioisme.modev.core.model.AppListenOverride
import com.lioisme.modev.core.model.ConfigKeys
import com.lioisme.modev.core.model.EffectiveSwitch
import com.lioisme.modev.core.model.HookSwitch
import com.lioisme.modev.data.server.ConfigServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 全局捕获配置持久化出口(V10:由按包多份收敛为全局一份)。
 *
 * 现代变体双写:RemotePreferences(框架 DB,单一事实源,注入侧直读)+ SharedPreferences 兜底缓存;
 * legacy 变体仅本地([remotePrefs] 恒为 null,行为与重构前一致)。
 */
interface ConfigWriter {
    fun set(switch: HookSwitch)
    fun get(): HookSwitch?

    /**
     * 迁移 V9 及更早的按包配置(`switch_{pkg}`):取首份写入全局键并清理全部旧键。
     * 无旧键为 no-op;全局键已有值时跳过(保留用户当前配置)。
     */
    fun migrateLegacy()

    /**
     * 写入任意配置键(V23 泛化内核,单应用覆盖键使用):
     * 本地为宿主进程权威,远程为注入侧兜底直读的镜像。
     */
    fun put(key: String, value: String)

    /** 删除任意配置键(本地 + 远程镜像)。 */
    fun remove(key: String)

    /**
     * 读本地权威值。与 [get](全局键读优先远程)不同:
     * 覆盖键只由宿主进程写入,本地即单一事实源,读远程反而可能在
     * 远程写失败时读到陈旧值。
     */
    fun getLocal(key: String): String?

    /** 扫描本地前缀键(init 预载全部覆盖用)。 */
    fun localKeysWithPrefix(prefix: String): Set<String>
}

/**
 * 本地 SharedPreferences + 现代 RemotePreferences 镜像双写:
 * 读优先远程(与注入侧所见一致),远程不可用(legacy/服务未绑定)时回退本地。
 * 配置键全局唯一([KEY]),与包名解耦。
 */
class MirroredConfigWriter(
    private val prefs: SharedPreferences,
    private val gson: Gson,
    private val remotePrefs: () -> SharedPreferences?,
) : ConfigWriter {

    override fun set(switch: HookSwitch) {
        put(KEY, gson.toJson(switch))
    }

    override fun get(): HookSwitch? {
        val fromRemote = runCatching { remotePrefs()?.getString(KEY, null) }.getOrNull()
            ?.let { parse(it) }
        if (fromRemote != null) return fromRemote
        return prefs.getString(KEY, null)?.let { parse(it) }
    }

    override fun migrateLegacy() {
        val legacy = legacyKeys()
        if (legacy.isEmpty()) return
        // 已存在全局配置:跳过迁移(不覆盖用户当前设置),但旧键仍需清理
        if (get() == null) {
            val first = legacy.firstNotNullOfOrNull { key -> prefs.getString(key, null) }
            if (first != null) {
                put(KEY, first)
            }
        }
        // 无论是否取到旧值,清理全部旧键(本地 + 远程),消除逐应用残留
        legacy.forEach { key -> remove(key) }
    }

    override fun put(key: String, value: String) {
        prefs.edit { putString(key, value) }
        // 远程写失败(legacy/服务未绑定)静默,本地已兜底
        runCatching {
            remotePrefs()?.edit()?.putString(key, value)?.commit()
        }
    }

    override fun remove(key: String) {
        prefs.edit { remove(key) }
        runCatching {
            remotePrefs()?.edit()?.remove(key)?.commit()
        }
    }

    override fun getLocal(key: String): String? = prefs.getString(key, null)

    override fun localKeysWithPrefix(prefix: String): Set<String> =
        prefs.all.keys.filterTo(mutableSetOf()) { it.startsWith(prefix) }

    /** 旧版按包配置键(不含全局键),字典序确定(迁移取首唯一,跨设备一致)。 */
    private fun legacyKeys(): List<String> =
        prefs.all.keys.filter { it.startsWith(PREFIX) && it != KEY }.sorted()

    private fun parse(json: String): HookSwitch? = HookSwitch.fromJson(gson, json)

    companion object {
        /** 全局捕获配置键(V10):全应用共享一份,注入侧与宿主侧同键读写。 */
        const val KEY = ConfigKeys.GLOBAL_SWITCH

        private const val PREFIX = ConfigKeys.SWITCH_PREFIX
    }
}

/**
 * 捕获配置仓库:
 * - 默认配置(V25 收敛):内置最佳推荐值([HookSwitch.DEFAULT]),**无任何 UI 写入路径**;
 *   `switch_global` 键仅作注入侧兜底种子(init 强制重置,清除 V24 及更早的全局自定义遗留);
 * - 单应用监听自定义(V24 二态偏差 + V25 采集微调项):每包一键 `override_{pkg}`
 *   惰性创建,[EffectiveSwitch] 将默认配置 ⊕ 偏差解析为有效配置后经 ConfigServer
 *   定向推送对应会话,未定制应用恒等于内置默认值。
 */
class SettingsRepository(
    private val server: ConfigServer,
    private val writer: ConfigWriter,
    private val gson: Gson,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _globalSwitch = MutableStateFlow(HookSwitch.DEFAULT)

    /**
     * 默认配置流(V25:恒为内置推荐值;保留流形态供每应用页 combine 与
     * capability 消费,不再有任何写入方)。
     */
    val globalSwitch: StateFlow<HookSwitch> = _globalSwitch

    private val _overrides = MutableStateFlow<Map<String, AppListenOverride>>(emptyMap())

    /** 已存在的单应用自定义偏差(包名 → 偏差;仅含非零偏差项,init 时从本地预载)。 */
    val overrides: StateFlow<Map<String, AppListenOverride>> = _overrides

    /** 当前包的自定义偏差快照(未定制返回 [AppListenOverride.DEFAULT],调用方免空判)。 */
    fun overrideFor(packageName: String): AppListenOverride =
        _overrides.value[packageName] ?: AppListenOverride.DEFAULT

    /** 该包的有效配置快照(默认配置 ⊕ 偏差,纯解析)。 */
    fun effectiveSwitch(packageName: String): HookSwitch =
        EffectiveSwitch.resolve(_globalSwitch.value, _overrides.value[packageName])

    /** 该包的有效配置流:默认配置或偏差任一变化即重发射(distinct 由数据类 equals 保证)。 */
    fun effectiveSwitchFlow(packageName: String): Flow<HookSwitch> =
        combine(globalSwitch, overrides) { global, map ->
            EffectiveSwitch.resolve(global, map[packageName])
        }

    /**
     * 写入单应用自定义偏差:双写持久化 + 内存热更 + 向该包会话定向推送有效配置。
     * 零偏差([AppListenOverride.isDefault])等价删除存储键。
     */
    suspend fun setOverride(packageName: String, override: AppListenOverride) {
        if (override.isDefault) {
            removeOverride(packageName)
            return
        }
        withContext(Dispatchers.IO) {
            writer.put(ConfigKeys.overrideKey(packageName), override.toJson(gson))
        }
        _overrides.update { it + (packageName to override) }
        pushOverride(packageName)
    }

    /** 删除单应用自定义(该包回落纯默认配置语义)并定向推送。 */
    suspend fun removeOverride(packageName: String) {
        withContext(Dispatchers.IO) {
            writer.remove(ConfigKeys.overrideKey(packageName))
        }
        _overrides.update { it - packageName }
        pushOverride(packageName)
    }

    private fun pushOverride(packageName: String) {
        scope.launch { server.pushToPackage(packageName) }
    }

    init {
        // 迁移旧按包配置 → 全局键(仅一次;无旧键则回落默认并落盘,保证远程侧有值)
        writer.migrateLegacy()
        // V25 D2:默认配置收敛为内置推荐值 —— 无条件重置 switch_global,
        // 清除 V24 及更早「全局设置页」遗留的用户自定义值(此后无 UI 写入路径,
        // 注入侧兜底读到恒为内置默认,陈旧安装自愈)。
        writer.set(HookSwitch.DEFAULT)
        _globalSwitch.value = HookSwitch.DEFAULT
        // 预载已存在的单应用覆盖(本地权威;非法 JSON 容错为 DEFAULT 并被过滤)
        _overrides.value = writer.localKeysWithPrefix(ConfigKeys.OVERRIDE_PREFIX)
            .associate { key ->
                val pkg = key.removePrefix(ConfigKeys.OVERRIDE_PREFIX)
                pkg to AppListenOverride.fromJson(gson, writer.getLocal(key))
            }
            .filterValues { !it.isDefault }
    }
}
