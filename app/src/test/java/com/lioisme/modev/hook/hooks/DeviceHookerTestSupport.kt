package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.DeviceEnvRecord
import com.lioisme.modev.core.model.DeviceProfile
import com.lioisme.modev.core.model.FeatureConfig
import com.lioisme.modev.core.model.HookSwitch
import com.lioisme.modev.core.model.MonitoredRecord
import com.lioisme.modev.hook.ConfigWatcher
import com.lioisme.modev.hook.CryptoContextTracker
import com.lioisme.modev.hook.HookEngine
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.MethodHook
import com.lioisme.modev.hook.Platform
import com.lioisme.modev.hook.ProbeContext
import com.lioisme.modev.hook.RecordSink
import java.lang.reflect.Executable
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 设备环境钩子的共用测试夹具(V49 WP-D3 自 `ProfileDeviceValueHookerTest` 抽出)。
 *
 * 三族画像钩子(密度/核数/电池 与 标识/SIM)驱动方式完全相同:假平台捕获注册、假调用现场、
 * 已启动的 sink 收审计。各抄一份就是第三份「静默不生效」的温床(sink 忘 start 那次实锤)。
 */
internal class FakeHookPlatform : Platform {
    val registered = mutableListOf<Pair<Executable?, MethodHook>>()
    override fun register(executable: Executable?, hook: MethodHook): Boolean {
        registered += executable to hook
        return true
    }

    override fun log(message: String, throwable: Throwable?) = Unit
}

/** 可改写返回值的假调用现场:[rewrites] 记录每次 setResult,便于断「零动作」。 */
internal class FakeCall(
    private val args: List<Any?> = emptyList(),
    private var value: Any? = null,
    private var receiver: Any? = null,
) : Invocation() {
    val rewrites = mutableListOf<Any?>()
    override val executable: Executable? get() = null
    override val thisObject: Any? get() = receiver
    override val result: Any? get() = value
    override val argCount: Int get() = args.size
    override fun getArgUnsafe(index: Int): Any? = args[index]
    override val consumed: Boolean get() = rewrites.isNotEmpty()
    override fun setResult(newValue: Any?) {
        value = newValue
        rewrites += newValue
    }
}

internal class DeviceHookerHarness {
    val platform = FakeHookPlatform()
    val records = CopyOnWriteArrayList<MonitoredRecord>()

    fun context(switch: HookSwitch) = ProbeContext(
        engine = HookEngine(platform),
        config = { switch },
        configWatcher = ConfigWatcher(),
        tracker = CryptoContextTracker,
        // 必须 start:否则 `emit` 首句 `if (!running) return` 静默丢记录,审计断言恒绿在空列表上。
        sink = RecordSink(
            jsonOf = { record -> records += record; "" },
            sendJson = { true },
            isConnected = { false },
        ).also { it.start() },
        packageName = "com.gate.test",
    )

    fun switchWith(
        profile: DeviceProfile,
        tweak: (HookSwitch) -> HookSwitch = { it },
    ): HookSwitch = tweak(HookSwitch()).copy(feature = FeatureConfig(deviceProfile = profile))

    /** 按「类#方法」取回调(签名键与 `SharedMethodBroadcast` 同源)。 */
    fun hookOf(className: String, methodName: String): MethodHook {
        val matches = platform.registered.filter {
            it.first?.declaringClass?.name == className && it.first?.name == methodName
        }
        check(matches.isNotEmpty()) { "no hook registered on $className#$methodName" }
        return matches.first().second
    }

    fun hasHook(className: String, methodName: String): Boolean = platform.registered.any {
        it.first?.declaringClass?.name == className && it.first?.name == methodName
    }

    /** 记录出口在工作线程上异步回调 ⇒ 断言前先等(否则「0 条」断言恒真)。 */
    fun awaitAudit(methodName: String, timeoutMs: Long = 3_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (audits(methodName).isEmpty() && System.currentTimeMillis() < deadline) {
            runCatching { Thread.sleep(20) }
        }
    }

    fun audits(methodName: String): List<DeviceEnvRecord> =
        records.filterIsInstance<DeviceEnvRecord>().filter { it.targetProperty == methodName }

    fun settle(ms: Long = 200) = runCatching { Thread.sleep(ms) }
}
