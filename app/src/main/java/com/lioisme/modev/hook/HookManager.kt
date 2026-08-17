package com.lioisme.modev.hook

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.lioisme.modev.core.codec.WireCodec
import com.lioisme.modev.core.model.HookSwitch
import com.lioisme.modev.framework.RemotePrefsBridge
import com.lioisme.modev.hook.hooks.Base64Hooker
import com.lioisme.modev.hook.hooks.CipherHooker
import com.lioisme.modev.hook.hooks.KeyAgreementHooker
import com.lioisme.modev.hook.hooks.KeyFactoryHooker
import com.lioisme.modev.hook.hooks.KeyGenerationHooker
import com.lioisme.modev.hook.hooks.KeyParamHooker
import com.lioisme.modev.hook.hooks.KeyStoreHooker
import com.lioisme.modev.hook.hooks.MacHooker
import com.lioisme.modev.hook.hooks.MessageDigestHooker
import com.lioisme.modev.hook.hooks.SecureRandomHooker
import com.lioisme.modev.hook.hooks.SignatureHooker
import com.lioisme.modev.hook.hooks.WebViewHooker
import java.io.File

/**
 * Hook 引擎组装器:每个注入进程一次,装配引擎/跟踪器/通道/出口与八类 Hooker。
 * 由入口(现代 libxposed 或 legacy Xposed)注入对应 [Platform] 适配器,业务层零框架依赖。
 */
object HookManager {

    private lateinit var engine: HookEngine
    private lateinit var tracker: CryptoContextTracker
    private lateinit var channel: ConfigChannel
    private lateinit var sink: RecordSink

    private var installed = false

    /**
     * @param packageName 主包名(记录归档键:默认进程即包名;分离进程取 pkg 部分)
     * @param processName 实际进程名(hello 与 spool 分目录用,形如 pkg:sub)
     * @param remotePrefsProvider 现代变体注入:返回框架 RemotePreferences(可空);
     *   legacy 传 null,配置走 TCP 推送(重构前路径)
     */
    @Synchronized
    fun install(
        platform: Platform,
        packageName: String,
        processName: String,
        remotePrefsProvider: (() -> SharedPreferences?)? = null,
    ) {
        if (installed) return
        installed = true

        engine = HookEngine(platform)
        tracker = CryptoContextTracker
        channel = ConfigChannel(
            packageName = packageName,
            processName = processName,
            onConfig = { /* 配置变更即生效(钩子经 config() 实时读取) */ },
            onAck = { seq -> sink.onAck(seq) },
            tokenProvider = { remotePrefsProvider?.invoke()?.getString(RemotePrefsBridge.TOKEN_KEY, null) },
        )

        // M5-3/M5-4: spool 为引擎级能力(两种变体一律启用),按进程名分目录防多进程互扰;
        // 注入早于 Application 时由 RecordSink 懒创建重试
        val spoolProvider: () -> RecordSpool? = {
            applicationContext()?.let { RecordSpool(File(it.filesDir, "modev_spool"), sub = processName) }
        }
        // M5-3 补充: seq 持久化(进程重启续号,防与库内旧记录撞号被 REPLACE 覆盖);
        // 懒获取(注入早于 Application 时 Context 不可用,由 RecordSink 重试)
        val seqFileProvider: () -> File? = {
            applicationContext()?.let {
                File(it.filesDir, "modev_seq_${processName.replace(":", "_")}")
            }
        }
        sink = RecordSink(
            jsonOf = { record -> WireCodec.record(record) },
            sendJson = { json -> channel.sendRecordJson(json) },
            isConnected = { channel.connected },
            spoolProvider = spoolProvider,
            seqFileProvider = seqFileProvider,
        )

        // M3-1/M3-4: 现代变体默认配置兜底 = RemotePreferences 直读(无宿主也可用),
        // 并注册变更监听即时生效;TCP 推送仍可用(与远程值一致,且是 legacy 唯一路径)
        // V10:配置全局唯一(switch_global),与包名解耦
        remotePrefsProvider?.invoke()?.let { remote ->
            val key = RemotePrefsBridge.GLOBAL_SWITCH_KEY
            parseSwitch(remote.getString(key, null))?.let { channel.updateConfig(it) }
            runCatching {
                remote.registerOnSharedPreferenceChangeListener { prefs, changedKey ->
                    if (changedKey == key) {
                        parseSwitch(prefs.getString(changedKey, null))?.let { channel.updateConfig(it) }
                    }
                }
            }
        }

        channel.start()
        sink.start()

        val config: () -> HookSwitch = { channel.currentConfig }
        val registrars = listOf(
            CipherHooker(engine, config, tracker, sink, packageName),
            MessageDigestHooker(engine, config, tracker, sink, packageName),
            MacHooker(engine, config, tracker, sink, packageName),
            SignatureHooker(engine, config, tracker, sink, packageName),
            KeyParamHooker(engine, config, tracker, sink, packageName),
            KeyGenerationHooker(engine, config, tracker, sink, packageName),
            KeyFactoryHooker(engine, config, tracker, sink, packageName),
            KeyStoreHooker(engine, config, tracker, sink, packageName),
            KeyAgreementHooker(engine, config, tracker, sink, packageName),
            SecureRandomHooker(engine, config, tracker, sink, packageName),
            Base64Hooker(engine, config, tracker, sink, packageName),
            WebViewHooker(engine, config, tracker, sink, packageName),
        )
        for (registrar in registrars) {
            registrar.install()
        }
    }

    /** 注入进程取目标应用 Context(供 Spool 落盘);失败返回 null(不启用 Spool)。 */
    private fun applicationContext(): Context? = runCatching {
        Class.forName("android.app.ActivityThread")
            .getMethod("currentApplication")
            .invoke(null) as? Context
    }.getOrNull()

    private fun parseSwitch(json: String?): HookSwitch? =
        json?.let { runCatching { Gson().fromJson(it, HookSwitch::class.java) }.getOrNull() }
}