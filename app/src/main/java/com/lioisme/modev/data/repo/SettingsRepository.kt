package com.lioisme.modev.data.repo

import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.gson.Gson
import com.lioisme.modev.core.codec.WireProtocol
import com.lioisme.modev.core.model.AppListenOverride
import com.lioisme.modev.core.model.ConfigKeys
import com.lioisme.modev.core.model.EffectiveSwitch
import com.lioisme.modev.core.model.FeatureConfig
import com.lioisme.modev.core.model.HookSwitch
import com.lioisme.modev.data.server.ConfigServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
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
    private val localPrefs: SharedPreferences,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _globalSwitch = MutableStateFlow(HookSwitch.DEFAULT)

    /**
     * 默认配置流(V25:恒为内置推荐值;保留流形态供每应用页 combine 与
     * capability 消费,不再有任何写入方)。
     */
    val globalSwitch: StateFlow<HookSwitch> = _globalSwitch

    private val _serverExternalAccess = MutableStateFlow(false)

    /**
     * 服务监听外部访问(V26):false(默认)= 127.0.0.1 仅本机;
     * true = 0.0.0.0 所有接口(同网段设备可连)。宿主本地偏好,
     * 不经远程镜像(注入侧零感知)。
     */
    val serverExternalAccess: StateFlow<Boolean> = _serverExternalAccess

    /**
     * 写入外部访问开关并即时生效:本地持久化 → 服务端改绑([ConfigServer.restart],
     * 已连接会话由注入侧重连循环恢复)。
     */
    suspend fun setServerExternalAccess(enabled: Boolean) {
        withContext(Dispatchers.IO) {
            localPrefs.edit { putBoolean(ConfigKeys.SERVER_EXTERNAL_ACCESS, enabled) }
        }
        _serverExternalAccess.value = enabled
        // volatile 同步置位(状态可观测确定性),重启改绑异步执行
        server.externalAccess = enabled
        scope.launch { server.restart() }
    }

    private val _serverPort = MutableStateFlow(WireProtocol.PORT)

    /** 服务监听端口(V29):默认1510,可自定义。 */
    val serverPort: StateFlow<Int> = _serverPort

    /**
     * 写入监听端口并即时生效:本地持久化 → 服务端改绑新端口([ConfigServer.restart],
     * 已连接会话由注入侧重连循环恢复;当前 hook 侧使用同一默认常量,非1510时需后续同步)。
     */
    suspend fun setServerPort(port: Int) {
        val clamped = port.coerceIn(1, 65535)
        withContext(Dispatchers.IO) {
            localPrefs.edit { putInt(ConfigKeys.SERVER_PORT, clamped) }
        }
        _serverPort.value = clamped
        server.port = clamped
        scope.launch { server.restart() }
    }

    private val _overrides = MutableStateFlow<Map<String, AppListenOverride>>(emptyMap())

    /** 已存在的单应用自定义偏差(包名 → 偏差;仅含非零偏差项,init 时从本地预载)。 */
    val overrides: StateFlow<Map<String, AppListenOverride>> = _overrides

    /** 当前包的自定义偏差快照(未定制返回 [AppListenOverride.DEFAULT],调用方免空判)。 */
    fun overrideFor(packageName: String): AppListenOverride =
        _overrides.value[packageName] ?: AppListenOverride.DEFAULT

    /** 该包的有效配置快照(默认配置 ⊕ 偏差,纯解析;V37 T2.2 叠加关键词库解析)。 */
    fun effectiveSwitch(packageName: String): HookSwitch =
        withKeywordRules(
            EffectiveSwitch.resolve(_globalSwitch.value, _overrides.value[packageName]),
            _overrides.value[packageName],
        )

    /** 该包的有效配置流:默认配置、偏差或关键词库任一变化即重发射(distinct 由数据类 equals 保证)。 */
    fun effectiveSwitchFlow(packageName: String): Flow<HookSwitch> {
        val library = keywordLibrary
        val global = globalSwitch
        val overrideFlow = combine(global, overrides) { g, map -> g to map[packageName] }
        return if (library == null) {
            overrideFlow.map { (g, ovr) -> withKeywordRules(EffectiveSwitch.resolve(g, ovr), ovr) }
        } else {
            combine(global, overrides, library.entries) { g, map, _ ->
                val ovr = map[packageName]
                withKeywordRules(EffectiveSwitch.resolve(g, ovr), ovr)
            }
        }
    }

    // ---- V37 T2.2 关键词库接线 ----

    private var keywordLibrary: KeywordLibraryRepository? = null

    /** 装配关键词库(容器初始化期一次;null = 未装配,规则解析恒空)。 */
    fun attachKeywordLibrary(library: KeywordLibraryRepository) {
        keywordLibrary = library
    }

    /** 关键词库变更后向全部在线会话重推各自有效配置(库为全局资产,影响所有作用域包)。 */
    fun repushAllConnected() {
        scope.launch { server.pushToAllConnected() }
    }

    /**
     * 有效配置叠加关键词规则(V37 T2.2):
     * keywordIds=null 跟随全局启用集;非 null 取其子集;库未装配时恒空规则
     * (旧扁平词表经 activeKeywordRules 降级兜底,兼容语义不变)。
     */
    private fun withKeywordRules(switch: HookSwitch, override: AppListenOverride?): HookSwitch {
        val library = keywordLibrary ?: return switch
        val followGlobal = override?.keywordFollowGlobal ?: true
        val ids = if (followGlobal) null else override?.keywordIds ?: emptyList()
        val rules = library.resolveRules(ids)
        return switch.copy(feature = switch.feature.copy(dialogKeywordRules = rules))
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

    // ---- V37 T3.5 全局功能配置(画像 / 黑名单追加;词表走关键词库) ----

    private val _globalFeature = MutableStateFlow(FeatureConfig())

    /** 全局功能配置(伪装画像选择 + 黑名单追加项;所有作用域包的 feature 默认值)。 */
    val globalFeature: StateFlow<FeatureConfig> = _globalFeature

    /**
     * 写入全局功能配置:本地持久化 → 重播 switch_global 种子(注入侧直读兜底可见)
     * → 全部在线会话重推各自有效配置。
     */
    suspend fun setGlobalFeature(config: FeatureConfig) {
        withContext(Dispatchers.IO) {
            localPrefs.edit { putString(ConfigKeys.GLOBAL_FEATURE, gson.toJson(config)) }
        }
        _globalFeature.value = config
        val seed = HookSwitch.DEFAULT.copy(feature = config)
        withContext(Dispatchers.IO) { writer.set(seed) }
        _globalSwitch.value = seed
        server.pushToAllConnected()
    }

    init {
        // 迁移旧按包配置 → 全局键(仅一次;无旧键则回落默认并落盘,保证远程侧有值)
        writer.migrateLegacy()
        // V37 T3.5:恢复全局功能配置(画像/黑名单;默认空 = 内置行为不变)
        _globalFeature.value = localPrefs.getString(ConfigKeys.GLOBAL_FEATURE, null)
            ?.let { runCatching { gson.fromJson(it, FeatureConfig::class.java) }.getOrNull() }
            ?: FeatureConfig()
        // V25 D2:默认配置收敛为内置推荐值 ⊕ 全局功能配置 —— 无条件重置 switch_global,
        // 清除 V24 及更早「全局设置页」遗留的用户自定义值(画像/黑名单经
        // setGlobalFeature 唯一写路径进入种子,注入侧兜底直读可见)。
        val seed = HookSwitch.DEFAULT.copy(feature = _globalFeature.value)
        writer.set(seed)
        _globalSwitch.value = seed
        // 预载已存在的单应用覆盖(本地权威;非法 JSON 容错为 DEFAULT 并被过滤)
        _overrides.value = writer.localKeysWithPrefix(ConfigKeys.OVERRIDE_PREFIX)
            .associate { key ->
                val pkg = key.removePrefix(ConfigKeys.OVERRIDE_PREFIX)
                pkg to AppListenOverride.fromJson(gson, writer.getLocal(key))
            }
            .filterValues { !it.isDefault }
        // 镜像覆盖到 RemotePreferences:注入侧 install() 在 TCP 推送前读 Remote,
        // 未镜像的覆盖键对注入侧不可见(仅本地 SP 有值 → effectiveFromRemote() 返回 null)。
        _overrides.value.forEach { (pkg, override) ->
            writer.put(ConfigKeys.overrideKey(pkg), override.toJson(gson))
        }
        // V26:恢复服务监听外部访问偏好(进程重启后重放;默认停用零动作)
        val storedExternalAccess = localPrefs.getBoolean(ConfigKeys.SERVER_EXTERNAL_ACCESS, false)
        _serverExternalAccess.value = storedExternalAccess
        if (storedExternalAccess) {
            server.externalAccess = true
            // 服务端可能已在容器惰性初始化时以回环启动,restart 立即改绑全接口
            scope.launch { server.restart() }
        }
        // V29:恢复服务监听端口偏好(进程重启后重放;默认1510零动作)
        val storedPort = localPrefs.getInt(ConfigKeys.SERVER_PORT, WireProtocol.PORT)
        if (storedPort != WireProtocol.PORT) {
            _serverPort.value = storedPort
            server.port = storedPort
            scope.launch { server.restart() }
        }
    }
}
