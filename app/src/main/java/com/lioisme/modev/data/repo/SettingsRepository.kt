package com.lioisme.modev.data.repo

import android.content.SharedPreferences
import com.google.gson.Gson
import com.lioisme.modev.core.model.ConfigKeys
import com.lioisme.modev.core.model.HookSwitch
import com.lioisme.modev.data.server.ConfigServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

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
        val json = gson.toJson(switch)
        prefs.edit().putString(KEY, json).apply()
        // 远程写失败(legacy/服务未绑定)静默,本地已兜底
        runCatching {
            remotePrefs()?.edit()?.putString(KEY, json)?.commit()
        }
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
                prefs.edit().putString(KEY, first).apply()
                runCatching {
                    remotePrefs()?.edit()?.putString(KEY, first)?.commit()
                }
            }
        }
        // 无论是否取到旧值,清理全部旧键(本地 + 远程),消除逐应用残留
        legacy.forEach { key -> prefs.edit().remove(key).apply() }
        runCatching {
            remotePrefs()?.edit()?.let { editor ->
                legacy.forEach { editor.remove(it) }
                editor.commit()
            }
        }
    }

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
 * 全局捕获配置仓库(V10):ConfigWriter 持久化 + 内存 StateFlow +
 * 变更即推送 ConfigServer 广播(注入侧即时生效)。配置全局唯一,无包名维度。
 */
class SettingsRepository(
    private val server: ConfigServer,
    private val writer: ConfigWriter,
) {

    private val _globalSwitch = MutableStateFlow(HookSwitch.DEFAULT)

    /** 全局捕获配置(全应用唯一事实源,UI 收集即热更)。 */
    val globalSwitch: StateFlow<HookSwitch> = _globalSwitch

    /** 当前全局配置快照。 */
    val switch: HookSwitch get() = _globalSwitch.value

    /** 更新全局配置:持久化 + 广播,注入侧即时生效。 */
    fun set(switch: HookSwitch) {
        writer.set(switch)
        _globalSwitch.value = switch
        server.broadcastConfig(switch)
    }

    init {
        // 迁移旧按包配置 → 全局键(仅一次;无旧键则回落默认并落盘,保证远程侧有值)
        writer.migrateLegacy()
        _globalSwitch.value = writer.get() ?: HookSwitch.DEFAULT
        if (writer.get() == null) {
            writer.set(HookSwitch.DEFAULT)
        }
    }
}
