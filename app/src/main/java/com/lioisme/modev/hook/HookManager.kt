package com.lioisme.modev.hook

import com.lioisme.modev.core.codec.WireCodec
import com.lioisme.modev.core.model.HookSwitch
import com.lioisme.modev.hook.hooks.Base64Hooker
import com.lioisme.modev.hook.hooks.CipherHooker
import com.lioisme.modev.hook.hooks.KeyFactoryHooker
import com.lioisme.modev.hook.hooks.KeyGenerationHooker
import com.lioisme.modev.hook.hooks.KeyParamHooker
import com.lioisme.modev.hook.hooks.MacHooker
import com.lioisme.modev.hook.hooks.MessageDigestHooker
import com.lioisme.modev.hook.hooks.SignatureHooker
import io.github.libxposed.api.XposedInterface

/**
 * Hook 引擎组装器:每个注入进程一次,装配引擎/跟踪器/通道/出口与八类 Hooker。
 */
object HookManager {

    private lateinit var engine: HookEngine
    private lateinit var tracker: CryptoContextTracker
    private lateinit var channel: ConfigChannel
    private lateinit var sink: RecordSink

    private var installed = false

    /**
     * @param packageName 目标进程名(默认进程即包名;分离进程形如 pkg:sub)
     */
    @Synchronized
    fun install(xposed: XposedInterface, packageName: String, processName: String) {
        if (installed) return
        installed = true

        engine = HookEngine(xposed)
        tracker = CryptoContextTracker()
        channel = ConfigChannel(xposed, packageName, processName) { /* 配置变更即生效 */ }
        sink = RecordSink { record -> channel.sendRecordJson(WireCodec.record(record)) }
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
            Base64Hooker(engine, config, tracker, sink, packageName),
        )
        for (registrar in registrars) {
            registrar.install()
        }
    }
}