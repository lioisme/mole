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
import com.lioisme.modev.domain.asset.KeywordLibraryService
import com.lioisme.modev.domain.config.SpoofProfileService
import com.lioisme.modev.domain.config.FeatureService
import com.lioisme.modev.domain.config.ListenConfigService
import com.lioisme.modev.domain.server.McpServiceConfig
import com.lioisme.modev.domain.server.PortWriteGuard
import com.lioisme.modev.domain.server.ServerConfigService
import com.lioisme.modev.data.server.ConfigServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOf
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
) : ListenConfigService, FeatureService, ServerConfigService, McpServiceConfig {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _globalSwitch = MutableStateFlow(HookSwitch.DEFAULT)

    /**
     * 默认配置流(V25:恒为内置推荐值;保留流形态供每应用页 combine 与
     * capability 消费,不再有任何写入方)。
     */
    override val globalSwitch: StateFlow<HookSwitch> = _globalSwitch

    private val _serverExternalAccess = MutableStateFlow(false)

    /**
     * 统一外部访问(V41 T2,D-1):wire ConfigServer 与 MCP McpServer **双服务消费**
     * 的单一开关——false(默认)= 127.0.0.1 仅本机;true = 0.0.0.0 所有接口
     * (同网段设备可连)。宿主本地偏好,不经远程镜像(注入侧零感知)。
     * V41 起取代 V26/V38 的 wire/MCP 双键分裂(`MCP_EXTERNAL_ACCESS` 仅迁移输入)。
     */
    override val serverExternalAccess: StateFlow<Boolean> = _serverExternalAccess

    /**
     * 写入统一外部访问开关并即时生效(D-1):本地持久化 → 双服务改绑——
     * wire volatile 置位 + 异步 restart([ConfigServer.restart],已连接会话由
     * 注入侧重连循环恢复);MCP 经 [mcpRestarter] 回填绑定态后异步重启。
     * 与令牌认证互相独立(V40 D-4 语义保持):是否要求 Bearer 仅由
     * [ConfigKeys.MCP_TOKEN_AUTH] 决定。
     */
    override suspend fun setExternalAccess(enabled: Boolean) {
        withContext(Dispatchers.IO) {
            localPrefs.edit { putBoolean(ConfigKeys.SERVER_EXTERNAL_ACCESS, enabled) }
        }
        _serverExternalAccess.value = enabled
        // wire:volatile 同步置位(状态可观测确定性),重启改绑异步执行
        server.externalAccess = enabled
        scope.launch { server.restart() }
        // MCP:回调内回填 externalAccess 后 restart(异步,见 restartMcpAsync)
        restartMcpAsync()
    }

    private val _serverPort = MutableStateFlow(WireProtocol.PORT)

    /** 服务监听端口(V29):默认 1511(V39:1510 让位 MCP),可自定义。 */
    override val serverPort: StateFlow<Int> = _serverPort

    /**
     * 写入监听端口并即时生效:可用性硬闸 → 本地持久化 → 服务端改绑
     * ([ConfigServer.restart],已连接会话由注入侧重连循环恢复)。
     *
     * V45 DUAL-3 / T2.4:校验从 UI 组件上移到本写口 —— UI 与 MCP 两侧走同一实现,
     * 不再出现「UI 三闸硬拒、MCP clamp 直写」;被拒时偏好零写入。
     */
    override suspend fun setServerPort(port: Int) {
        PortWriteGuard.requireAvailable(
            candidate = port,
            selfPort = _serverPort.value,
            selfLabel = WIRE_LABEL,
            otherPort = _mcpPort.value,
            otherLabel = MCP_LABEL,
            externalAccess = _serverExternalAccess.value,
        )
        withContext(Dispatchers.IO) {
            localPrefs.edit { putInt(ConfigKeys.SERVER_PORT, port) }
        }
        _serverPort.value = port
        server.port = port
        scope.launch { server.restart() }
    }

    private val _overrides = MutableStateFlow<Map<String, AppListenOverride>>(emptyMap())

    /** 已存在的单应用自定义偏差(包名 → 偏差;仅含非零偏差项,init 时从本地预载)。 */
    override val overrides: StateFlow<Map<String, AppListenOverride>> = _overrides

    /** 当前包的自定义偏差快照(未定制返回 [AppListenOverride.DEFAULT],调用方免空判)。 */
    override fun overrideFor(packageName: String): AppListenOverride =
        _overrides.value[packageName] ?: AppListenOverride.DEFAULT

    /** 该包的有效配置快照(默认配置 ⊕ 偏差,纯解析;V37 T2.2 叠加关键词库解析)。 */
    override fun effectiveSwitch(packageName: String): HookSwitch =
        resolveFor(_globalSwitch.value, _overrides.value[packageName])

    /** 该包的有效配置流:默认配置、偏差、关键词库或画像库任一变化即重发射(distinct 由数据类 equals 保证)。 */
    override fun effectiveSwitchFlow(packageName: String): Flow<HookSwitch> {
        val global = globalSwitch
        val overrideFlow = combine(global, overrides) { g, map -> g to map[packageName] }
        return combine(overrideFlow, assetRevision()) { (g, ovr), _ -> resolveFor(g, ovr) }
    }

    /**
     * 全局资产(关键词库 / 画像库)的「任一变更」触发流(V47 T2 与关键词库接线的合并重构):
     * 两个可装配服务各自是 StateFlow(发射即有值),故未装配侧以恒流补齐,
     * 保证装配组合任意一种都不改变「变更后立即重推」的既有语义。
     */
    private fun assetRevision(): Flow<Unit> {
        val library = keywordLibrary
        val profiles = spoofProfiles
        return when {
            library != null && profiles != null ->
                combine(library.entries, profiles.profiles) { _, _ -> Unit }

            library != null -> library.entries.map { Unit }
            profiles != null -> profiles.profiles.map { Unit }
            else -> flowOf(Unit)
        }
    }

    /** 单一解析出口:偏差 → 关键词规则 → 画像快照(流与同步读同源,禁两侧各算一遍)。 */
    private fun resolveFor(global: HookSwitch, override: AppListenOverride?): HookSwitch =
        expandProfile(withKeywordRules(EffectiveSwitch.resolve(global, override), override))

    // ---- V47 T2.2 画像库接线 ----

    private var spoofProfiles: SpoofProfileService? = null

    /** 装配画像库(容器初始化期一次;null = 未装配,快照恒保持来路值不覆盖)。 */
    fun attachSpoofProfiles(service: SpoofProfileService) {
        spoofProfiles = service
    }

    /**
     * 把生效的 `spoofProfileId` 展开为注入侧消费的 [FeatureConfig.deviceProfile] 快照。
     *
     * - 未装配画像库 ⇒ 原样返回(注入侧「直读两键 / 磁盘兜底」通路依赖来路已带的快照,
     *   绝不能用空快照把它清掉);
     * - 未知 id ⇒ 空画像(不伪装),**不回落任何档位**(GAP-02 的静默失效就是反面教材);
     * - 旧 `spoofOverrides` 原样透传,不映射进画像(其 `ro.*` 键无对应画像字段,
     *   映射即有损;既有 hooker 仍按该表取值,直至 WP-3/WP-4 逐组接管)。
     */
    private fun expandProfile(switch: HookSwitch): HookSwitch {
        val service = spoofProfiles ?: return switch
        val feature = switch.feature
        val profile = service.resolve(feature.spoofProfileId).profileOrEmpty()
        return if (profile == feature.deviceProfile) {
            switch
        } else {
            switch.copy(feature = feature.copy(deviceProfile = profile))
        }
    }

    // ---- V37 T2.2 关键词库接线 ----

    private var keywordLibrary: KeywordLibraryService? = null

    /** 装配关键词库(容器初始化期一次;null = 未装配,规则解析恒空)。 */
    fun attachKeywordLibrary(library: KeywordLibraryService) {
        keywordLibrary = library
    }

    /** 关键词库变更后向全部在线会话重推各自有效配置(库为全局资产,影响所有作用域包)。 */
    override fun repushAllConnected() {
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
    override suspend fun setOverride(packageName: String, override: AppListenOverride) {
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

    /**
     * 删除单应用自定义(两域一起清)并定向推送。
     *
     * **不是契约方法**(V45 T3.5 / DUAL-12):UI 与 MCP 的「恢复默认配置」一律走
     * [ListenConfigService.resetOverride] 按域清,否则一页恢复会抹掉另一页的定制。
     * 本入口只服务两件事 —— [setOverride] 的零偏差删键归一、测试夹具复位。
     */
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
    override val globalFeature: StateFlow<FeatureConfig> = _globalFeature

    // ---- V38 MCP 服务配置(V39 令牌化;宿主本地;与 wire ConfigServer 完全独立) ----

    private val _mcpEnabled = MutableStateFlow(true)

    /** MCP 服务启用(V38):默认 true = 随宿主进程常驻(V39:默认回环 127.0.0.1:1510,令牌认证默认停用)。 */
    override val mcpEnabled: StateFlow<Boolean> = _mcpEnabled

    private val _mcpPort = MutableStateFlow(ConfigKeys.MCP_DEFAULT_PORT)

    /** MCP 监听端口(V39):默认 1510,与 wire 1511 区隔。 */
    override val mcpPort: StateFlow<Int> = _mcpPort

    private val _mcpTokenAuth = MutableStateFlow(false)

    /** MCP 令牌认证(V39):默认停用;启用后**全接口**(含回环 adb forward)必须携带 Bearer 令牌。 */
    override val mcpTokenAuth: StateFlow<Boolean> = _mcpTokenAuth

    private val _mcpTokens = MutableStateFlow<List<com.lioisme.modev.core.model.McpTokenEntry>>(emptyList())

    /** MCP 令牌列表(V39):多令牌;创建/撤销即时生效(原地更新校验集,不重启 socket、不踢会话)。 */
    override val mcpTokens: StateFlow<List<com.lioisme.modev.core.model.McpTokenEntry>> = _mcpTokens

    /** MCP 回调:端口/绑定/启停变更后由仓库驱动服务端重启(setter 装配,AppContainer 接线)。 */
    var mcpRestarter: (() -> Unit)? = null

    /**
     * MCP 重启统一收口(V41 V1.4):**恒异步**——调用方多为 Main 调度的 VM 写路径,
     * 回调链直达 NanoHTTPD start(主线程开 ServerSocket,曾致 jank/StrictMode 违规、
     * NetworkOnMainThreadException 被服务端 catch-all 吞掉后静默 bind 失败)。
     * 与 wire 侧 `scope.launch { server.restart() }` 模式对齐。
     */
    private fun restartMcpAsync() {
        scope.launch { mcpRestarter?.invoke() }
    }

    /** MCP 回调:令牌集合变更后原地推送服务端校验集(无需重启,避免踢掉已连会话)。 */
    var mcpTokenSetUpdater: ((Set<String>) -> Unit)? = null

    /** 写 MCP 启用开关:持久化 → 内存 → 异步 start/stop。 */
    override suspend fun setMcpEnabled(enabled: Boolean) {
        withContext(Dispatchers.IO) {
            localPrefs.edit { putBoolean(ConfigKeys.MCP_ENABLED, enabled) }
        }
        _mcpEnabled.value = enabled
        restartMcpAsync()
    }

    /**
     * 写 MCP 端口:可用性硬闸(与 wire 同一 [PortWriteGuard],对端 = wire 端口)→
     * 持久化 → 内存 → 异步重启改绑(V45 DUAL-3 同源收口)。
     */
    override suspend fun setMcpPort(port: Int) {
        PortWriteGuard.requireAvailable(
            candidate = port,
            selfPort = _mcpPort.value,
            selfLabel = MCP_LABEL,
            otherPort = _serverPort.value,
            otherLabel = WIRE_LABEL,
            externalAccess = _serverExternalAccess.value,
        )
        withContext(Dispatchers.IO) {
            localPrefs.edit { putInt(ConfigKeys.MCP_PORT, port) }
        }
        _mcpPort.value = port
        restartMcpAsync()
    }

    /** 写 MCP 令牌认证(V40 D-4 解耦):true = 启用后全接口强制 Bearer(列表为空即时派生一枚,
     * 避免「启用即锁死」);false = 停用认证。与外部访问无级联——外部 0.0.0.0 监听
     * 是否可无认证访问由用户显式选择(UI 文案承载风险提示)。
     */
    override suspend fun setMcpTokenAuth(enabled: Boolean) {
        if (enabled && _mcpTokens.value.isEmpty()) {
            persistMcpTokens(_mcpTokens.value + com.lioisme.modev.core.model.McpTokenEntry.create(label = ""))
        }
        persistMcpTokenAuth(enabled)
        restartMcpAsync()
    }

    /** 新增令牌(label 可空 = 未命名,UI 回落资源文案):持久化 → 原地更新校验集;返回新条目。 */
    override suspend fun addMcpToken(label: String): com.lioisme.modev.core.model.McpTokenEntry {
        val entry = com.lioisme.modev.core.model.McpTokenEntry.create(label.trim())
        persistMcpTokens(_mcpTokens.value + entry)
        return entry
    }

    /** 重命名:仅换 label,值集合原序原值 → 校验集内容零变化(不重启不踢会话)。 */
    override suspend fun setMcpTokenLabel(id: String, label: String) {
        val trimmed = label.trim()
        val current = _mcpTokens.value
        val next = current.map { if (it.id == id) it.copy(label = trimmed) else it }
        if (next == current) return
        persistMcpTokens(next)
    }

    /** 撤销令牌:持久化 → 原地更新校验集;撤销最后一枚不自动停用认证(空集合 = 全拒,UI 提示)。 */
    override suspend fun revokeMcpToken(id: String) {
        persistMcpTokens(_mcpTokens.value.filterNot { it.id == id })
    }

    private suspend fun persistMcpTokenAuth(enabled: Boolean) {
        withContext(Dispatchers.IO) {
            localPrefs.edit { putBoolean(ConfigKeys.MCP_TOKEN_AUTH, enabled) }
        }
        _mcpTokenAuth.value = enabled
    }

    private suspend fun persistMcpTokens(tokens: List<com.lioisme.modev.core.model.McpTokenEntry>) {
        withContext(Dispatchers.IO) {
            localPrefs.edit { putString(ConfigKeys.MCP_TOKENS, gson.toJson(tokens)) }
        }
        _mcpTokens.value = tokens
        mcpTokenSetUpdater?.invoke(tokens.map { it.value }.toSet())
    }

    /**
     * 写入全局功能配置:本地持久化 → 重播 switch_global 种子(注入侧直读兜底可见)
     * → 全部在线会话重推各自有效配置。
     */
    override suspend fun setGlobalFeature(config: FeatureConfig) {
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
        // V43 T7/V5.1:core 日志出口注入(宿主侧)
        EffectiveSwitch.interventionWarn = { msg -> android.util.Log.w("EffectiveSwitch", msg) }
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
        // V41 T2 统一外部访问:双键 OR 合并迁移(D-1)——SERVER_EXTERNAL_ACCESS 为唯一
        // 事实源;V38 遗留 mcp_external_access 读后删除(幂等:旧键不存在即零动作)。
        // 曾单独开启 MCP 外部访问的用户升级后双服务同绑 0.0.0.0(RK-1 决策语义)。
        val legacyMcpExternal = localPrefs.getBoolean(ConfigKeys.MCP_EXTERNAL_ACCESS, false)
        val storedExternalAccess = localPrefs.getBoolean(ConfigKeys.SERVER_EXTERNAL_ACCESS, false)
        val unifiedExternalAccess = storedExternalAccess || legacyMcpExternal
        if (localPrefs.contains(ConfigKeys.MCP_EXTERNAL_ACCESS)) {
            localPrefs.edit {
                remove(ConfigKeys.MCP_EXTERNAL_ACCESS)
                if (unifiedExternalAccess != storedExternalAccess) {
                    putBoolean(ConfigKeys.SERVER_EXTERNAL_ACCESS, unifiedExternalAccess)
                }
            }
        }
        _serverExternalAccess.value = unifiedExternalAccess
        if (unifiedExternalAccess) {
            server.externalAccess = true
            // 服务端可能已在容器惰性初始化时以回环启动,restart 立即改绑全接口
            scope.launch { server.restart() }
        }
        // V29:恢复服务监听端口偏好(进程重启后重放;默认 1511 零动作)
        val storedPort = localPrefs.getInt(ConfigKeys.SERVER_PORT, WireProtocol.PORT)
        if (storedPort != WireProtocol.PORT) {
            _serverPort.value = storedPort
            server.port = storedPort
            scope.launch { server.restart() }
        }
        // V38→V39:恢复 MCP 服务偏好(进程重启后重放;enabled 默认 true,端口默认零动作)。
        // V41 T2:外部访问已随统一键上方重放(mcp_external_access 退役为迁移输入)。
        _mcpEnabled.value = localPrefs.getBoolean(ConfigKeys.MCP_ENABLED, true)
        _mcpPort.value = localPrefs.getInt(ConfigKeys.MCP_PORT, ConfigKeys.MCP_DEFAULT_PORT)
        // V39 令牌迁移:旧单键 mcp_token 导入为未命名条目(一次性,导入后删旧键);
        // 令牌认证直读偏好(V40 D-4 解耦;V39 升级用户曾级联写入过显式 prefs 值,保持不变)。
        var tokens = localPrefs.getString(ConfigKeys.MCP_TOKENS, null)
            ?.let { runCatching { gson.fromJson(it, Array<com.lioisme.modev.core.model.McpTokenEntry>::class.java).toList() }.getOrNull() }
            ?: emptyList()
        val legacyToken = localPrefs.getString(ConfigKeys.MCP_TOKEN, null)
        if (!legacyToken.isNullOrBlank() && tokens.none { it.value == legacyToken }) {
            tokens = tokens + com.lioisme.modev.core.model.McpTokenEntry(
                id = java.util.UUID.randomUUID().toString().replace("-", ""),
                label = "",
                value = legacyToken,
                createdAt = 0L,
            )
            localPrefs.edit {
                putString(ConfigKeys.MCP_TOKENS, gson.toJson(tokens))
                remove(ConfigKeys.MCP_TOKEN)
            }
        }
        _mcpTokens.value = tokens
        // V40 D-4 解耦:令牌认证直读偏好,不再按外部访问重推导(升级用户若曾依赖
        // V39 级联语义,升级后保持 prefs 中的显式值;V39 未级联写入过 prefs 时默认停用)。
        _mcpTokenAuth.value = localPrefs.getBoolean(ConfigKeys.MCP_TOKEN_AUTH, false)
    }

    companion object {
        /** 端口冲突分型文案用服务名(与 UI 预检同源字面,AGENTS #29)。 */
        private const val WIRE_LABEL = "wire"
        private const val MCP_LABEL = "MCP"
    }
}
