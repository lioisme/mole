package com.lioisme.modev.hook

import java.lang.reflect.Executable
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V43 T2/T3 单一注册点广播分派回归(V2.1/V2.9/D-4):
 * 同一框架方法全进程仅注册一个钩子;订阅者按序分派、短路即止、异常隔离。
 */
class SharedHookDispatchTest {

    private class FakePlatform : Platform {
        val registered = CopyOnWriteArrayList<Pair<Executable?, MethodHook>>()
        val interventionalKeys = CopyOnWriteArrayList<String>()
        override fun register(executable: Executable?, hook: MethodHook): Boolean {
            registered += executable to hook
            return true
        }

        override fun registerInterventional(executable: Executable?, hook: MethodHook): Boolean {
            interventionalKeys += "${executable?.declaringClass?.name}#${executable?.name}"
            registered += executable to hook
            return true
        }

        override fun log(message: String, throwable: Throwable?) = Unit
    }

    private open class Inv(
        private val args: List<Any?>,
        override val result: Any? = null,
        override val token: Any? = Object(),
    ) : Invocation() {
        override val executable: Executable? get() = null
        override val thisObject: Any? get() = null
        override val argCount: Int get() = args.size
        override fun getArgUnsafe(index: Int): Any? = args[index]
        var replaced = false
        var thrown: Throwable? = null
        override val consumed: Boolean get() = replaced || thrown != null
        override fun setResult(value: Any?) { replaced = true }
        override fun setThrowable(t: Throwable) { thrown = t }
    }

    // ── SharedMethodBroadcast ────────────────────────────────

    @Test
    fun `same method via distinct reflective objects shares one registration`() {
        val platform = FakePlatform()
        val bus = SharedMethodBroadcast(HookEngine(platform))
        // 两次 getMethod 可能返回不同实例——签名键必须仍去重
        val m1 = String::class.java.getMethod("length")
        val m2 = String::class.java.getMethod("length")
        val seen = CopyOnWriteArrayList<String>()
        bus.subscribe(m1, object : SharedMethodBroadcast.Sub {
            override fun after(invocation: Invocation, result: Any?) { seen += "a" }
        })
        bus.subscribe(m2, object : SharedMethodBroadcast.Sub {
            override fun after(invocation: Invocation, result: Any?) { seen += "b" }
        })
        assertEquals(1, bus.registeredCount)
        assertEquals(1, platform.registered.size)
        assertTrue("必须干预型注册(setThrowable 可达调用方)", platform.interventionalKeys.isNotEmpty())
        platform.registered.single().second.after(Inv(emptyList()))
        assertEquals(listOf("a", "b"), seen)
    }

    @Test
    fun `before chain stops at first consumer`() {
        val platform = FakePlatform()
        val bus = SharedMethodBroadcast(HookEngine(platform))
        val calls = CopyOnWriteArrayList<String>()
        bus.subscribe(
            String::class.java.getMethod("length"),
            object : SharedMethodBroadcast.Sub {
                override fun before(invocation: Invocation) {
                    calls += "first"
                    invocation.setResult("x") // consumed:后续订阅者不得再改
                }
            },
        )
        bus.subscribe(
            String::class.java.getMethod("length"),
            object : SharedMethodBroadcast.Sub {
                override fun before(invocation: Invocation) { calls += "second" }
            },
        )
        platform.registered.single().second.before(Inv(emptyList()))
        assertEquals(listOf("first"), calls)
    }

    @Test
    fun `after subscribers all receive updated result after prior replace`() {
        val platform = FakePlatform()
        val bus = SharedMethodBroadcast(HookEngine(platform))
        val results = CopyOnWriteArrayList<Any?>()
        bus.subscribe(
            Integer::class.java.getMethod("intValue"),
            object : SharedMethodBroadcast.Sub {
                override fun after(invocation: Invocation, result: Any?) {
                    results += result
                    invocation.setResult("replaced")
                }
            },
        )
        bus.subscribe(
            Integer::class.java.getMethod("intValue"),
            object : SharedMethodBroadcast.Sub {
                override fun after(invocation: Invocation, result: Any?) { results += result }
            },
        )
        val hook = platform.registered.single().second
        // 同一 after 视图:前序 setResult 对后续可见(Invocation.result 实时读)
        val live = object : Inv(emptyList()) {
            private var current: Any? = 42
            override val result: Any? get() = current
            override fun setResult(value: Any?) {
                current = value
                replaced = true
            }
        }
        hook.after(live)
        assertEquals(listOf(42, "replaced"), results)
    }

    // ── LoadClassWatcher ─────────────────────────────────────

    @Test
    fun `watcher registers once and broadcasts to every subscriber with isolation`() {
        val platform = FakePlatform()
        val watcher = LoadClassWatcher(HookEngine(platform))
        val hits1 = CopyOnWriteArrayList<String>()
        val hits2 = CopyOnWriteArrayList<String>()
        watcher.observeAfter { name, _ -> hits1 += name }
        watcher.observeAfter { name, _ -> hits2 += name }
        // 第三次订阅不得再注册
        watcher.observeAfter { _, _ -> }
        // JVM 无 dalvik BaseDexClassLoader → 仅 loadClass 一注册
        assertEquals("loadClass/findClass 合计注册次数", 1, platform.registered.size)
        val hook = platform.registered.single().second
        hook.after(Inv(listOf("com.example.Foo")))
        assertEquals(listOf("com.example.Foo"), hits1)
        assertEquals(listOf("com.example.Foo"), hits2)
    }

    @Test
    fun `before subscriber throwing does not break other subscribers`() {
        val platform = FakePlatform()
        val watcher = LoadClassWatcher(HookEngine(platform))
        val survived = CopyOnWriteArrayList<String>()
        watcher.interceptBefore { _, _, _ -> throw IllegalStateException("bug") }
        watcher.interceptBefore { name, _, _ -> survived += name }
        val hook = platform.registered.first().second
        hook.before(Inv(listOf("de.robv.android.xposed.XposedHelpers")))
        assertEquals(listOf("de.robv.android.xposed.XposedHelpers"), survived)
    }
}
