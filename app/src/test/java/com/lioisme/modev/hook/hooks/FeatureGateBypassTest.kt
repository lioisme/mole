package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.HookSwitch
import com.lioisme.modev.core.model.ProcessKind
import com.lioisme.modev.core.model.UiInteractionKind
import com.lioisme.modev.hook.CryptoContextTracker
import com.lioisme.modev.hook.ConfigWatcher
import com.lioisme.modev.hook.HookEngine
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.MethodHook
import com.lioisme.modev.hook.Platform
import com.lioisme.modev.hook.ProbeContext
import com.lioisme.modev.hook.RecordSink
import java.lang.reflect.Executable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V37 T0.1/T0.2 FeatureGate 红线回归:干预动作本身(非仅记录)必须经
 * master ∧ kind 双门控——开关关闭时 setResult 等干预一律不得发生。
 * 覆盖审计实锤三缺陷:退出拦截裸位旁路、弹窗关键词无开关旁路、Build 静态字段无条件篡改。
 */
class FeatureGateBypassTest {

    private class FakePlatform : Platform {
        val hooks = mutableMapOf<Executable, MutableList<MethodHook>>()
        override fun register(executable: Executable?, hook: MethodHook): Boolean {
            if (executable == null) return false
            hooks.getOrPut(executable) { mutableListOf() }.add(hook)
            return true
        }
        override fun log(message: String, throwable: Throwable?) = Unit
    }

    private class FakeInvocation(
        override val executable: Executable? = null,
        override val thisObject: Any? = null,
        override val result: Any? = null,
        private val args: List<Any?> = emptyList(),
    ) : Invocation() {
        var setResultCalls = 0
        override val argCount: Int get() = args.size
        override fun getArgUnsafe(index: Int): Any? = args[index]
        override fun setResult(value: Any?) {
            setResultCalls++
        }
    }

    private fun testSink() = RecordSink(
        jsonOf = { "" },
        sendJson = { true },
        isConnected = { false },
    )

    private fun testContext(
        platform: FakePlatform,
        watcher: ConfigWatcher = ConfigWatcher(),
        config: () -> HookSwitch,
    ) = ProbeContext(
        engine = HookEngine(platform),
        config = config,
        configWatcher = watcher,
        tracker = CryptoContextTracker,
        sink = testSink(),
        packageName = "com.gate.test",
    )

    private val systemExit by lazy {
        System::class.java.getMethod("exit", Int::class.javaPrimitiveType)
    }

    // ── T0.1:退出拦截 master ∧ kind 双门控 ─────────────────────────────

    @Test
    fun `exit intercept blocks when master and kind both on`() {
        val platform = FakePlatform()
        val hooker = ExitInterceptHooker(
            testContext(platform) { HookSwitch(processControlListen = true, exitIntercept = true) },
        )
        hooker.install()
        val inv = FakeInvocation(executable = systemExit, args = listOf(0))
        platform.hooks.getValue(systemExit).forEach { it.before(inv) }
        assertEquals(1, inv.setResultCalls)
    }

    @Test
    fun `exit intercept allows when master off`() {
        val platform = FakePlatform()
        val hooker = ExitInterceptHooker(
            testContext(platform) { HookSwitch(processControlListen = false, exitIntercept = true) },
        )
        hooker.install()
        val inv = FakeInvocation(executable = systemExit, args = listOf(0))
        platform.hooks.getValue(systemExit).forEach { it.before(inv) }
        assertEquals("master=false 时干预必须停用", 0, inv.setResultCalls)
    }

    @Test
    fun `exit intercept allows when kind off`() {
        val platform = FakePlatform()
        val hooker = ExitInterceptHooker(
            testContext(platform) { HookSwitch(processControlListen = true, exitIntercept = false) },
        )
        hooker.install()
        val inv = FakeInvocation(executable = systemExit, args = listOf(0))
        platform.hooks.getValue(systemExit).forEach { it.before(inv) }
        assertEquals(0, inv.setResultCalls)
    }

    // ── T0.1:弹窗关键词拦截闸门(纯函数直测) ──────────────────────────

    @Test
    fun `dialog keyword intercept allows when master off`() {
        val switch = HookSwitch(
            uiListen = false,
            dialogKeyword = true,
            feature = com.lioisme.modev.core.model.FeatureConfig(dialogKeywords = listOf("风险")),
        )
        assertTrue(
            DialogKeywordInterceptHooker.matchedKeywords(switch, "风险提示", null).isEmpty(),
        )
    }

    @Test
    fun `dialog keyword intercept allows when kind off`() {
        val switch = HookSwitch(
            uiListen = true,
            dialogKeyword = false,
            feature = com.lioisme.modev.core.model.FeatureConfig(dialogKeywords = listOf("风险")),
        )
        assertTrue(DialogKeywordInterceptHooker.matchedKeywords(switch, "风险提示", null).isEmpty())
    }

