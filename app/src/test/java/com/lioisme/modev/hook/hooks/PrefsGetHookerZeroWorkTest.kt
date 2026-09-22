package com.lioisme.modev.hook.hooks

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

import com.lioisme.modev.core.model.HookSwitch
import com.lioisme.modev.core.model.PrefsKind
import com.lioisme.modev.hook.HookEngine
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.MethodHook
import com.lioisme.modev.hook.Platform
import com.lioisme.modev.hook.ProbeContext
import com.lioisme.modev.hook.CryptoContextTracker
import com.lioisme.modev.hook.RecordSink
import java.lang.reflect.Executable
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V43 T2 热路径「全关零工作」行为钉死(V2.2):PrefsGetHooker 在 GET 默认关时,
 * getAll/getStringSet 不得触碰返回容器(gson 全量序列化零发生)、不得走实例索引、
 * 不得产生记录。投毒 Map/Set(entrySet/iterator 计数)是「序列化先于门控」形态的
 * 直接哨兵——旧实现在 emit 实参位先 collectionText(map),本测试当时必红。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PrefsGetHookerZeroWorkTest {

    private class CapturingPlatform : Platform {
        val hooks = CopyOnWriteArrayList<Pair<String, MethodHook>>()
        override fun register(executable: Executable?, hook: MethodHook): Boolean {
            if (executable != null) hooks += executable.name to hook
            return true
        }

        override fun registerInterventional(executable: Executable?, hook: MethodHook): Boolean =
            register(executable, hook)

        override fun log(message: String, throwable: Throwable?) = Unit
    }

    private class Inv(
        private val args: List<Any?>,
        override val result: Any?,
        override val thisObject: Any? = null,
    ) : Invocation() {
        override val executable: Executable? get() = null
        override val argCount: Int get() = args.size
        override fun getArgUnsafe(index: Int): Any? = args[index]
    }

    /** 访问即计数的投毒容器:gson(MapTypeAdapter)经 entrySet/iterator 序列化,任一触发即计数。 */
    private class PoisonMap : LinkedHashMap<String, String>() {
        var touched = 0
            private set
        override val entries: MutableSet<MutableMap.MutableEntry<String, String>>
            get() {
                touched++
                return super.entries
            }

        override val keys: MutableSet<String>
            get() {
                touched++
                return super.keys
            }

        override val values: MutableCollection<String>
            get() {
                touched++
                return super.values
            }
    }

    private fun buildHooker(platform: CapturingPlatform, switch: HookSwitch = HookSwitch.DEFAULT): PrefsGetHooker {
        val sink = RecordSink(
            jsonOf = { "{}" },
            sendJson = { true },
            isConnected = { false },
        )
        val context = ProbeContext(
            engine = HookEngine(platform),
            config = { switch },
            tracker = CryptoContextTracker,
            sink = sink,
            packageName = "com.test",
        )
        val hooker = PrefsGetHooker(context, PrefsInstanceIndex())
        hooker.install()
        return hooker
    }

    @Test
    fun `getAll with GET disabled does not touch the returned map`() {
        val platform = CapturingPlatform()
        buildHooker(platform)
        val getAll = platform.hooks.filter { it.first == "getAll" }
        assertTrue("getAll 钩子应已注册(平台存在该方法)", getAll.isNotEmpty())
        val poison = PoisonMap().apply { put("sessionToken", "secret") }
        for ((_, hook) in getAll) {
            hook.after(Inv(args = emptyList(), result = poison))
        }
        // GET kind 默认关(HookSwitch.DEFAULT):门控前置 → 容器零访问、零记录
        assertEquals("全关时不得序列化返回容器(V2.2)", 0, poison.touched)
    }

    @Test
    fun `prefsEnabled default keeps GET off`() {
        // 语义前置断言:本测试有效性依赖 GET 默认关(NOISE_HIGH 契约)
        assertTrue(!HookSwitch.DEFAULT.prefsEnabled(PrefsKind.GET))
    }

    /** 正向对照:GET 开启时容器必被序列化(哨兵非空转)。 */
    @Test
    fun `getAll with GET enabled does serialize the map`() {
        val platform = CapturingPlatform()
        buildHooker(
            platform,
            switch = HookSwitch.DEFAULT.copy(prefsListen = true, prefsGet = true),
        )
        val poison = PoisonMap().apply { put("sessionToken", "secret") }
        for ((_, hook) in platform.hooks.filter { it.first == "getAll" }) {
            hook.after(Inv(args = emptyList(), result = poison))
        }
        assertTrue("GET 开启时必有序列化访问", poison.touched > 0)
    }
}
