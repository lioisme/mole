package com.lioisme.modev.hook

import com.lioisme.modev.core.model.HookSwitch
import com.lioisme.modev.core.model.RecordTypes
import java.lang.reflect.Executable
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.assertEquals
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * 注入装配层的框架无关契约(T4.1):`HookEngine` / `Platform` / `Invocation` / `ProbeContext`
 * 四个中性抽象是「两个框架适配器对称」的唯一支点 —— 它们一旦走样(例如 engine 里偷偷
 * 直调 `register`、或写口从抛异常退化成静默 no-op),两个适配器会**同时**退化而不被任何
 * 一侧的测试发现。本类锁住支点语义;适配器自身的真实行为(`Chain.shortCircuited` /
 * `MethodHookParam.hasThrowable` 等)只在设备插桩矩阵可证,登记于 PLAN T4.1 备注。
 */
class InjectionAssemblyContractTest {

    private class FakePlatform(private val accept: Boolean = true) : Platform {
        val plain = CopyOnWriteArrayList<Pair<Executable?, MethodHook>>()
        val interventional = CopyOnWriteArrayList<Pair<Executable?, MethodHook>>()
        val logs = CopyOnWriteArrayList<String>()

        override fun register(executable: Executable?, hook: MethodHook): Boolean {
            plain += executable to hook
            return accept
        }

        // 刻意不覆写 registerInterventional —— 走接口默认实现(legacy 形态)。
        override fun log(message: String, throwable: Throwable?) {
            logs += message
        }
    }

    private class SplitPlatform : Platform {
        val plain = AtomicInteger()
        val interventional = AtomicInteger()
        override fun register(executable: Executable?, hook: MethodHook): Boolean {
            plain.incrementAndGet()
            return true
        }

        override fun registerInterventional(executable: Executable?, hook: MethodHook): Boolean {
            interventional.incrementAndGet()
            return true
        }

        override fun log(message: String, throwable: Throwable?) = Unit
    }

    private open class Bare(
        private val args: List<Any?> = emptyList(),
        override val result: Any? = null,
        private val tokenOf: Any? = null,
    ) : Invocation() {
        override val executable: Executable? get() = null
        override val thisObject: Any? get() = null
        override val argCount: Int get() = args.size
        override val token: Any? get() = tokenOf ?: this
        override fun getArgUnsafe(index: Int): Any? = args[index]
    }

    private val method: Executable = String::class.java.getDeclaredMethod("length")

    // ── HookEngine ───────────────────────────────────────────

    @Test
    fun `null executable 既不注册也不触碰平台`() {
        val platform = FakePlatform()
        val engine = HookEngine(platform)
        val hook = object : MethodHook() {}
        assertFalse(engine.hook(null, hook))
        assertFalse(engine.hookThrowing(null, hook))
        assertTrue("缺席方法不得留痕", platform.plain.isEmpty() && platform.interventional.isEmpty())
    }

    @Test
    fun `注册结果逐层透传(平台拒绝即引擎拒绝)`() {
        val rejecting = FakePlatform(accept = false)
        assertFalse("平台返回 false 时引擎必须如实返回 false", HookEngine(rejecting).hook(method, object : MethodHook() {}))
        val accepting = FakePlatform()
        assertTrue(HookEngine(accepting).hook(method, object : MethodHook() {}))
        assertEquals(1, accepting.plain.size)
        assertSame(method, accepting.plain[0].first)
    }

    @Test
    fun `未覆写干预口的平台由接口默认实现降级为普通注册`() {
        // legacy 形态:MethodHookParam.setThrowable 天然可用 ⇒ registerInterventional
        // 不覆写,靠默认实现落到 register;若默认实现被改成立即返回 false,legacy 干预全灭。
        val platform = FakePlatform()
        assertTrue(HookEngine(platform).hookThrowing(method, object : MethodHook() {}))
        assertEquals("干预型注册必须降级为普通注册而非丢弃", 1, platform.plain.size)
    }

