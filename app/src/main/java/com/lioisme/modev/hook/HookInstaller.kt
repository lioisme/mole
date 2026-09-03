package com.lioisme.modev.hook

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.lioisme.modev.core.codec.BehaviorRecordCodec
import com.lioisme.modev.core.codec.CryptoRecordCodec
import com.lioisme.modev.core.codec.Json
import com.lioisme.modev.core.codec.PrefsRecordCodec
import com.lioisme.modev.core.codec.SqlRecordCodec
import com.lioisme.modev.core.codec.WebViewRecordCodec
import com.lioisme.modev.core.codec.WireCodec
import com.lioisme.modev.core.model.AppListenOverride
import com.lioisme.modev.core.model.BehaviorRecord
import com.lioisme.modev.core.model.ConfigKeys
import com.lioisme.modev.core.model.CryptoRecord
import com.lioisme.modev.core.model.EffectiveSwitch
import com.lioisme.modev.core.model.HookSwitch
import com.lioisme.modev.core.model.PrefsRecord
import com.lioisme.modev.core.model.SqlRecord
import com.lioisme.modev.core.model.WebViewRecord
import com.lioisme.modev.core.model.NetworkRecord
import com.lioisme.modev.core.model.DeviceEnvRecord
import com.lioisme.modev.core.model.UiInteractionRecord
import com.lioisme.modev.core.model.SystemRecord
import com.lioisme.modev.core.model.ProcessRecord
import com.lioisme.modev.core.codec.NetworkRecordCodec
import com.lioisme.modev.core.codec.DeviceEnvRecordCodec
import com.lioisme.modev.core.codec.UiInteractionRecordCodec
import com.lioisme.modev.core.codec.SystemRecordCodec
import com.lioisme.modev.core.codec.ProcessRecordCodec
import com.lioisme.modev.framework.RemotePrefsBridge
import com.lioisme.modev.hook.probes.AlgorithmProbe
import com.lioisme.modev.hook.probes.BehaviorProbe
import com.lioisme.modev.hook.probes.DatabaseProbe
import com.lioisme.modev.hook.probes.DeviceEnvProbe
import com.lioisme.modev.hook.probes.PrefsProbe
import com.lioisme.modev.hook.probes.WebViewProbe
import com.lioisme.modev.hook.probes.NetworkProbe
import com.lioisme.modev.hook.probes.UiInteractionProbe
import com.lioisme.modev.hook.probes.SystemProbe
import com.lioisme.modev.hook.probes.ProcessControlProbe
import java.io.File

/**
 * 注入进程装配器(ADR-3/ADR-7):一次装配引擎/跟踪器/通道/出口,再按注册的
 * [Probe] 清单装配各监听能力。由入口(现代 libxposed 或 legacy Xposed)构造,
 * 全部状态为实例字段,进程内无全局可变单例。
 *
 * 新增监听能力 = 在 [probes] 清单注册新 [Probe],其余管线零改动。
 */