    @Test
    fun `dialog keyword intercept allows when keyword list empty`() {
        val switch = HookSwitch(uiListen = true, dialogKeyword = true)
        assertTrue(DialogKeywordInterceptHooker.matchedKeywords(switch, "任意标题", "任意消息").isEmpty())
    }

    @Test
    fun `dialog keyword intercept matches case-insensitively when fully on`() {
        val switch = HookSwitch(
            uiListen = true,
            dialogKeyword = true,
            feature = com.lioisme.modev.core.model.FeatureConfig(dialogKeywords = listOf("DETECT", "root")),
        )
        val matched = DialogKeywordInterceptHooker.matchedKeywords(switch, "Detecting...", "device is ROOTED")
        assertEquals(listOf("DETECT", "root"), matched)
    }

    @Test
    fun `blank keyword never matches`() {
        val switch = HookSwitch(
            uiListen = true,
            dialogKeyword = true,
            feature = com.lioisme.modev.core.model.FeatureConfig(dialogKeywords = listOf("")),
        )
        assertTrue(DialogKeywordInterceptHooker.matchedKeywords(switch, "标题", "消息").isEmpty())
    }

    @Test
    fun `regex rule matches via structured keyword rules`() {
        val switch = HookSwitch(
            uiListen = true,
            dialogKeyword = true,
            feature = com.lioisme.modev.core.model.FeatureConfig(
                dialogKeywordRules = listOf(
                    com.lioisme.modev.core.model.KeywordRule(
                        "bootloader.*(unlocked|critical)",
                        com.lioisme.modev.core.model.KeywordMatchMode.REGEX,
                    ),
                ),
            ),
        )
        assertEquals(
            listOf("bootloader.*(unlocked|critical)"),
            DialogKeywordInterceptHooker.matchedKeywords(switch, "Bootloader is UNLOCKED", null),
        )
        assertTrue(
            DialogKeywordInterceptHooker.matchedKeywords(switch, "bootloader status", null).isEmpty(),
        )
    }

    // ── T0.2:Build 静态字段改写门控 + 配置变更惰性补写 ─────────────────

    @Test
    fun `build field masking not attempted when master off`() {
        val platform = FakePlatform()
        val watcher = ConfigWatcher()
        var switch = HookSwitch(deviceEnvListen = false, emulatorBuild = true)
        val hooker = EmulatorBuildHooker(testContext(platform, watcher) { switch })
        hooker.install()
        assertFalse("master=false 时不得尝试篡改 Build 字段", hooker.fieldsMasked.get())
    }

    @Test
    fun `build field masking not attempted when kind off`() {
        val platform = FakePlatform()
        val watcher = ConfigWatcher()
        var switch = HookSwitch(deviceEnvListen = true, emulatorBuild = false)
        val hooker = EmulatorBuildHooker(testContext(platform, watcher) { switch })
        hooker.install()
        assertFalse(hooker.fieldsMasked.get())
    }

    @Test
    fun `build field masking deferred until config enables it`() {
        val platform = FakePlatform()
        val watcher = ConfigWatcher()
        var switch = HookSwitch(deviceEnvListen = false, emulatorBuild = false)
        val hooker = EmulatorBuildHooker(testContext(platform, watcher) { switch })
        hooker.install()
        assertFalse(hooker.fieldsMasked.get())
        // 配置变更推送:开关打开 → 监听器惰性补写(幂等一次性)
        switch = HookSwitch(deviceEnvListen = true, emulatorBuild = true)
        watcher.notify(switch)
        assertTrue("配置开启后应一次性补写", hooker.fieldsMasked.get())
        // 再次通知不重复执行(CAS 幂等)
        watcher.notify(switch)
        assertTrue(hooker.fieldsMasked.get())
    }

    @Test
    fun `build field masking applied eagerly when already enabled`() {
        val platform = FakePlatform()
        val hooker = EmulatorBuildHooker(
            testContext(platform) { HookSwitch(deviceEnvListen = true, emulatorBuild = true) },
        )
        hooker.install()
        assertTrue(hooker.fieldsMasked.get())
    }

    // ── T0.4:干预集合由域标注派生,缺口类别纳入 ────────────────────────

    @Test
    fun `intervention gate covers previously missing kinds`() {
        // EMULATOR_SENSOR 与 DIALOG_KEYWORD 属功能域(V34 手工清单曾漏)
        val featureIds = com.lioisme.modev.core.model.CategoryTaxonomy
            .forDomain(com.lioisme.modev.core.model.CapabilityDomain.FEATURE)
            .map { it.id }
            .toSet()
        assertTrue(featureIds.contains(com.lioisme.modev.core.model.DeviceEnvKind.EMULATOR_SENSOR.name))
        assertTrue(featureIds.contains(UiInteractionKind.DIALOG_KEYWORD.name))
        assertTrue(featureIds.contains(ProcessKind.EXIT_INTERCEPT.name))
    }
}
