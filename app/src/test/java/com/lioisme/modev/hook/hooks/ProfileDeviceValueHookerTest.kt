package com.lioisme.modev.hook.hooks

import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.os.BatteryManager
import android.util.DisplayMetrics
import com.lioisme.modev.core.model.DeviceEnvRecord
import com.lioisme.modev.core.model.DeviceProfile
import com.lioisme.modev.core.model.FeatureConfig
import com.lioisme.modev.core.model.HookSwitch
import com.lioisme.modev.core.model.MonitoredRecord
import com.lioisme.modev.core.model.activeSpoofProfile
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 画像强制档 · 密度 / 核数 / 电池三族的行为回归。
 *
 * 三族同一机制(纯计划函数 + 广播订阅 + 门控),所以一个测试类分三段,不再抄三份夹具。
 * 与被测语义分开钉的两层:
 *  1. **计划函数**:未填 ⇒ null(默认开闸的零副作用前提)、界取自 catalog 而非本地字面量;
 *  2. **回调落地**:经真实 [com.lioisme.modev.hook.SharedMethodBroadcast] 分派,门控关 ⇒ 零
 *     setResult;两档共享同一读口时**全进程只注册一次**(AGENTS「同一方法的第二个 hook
 *     不执行」的形态在此变成可断言的红线,而不是靠人记住装钩顺序)。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProfileDeviceValueHookerTest {

    private class FakePlatform : Platform {
        val registered = mutableListOf<Pair<Executable?, MethodHook>>()
        override fun register(executable: Executable?, hook: MethodHook): Boolean {
            registered += executable to hook
            return true
        }

        override fun log(message: String, throwable: Throwable?) = Unit
    }

    private class FakeCall(
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

    /**
     * 记录捕获必须**启动** sink:未启动时 `emit` 首句 `if (!running) return` 直接丢弃,
     * 断言会恒绿在空列表上(V49 D2 实测抓到,同类夹具不启动 = 审计断言全是空的)。
     * 出口在工作线程上异步回调,所以取记录用有界等待,不靠 sleep 猜。
     */
    private val records = CopyOnWriteArrayList<MonitoredRecord>()
    private val system = Resources.getSystem()
    private val originalDpi = system.displayMetrics.densityDpi
    private val originalConfigDpi = system.configuration.densityDpi
    private val originalScreenWidthDp = system.configuration.screenWidthDp

    private fun awaitAudited(target: String) {
        val deadline = System.currentTimeMillis() + 3_000
        while (audited(target).isEmpty() && System.currentTimeMillis() < deadline) {
            runCatching { Thread.sleep(20) }
        }
    }

    /** 按读口取审计记录(安装期改写与实例读口各有各的 targetProperty,不靠时序猜)。 */
    private fun audited(target: String): List<DeviceEnvRecord> =
        records.filterIsInstance<DeviceEnvRecord>().filter { it.targetProperty == target }

    @After
    fun restoreSystemResources() {
        // 本类会就地改写 `Resources.getSystem` 的缓存实例,不还原就是跨用例污染。
        system.displayMetrics.densityDpi = originalDpi
        system.displayMetrics.density = originalDpi / 160f
        system.displayMetrics.scaledDensity = system.displayMetrics.density
        system.configuration.densityDpi = originalConfigDpi
        system.configuration.screenWidthDp = originalScreenWidthDp
    }

    private fun context(switch: HookSwitch, platform: FakePlatform = FakePlatform()) = ProbeContext(
        engine = HookEngine(platform),
        config = { switch },
        configWatcher = ConfigWatcher(),
        tracker = CryptoContextTracker,
        sink = RecordSink(
            jsonOf = { record -> records += record; "" },
            sendJson = { true },
            isConnected = { false },
        ).also { it.start() },
        packageName = "com.gate.test",
    ) to platform

    private fun switchWith(
        profile: DeviceProfile,
        tweak: (HookSwitch) -> HookSwitch = { it },
    ) = tweak(HookSwitch()).copy(feature = FeatureConfig(deviceProfile = profile))

    /** 取某个方法名下的全部已注册回调(名称即读口,不需再抄一份索引表)。 */
    private fun hooksFor(platform: FakePlatform, methodName: String): List<MethodHook> =
        platform.registered.filter { it.first?.name == methodName }.map { it.second }

    // ── 第一层:计划函数 ────────────────────────────────────────────

    @Test
    fun emptyProfilePlansNothingForAllThreeFamilies() {
        val empty = DeviceProfile.EMPTY
        assertEquals("密度未填 ⇒ 一处都不改", null, ProfileDensityHooker.targetDpi(empty))
        assertEquals("核数未填 ⇒ 一次 setResult 都不发", null, ProfileCpuHooker.targetCores(empty))
        assertEquals("电量未填 ⇒ 两个读口都不碰", null, ProfileBatteryHooker.targetLevel(empty))
    }

    @Test
    fun planFunctions_takeTheirBoundsFromTheCatalogNotFromLiterals() {
        // 越界值存不进画像库,但 wire 快照是系统边界 ⇒ 注入侧必须自己拒(与校验同一张界表)。
        assertEquals(null, ProfileDensityHooker.targetDpi(DeviceProfile(densityDpi = 1001)))
        assertEquals(null, ProfileDensityHooker.targetDpi(DeviceProfile(densityDpi = 71)))
        assertEquals(null, ProfileCpuHooker.targetCores(DeviceProfile(cpuCores = 0)))
        assertEquals(null, ProfileCpuHooker.targetCores(DeviceProfile(cpuCores = 65)))
        assertEquals(null, ProfileBatteryHooker.targetLevel(DeviceProfile(batteryLevel = 101)))
        assertEquals(420, ProfileDensityHooker.targetDpi(DeviceProfile(densityDpi = 420)))
        assertEquals(4, ProfileCpuHooker.targetCores(DeviceProfile(cpuCores = 4)))
        // 0 是合法意图(「电量耗尽」),判据是「填没填」而不是「大于 0」。
        assertEquals(0, ProfileBatteryHooker.targetLevel(DeviceProfile(batteryLevel = 0)))
    }

    // ── 第二层:回调落地 ────────────────────────────────────────────

    @Test
    fun profileFamilies_shareOneRegistrationPerPlatformMethod() {
        val (ctx, platform) = context(
            switchWith(DeviceProfile(densityDpi = 420, cpuCores = 4, batteryLevel = 66), tweak = { it.copy(emulatorCpu = true, emulatorBattery = true) }),
        )
        ProfileDensityHooker(ctx).install()
        ProfileCpuHooker(ctx).install()
        ProfileBatteryHooker(ctx).install()
        DeviceCpuHooker(ctx).install()
        DeviceBatteryHooker(ctx).install()

        val keys = platform.registered.map { "${it.first?.declaringClass?.name}#${it.first?.name}" }
        assertEquals("每个读口恒一次注册(二次注册在 Vector 上静默失声):\n$keys", keys.size, keys.toSet().size)
        assertTrue("availableProcessors 必须被装钩", keys.any { it.endsWith("#availableProcessors") })
        assertTrue("getIntProperty 必须被装钩", keys.any { it.endsWith("#getIntProperty") })
    }

    @Test
    fun density_rewritesEveryMetricsInstanceAndAuditsOnce() {
        val profile = DeviceProfile(densityDpi = 420)
        val (ctx, platform) = context(switchWith(profile))
        ProfileDensityHooker(ctx).install()

        val hook = hooksFor(platform, "getDisplayMetrics").single()
        val metrics = DisplayMetrics().apply {
            densityDpi = 480
            density = 3f
            scaledDensity = 3.3f
        }
        hook.after(FakeCall(value = metrics))
        assertEquals(420, metrics.densityDpi)
        assertEquals(2.625f, metrics.density, 0.0001f)
        assertEquals("字体缩放比必须保留(3.3/3.0)", 2.625f * 1.1f, metrics.scaledDensity, 0.0001f)

        hook.after(FakeCall(value = metrics))
        hook.after(FakeCall(value = metrics))
        awaitAudited("getDisplayMetrics")
        assertEquals(
            "热路径读口按「读口+值」去重留痕,不得每次调用记一条",
            1,
            audited("getDisplayMetrics").size,
        )
        assertEquals("420", audited("getDisplayMetrics").single().maskedValue)
    }

    @Test
    fun density_configurationRewriteIsIdempotent_soDpNeverShrinksTwice() {
        val configuration = Configuration().apply {
            densityDpi = 480
            screenWidthDp = 411
            screenHeightDp = 891
            smallestScreenWidthDp = 411
        }
        ProfileDensityHooker.applyDensity(configuration, 320)
        val once = Triple(configuration.screenWidthDp, configuration.screenHeightDp, configuration.densityDpi)
        repeat(5) { ProfileDensityHooker.applyDensity(configuration, 320) }
        assertEquals("重复改写必须恒等(否则每帧都在缩 dp)", once, Triple(configuration.screenWidthDp, configuration.screenHeightDp, configuration.densityDpi))
        assertEquals(320, configuration.densityDpi)
    }

    @Test
    fun density_masterOffLeavesSystemResourcesAlone() {
        val before = Resources.getSystem().displayMetrics.densityDpi
        val (ctx, _) = context(
            switchWith(DeviceProfile(densityDpi = 420), tweak = { it.copy(deviceEnvListen = false) }),
        )
        ProfileDensityHooker(ctx).install()
        assertEquals("master=false ⇒ 安装期零副作用", before, Resources.getSystem().displayMetrics.densityDpi)
        assertTrue(records.isEmpty())
    }

    @Test
    fun cpu_forcesTheProfileValueEvenWhenRealHasFewerCores() {
        val (ctx, platform) = context(switchWith(DeviceProfile(cpuCores = 4)))
        ProfileCpuHooker(ctx).install()
        val hook = hooksFor(platform, "availableProcessors").single()
        // 条件档的判据是 current > 目标才压平;真机 2 核伪装成 4 核必须仍改到 4。
        val call = FakeCall(value = 2)
        hook.after(call)
        assertEquals(listOf<Any>(4), call.rewrites)
        awaitAudited(ProfileCpuHooker.TARGET_PROPERTY)
        assertEquals(1, audited(ProfileCpuHooker.TARGET_PROPERTY).size)
    }

    @Test
    fun cpu_conditionalTierYieldsToTheProfileValue() {
        val profile = DeviceProfile(cpuCores = 4)
        val (ctx, platform) = context(switchWith(profile, tweak = { it.copy(emulatorCpu = true) }))
        DeviceCpuHooker(ctx).install()
        val hook = hooksFor(platform, "availableProcessors").single()

        val filled = FakeCall(value = 8)
        hook.after(filled)
        assertTrue("画像已填核数 ⇒ 条件档让位(否则两档先后顺序决定结果)", filled.rewrites.isEmpty())

        val (ctx2, platform2) = context(
            switchWith(DeviceProfile.EMPTY, tweak = { it.copy(emulatorCpu = true) }),
        )
        DeviceCpuHooker(ctx2).install()
        // 条件档的值源仍是遗留档(D6x 才退役),所以期望值从那里读,不抄字面量。
        val legacy = HookSwitch().activeSpoofProfile().cpuCores
        val empty = FakeCall(value = legacy + 4)
        hooksFor(platform2, "availableProcessors").single().after(empty)
        assertEquals("画像没填时条件档照旧压平", listOf<Any>(legacy), empty.rewrites)
    }

    @Test
    fun battery_touchesOnlyCapacityAndTheTwoBatteryExtras() {
        val (ctx, platform) = context(switchWith(DeviceProfile(batteryLevel = 66)))
        ProfileBatteryHooker(ctx).install()

        val property = hooksFor(platform, "getIntProperty").single()
        val capacity = FakeCall(args = listOf(BatteryManager.BATTERY_PROPERTY_CAPACITY), value = 100)
        property.after(capacity)
        assertEquals(listOf<Any>(66), capacity.rewrites)
        val chargeCounter = FakeCall(args = listOf(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER), value = 1)
        property.after(chargeCounter)
        assertTrue("计数器/电流不归画像族(键空间不相交,条件档继续负责)", chargeCounter.rewrites.isEmpty())

        val extra = hooksFor(platform, "getIntExtra").single()
        val level = FakeCall(args = listOf(BatteryManager.EXTRA_LEVEL, 0), value = 100)
        extra.after(level)
        assertEquals(listOf<Any>(66), level.rewrites)
        val scale = FakeCall(args = listOf(BatteryManager.EXTRA_SCALE, 0), value = 1)
        extra.after(scale)
        assertEquals("EXTRA_SCALE 恒 100(画像电量即百分比)", listOf<Any>(100), scale.rewrites)
        val unrelated = FakeCall(args = listOf("com.android.server.wifi.scoring", 0), value = 7)
        extra.after(unrelated)
        assertTrue("极热读口在非电池键上必须零动作", unrelated.rewrites.isEmpty())
    }

    @Test
    fun battery_conditionalTierKeepsOtherPropertiesWhenProfileFillsCapacity() {
        val (ctx, platform) = context(
            switchWith(DeviceProfile(batteryLevel = 66), tweak = { it.copy(emulatorBattery = true) }),
        )
        DeviceBatteryHooker(ctx).install()
        val property = hooksFor(platform, "getIntProperty").single()

        val capacity = FakeCall(args = listOf(BatteryManager.BATTERY_PROPERTY_CAPACITY), value = 100)
        property.after(capacity)
        assertTrue("电量百分比让位给画像(67 那个硬编码常量不得再赢)", capacity.rewrites.isEmpty())

        val status = FakeCall(args = listOf(5), value = 2)
        property.after(status)
        assertEquals("充电状态仍由条件档处理", listOf<Any>(3), status.rewrites)
    }

    @Test
    fun kindOffMeansZeroRewritesForItsOwnFamilyOnly() {
        val profile = DeviceProfile(densityDpi = 420, cpuCores = 4, batteryLevel = 66)
        val (ctx, platform) = context(switchWith(profile, tweak = { it.copy(profileCpu = false) }))
        ProfileCpuHooker(ctx).install()
        ProfileBatteryHooker(ctx).install()

        val cores = FakeCall(value = 2)
        hooksFor(platform, "availableProcessors").single().after(cores)
        assertTrue("kind 关 ⇒ 该族零 setResult", cores.rewrites.isEmpty())

        val level = FakeCall(args = listOf(BatteryManager.EXTRA_LEVEL, 0), value = 100)
        hooksFor(platform, "getIntExtra").single().after(level)
        assertEquals("相邻族不受牵连", listOf<Any>(66), level.rewrites)
    }
}