class HookInstaller(
    private val platform: Platform,
    /** 主包名(记录归档键:默认进程即包名;分离进程取 pkg 部分)。 */
    private val packageName: String,
    /** 实际进程名(hello 与 spool 分目录用,形如 pkg:sub)。 */
    private val processName: String,
    /** 现代变体注入:返回框架 RemotePreferences(可空);legacy 传 null,配置走 TCP 推送。 */
    private val remotePrefsProvider: (() -> SharedPreferences?)? = null,
    /** 监听能力清单(V29 WebView 一等公民入列);测试可注入替身。 */
    private val probes: List<Probe> =
        listOf(
            AlgorithmProbe(),
            DatabaseProbe(),
            BehaviorProbe(),
            PrefsProbe(),
            WebViewProbe(),
            NetworkProbe(),
            DeviceEnvProbe(),
            UiInteractionProbe(),
            SystemProbe(),
            ProcessControlProbe(),
        ),
) {

    // 通道与出口存在循环依赖(channel 的 ACK 回调驱动 sink,spool 发送游标依赖 channel
    // 连接状态),经 lazy 延迟解析,首次访问在 install() 内完成
    private val channel: ConfigChannel by lazy {
        ConfigChannel(
            packageName = packageName,
            processName = processName,
            onConfig = { /* 配置变更即生效(钩子经 config() 实时读取) */ },
            onAck = { seq -> sink.onAck(seq) },
            tokenProvider = { remotePrefsProvider?.invoke()?.getString(RemotePrefsBridge.TOKEN_KEY, null) },
        )
    }

    private val sink: RecordSink by lazy {
        // M5-3/M5-4: spool 为引擎级能力(两种变体一律启用),按进程名分目录防多进程互扰;
        // 注入早于 Application 时由 RecordSink 懒创建重试
        val spoolProvider: () -> RecordSpool? = {
            applicationContext()?.let { RecordSpool(File(it.filesDir, "modev_spool"), sub = processName) }
        }
        // M5-3 补充: seq 持久化(进程重启续号,防与库内旧记录撞号被 REPLACE 覆盖)
        val seqFileProvider: () -> File? = {
            applicationContext()?.let {
                File(it.filesDir, "modev_seq_${processName.replace(":", "_")}")
            }
        }
        RecordSink(
            // 注入侧编码分派(ADR-2):监听能力模型经各自编解码器产出载荷,
            // seq/recordType 由 WireCodec.record 强制写入信封,宿主按类型分发
            jsonOf = { record ->
                val payload = when (record) {
                    is CryptoRecord -> CryptoRecordCodec.encode(record)
                    is SqlRecord -> SqlRecordCodec.encode(record)
                    is BehaviorRecord -> BehaviorRecordCodec.encode(record)
                    is PrefsRecord -> PrefsRecordCodec.encode(record)
                    is WebViewRecord -> WebViewRecordCodec.encode(record)
                    is NetworkRecord -> NetworkRecordCodec.encode(record)
                    is DeviceEnvRecord -> DeviceEnvRecordCodec.encode(record)
                    is UiInteractionRecord -> UiInteractionRecordCodec.encode(record)
                    is SystemRecord -> SystemRecordCodec.encode(record)
                    is ProcessRecord -> ProcessRecordCodec.encode(record)
                }
                WireCodec.record(record.seq, record.recordType, payload)
            },
            sendJson = { json -> channel.sendRecordJson(json) },
            isConnected = { channel.connected },
            spoolProvider = spoolProvider,
            seqFileProvider = seqFileProvider,
        )
    }

    private var installed = false

    /**
     * 幂等装配:每个注入进程仅一次;后续调用直接返回。
     * 装配失败不影响宿主(引擎 PROTECTIVE 模式逐点兜底)。
     */
    @Synchronized
    fun install() {
        if (installed) return
        installed = true

        // M3-1/M3-4: 现代变体默认配置兜底 = RemotePreferences 直读(无宿主也可用),
        // 并注册变更监听即时生效;TCP 推送仍可用(与远程值一致,且是 legacy 唯一路径)。
        // V10:全局配置 switch_global;V23(D4):叠加本包覆盖键 resolve 出有效配置 ——
        // 模块 App 未运行时 per-app 监听设置依然生效,legacy 变体(无远程偏好)自然跳过。
        remotePrefsProvider?.invoke()?.let { remote ->
            val globalKey = RemotePrefsBridge.GLOBAL_SWITCH_KEY
            val overrideKey = ConfigKeys.overrideKey(packageName)

            fun effectiveFromRemote(): HookSwitch? {
                val global = parseSwitch(remote.getString(globalKey, null))
                val overrideJson = remote.getString(overrideKey, null)
                if (global == null && overrideJson == null) return null
                return EffectiveSwitch.resolve(
                    global ?: HookSwitch.DEFAULT,
                    AppListenOverride.fromJson(Json.gson, overrideJson),
                )
            }

            effectiveFromRemote()?.let { channel.updateConfig(it) }
            runCatching {
                remote.registerOnSharedPreferenceChangeListener { prefs, _ ->
                    // 不按 changedKey 过滤:远程偏好对新增键的事件粒度不可靠,
                    // 任一变化一律全量重读本包相关键(resolve 幂等,开销为内存级 SP 读)
                    effectiveFromRemote()?.let { channel.updateConfig(it) }
                }
            }
        }

        // V35 兜底:Vector Xposed 不投递 XposedService,RemotePreferences 中
        // 可能只有全局配置而无 per-app override;始终尝试从磁盘加载 override。
        runCatching {
            val file = java.io.File("/data/local/tmp/modev_config_$packageName.json")
            if (file.exists() && file.canRead()) {
                val json = file.readText(Charsets.UTF_8).trim()
                if (json.isNotEmpty()) {
                    val override = AppListenOverride.fromJson(Json.gson, json)
                    if (!override.isDefault) {
                        val resolved = EffectiveSwitch.resolve(channel.currentConfig, override)
                        channel.updateConfig(resolved)
                        Log.i(TAG, "V35 兜底:从磁盘加载 $packageName override 成功")
                    }
                }
            }
        }

        channel.start()
        sink.start()

        val context = ProbeContext(
            engine = HookEngine(platform),
            config = { channel.currentConfig },
            tracker = CryptoContextTracker,
            sink = sink,
            packageName = packageName,
        )
        for (probe in probes) {
            try {
                probe.install(context)
                // V34 审计增强:干预型探针安装时输出日志,便于调试和审计
                if (probe is InterventionProbeMarker) {
                    Log.i(TAG, "干预型探针已安装: ${probe.javaClass.simpleName}")
                }
            } catch (e: Throwable) {
                Log.e(TAG, "探针安装失败: ${probe.javaClass.simpleName}", e)
            }
        }
    }

    /** 注入进程取目标应用 Context(供 Spool 落盘);失败返回 null(不启用 Spool)。 */
    private fun applicationContext(): Context? = runCatching {
        Class.forName("android.app.ActivityThread")
            .getMethod("currentApplication")
            .invoke(null) as? Context
    }.getOrNull()

    private fun parseSwitch(json: String?): HookSwitch? =
        json?.let { HookSwitch.fromJson(Json.gson, it) }

    /**
     * V35 兜底:从磁盘 JSON 文件加载 per-app 配置。
     * 文件路径:/data/local/tmp/modev_config_{packageName}.json
     * 格式:与 SharedPreferences override_{pkg} 键值相同(JSON 对象)。
     */
    private fun loadConfigFromDisk(): HookSwitch? = runCatching {
        val file = java.io.File("/data/local/tmp/modev_config_$packageName.json")
        if (!file.exists()) return null
        val json = file.readText(Charsets.UTF_8).trim()
        if (json.isEmpty()) return null
        val globalJson = parseSwitch(
            runCatching { file.resolveSibling("modev_config_global.json").readText(Charsets.UTF_8).trim() }
                .getOrNull()
        )
        val override = AppListenOverride.fromJson(Json.gson, json)
        EffectiveSwitch.resolve(globalJson ?: HookSwitch.DEFAULT, override).also {
            Log.i(TAG, "V35 兜底:从磁盘加载 $packageName 配置成功")
        }
    }.getOrNull()

    companion object {
        private const val TAG = "HookInstaller"
    }
}

/**
 * V34 审计增强:标记干预型探针,安装时输出日志。
 * 实现此接口的 Probe 在安装时会被记录到 logcat。
 */
interface InterventionProbeMarker