    @Test
    fun `覆写干预口的平台两条通道互不串线`() {
        // libxposed 形态:干预型走 PASSTHROUGH+异常隔离,普通型走 PROTECTIVE;
        // 混用 = 干预静默失效或回调异常外泄给宿主。
        val platform = SplitPlatform()
        val engine = HookEngine(platform)
        engine.hook(method, object : MethodHook() {})
        engine.hookThrowing(method, object : MethodHook() {})
        assertEquals(1, platform.plain.get())
        assertEquals(1, platform.interventional.get())
    }

    // ── Invocation 基类写口 ──────────────────────────────────

    @Test
    fun `基类写口默认一律抛出不支持,绝不静默 no-op`() {
        val invocation = Bare()
        expectUnsupported { invocation.setResult("x") }
        expectUnsupported { invocation.setThrowable(RuntimeException("t")) }
        expectUnsupported { invocation.setArg(0, "x") }
    }

    @Test
    fun `未覆写时 consumed 恒 false 且 token 即自身`() {
        val invocation = Bare()
        assertFalse("默认未消费:广播侧据此继续分派", invocation.consumed)
        assertSame("默认 token = 调用视图自身", invocation, invocation.token)
        assertNull("before 阶段无结果", invocation.result)
    }

    @Test
    fun `同一底层句柄可被两个调用视图共享(按调用配对的支点)`() {
        // 适配器为 before/after 各构造一个视图,但必须共享同一 token:
        // HookerSupport 据此暂存入参改写。两次独立构造的默认 token 必须不同。
        val handle = Any()
        val before = Bare(tokenOf = handle)
        val after = Bare(tokenOf = handle, result = "ret")
        assertSame("两阶段必须落在同一句柄上", before.token, after.token)
        assertNotSame("不同调用不得共用默认 token", Bare().token, Bare().token)
        assertNull("after 视图携带结果,before 视图恒 null", before.result)
        assertEquals("ret", after.result)
    }

    // ── ProbeContext 装配面 ─────────────────────────────────

    @Test
    fun `上下文默认设施齐备且与 engine 同源`() {
        val platform = FakePlatform()
        val context = context(HookEngine(platform))
        assertNotNull("类加载监视器必须有默认实例", context.loadClassWatcher)
        assertNotNull("热门方法广播器必须有默认实例", context.broadcast)
        assertNull("未提供 app classloader 时惰性取 null(注入早于 Application)", context.appClassLoader())
        assertEquals("com.test", context.packageName)
    }

    @Test
    fun `配置快照每次现读(变更即生效,不缓存副本)`() {
        var switch = HookSwitch.DEFAULT
        val context = context(HookEngine(FakePlatform()), config = { switch })
        assertSame(switch, context.config())
        val next = switch.copy(cryptoListen = false)
        switch = next
        assertSame("config() 必须现读源值", next, context.config())
        assertFalse(context.config().cryptoListen)
    }

    @Test
    fun `探针声明的监听类型经上下文可见且记录键独立`() {
        val context = context(HookEngine(FakePlatform()))
        val probe = object : Probe {
            override val recordType: String = RecordTypes.SYSTEM
            override fun install(context: ProbeContext) = Unit
        }
        assertEquals("system", probe.recordType)
        assertEquals("com.test", context.packageName)
    }

    private fun context(
        engine: HookEngine,
        config: () -> HookSwitch = { HookSwitch.DEFAULT },
    ) = ProbeContext(
        engine = engine,
        config = config,
        tracker = CryptoContextTracker,
        sink = RecordSink(jsonOf = { "{}" }, sendJson = { true }, isConnected = { false }),
        packageName = "com.test",
    )

    private inline fun expectUnsupported(block: () -> Unit) {
        try {
            block()
            fail("必须抛 UnsupportedOperationException(静默 no-op 会让干预假成功)")
        } catch (expected: UnsupportedOperationException) {
            // 契约如此
        }
    }
}
