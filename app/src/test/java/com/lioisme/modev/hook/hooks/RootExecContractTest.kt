package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.HookSwitch
import com.lioisme.modev.hook.CryptoContextTracker
import com.lioisme.modev.hook.HookEngine
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.MethodHook
import com.lioisme.modev.hook.Platform
import com.lioisme.modev.hook.ProbeContext
import com.lioisme.modev.hook.RecordSink
import java.io.IOException
import java.lang.reflect.Executable
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V43 T3 契约忠实化回归(V2.5):root 命令拦截以 IOException 结束调用
 * (Runtime.exec / ProcessBuilder.start 的失败契约),绝不 setResult(null)
 * 让宿主 waitFor/流读取 NPE。
 */
class RootExecContractTest {

    private class CapturingPlatform : Platform {
        val hooks = CopyOnWriteArrayList<Pair<String, MethodHook>>()
        override fun register(executable: Executable?, hook: MethodHook): Boolean {
            if (executable != null) hooks += "${executable.declaringClass.simpleName}.${executable.name}" to hook
            return true
        }

        override fun registerInterventional(executable: Executable?, hook: MethodHook): Boolean =
            register(executable, hook)

        override fun log(message: String, throwable: Throwable?) = Unit
    }

    private class Inv(
        private val args: List<Any?>,
        override val thisObject: Any? = null,
    ) : Invocation() {
        override val executable: Executable? get() = null
        override val result: Any? get() = null
        override val argCount: Int get() = args.size
        override fun getArgUnsafe(index: Int): Any? = args[index]
        var replaced: Any? = null
        var replacedSet = false
        var thrown: Throwable? = null
        override val consumed: Boolean get() = replacedSet || thrown != null
        override fun setResult(value: Any?) { replaced = value; replacedSet = true }
        override fun setThrowable(t: Throwable) { thrown = t }
    }

    private fun hooker(): Pair<RootExecHooker, CapturingPlatform> {
        val platform = CapturingPlatform()
        val engine = HookEngine(platform)
        val sink = RecordSink(
            jsonOf = { "{}" },
            sendJson = { true },
            isConnected = { false },
        )
        val context = ProbeContext(
            engine = engine,
            config = { HookSwitch.DEFAULT.copy(deviceEnvListen = true, rootExec = true) },
            tracker = CryptoContextTracker,
            sink = sink,
            packageName = "com.test",
        )
        return RootExecHooker(context) to platform
    }

    @Test
    fun `su exec terminates with IOException not null result`() {
        val (hooker, platform) = hooker()
        hooker.install()
        val execHook = platform.hooks.first { it.first.endsWith("exec") }.second
        val inv = Inv(args = listOf(arrayOf("/system/xbin/su", "-c", "id")))
        execHook.before(inv)
        assertTrue("必须以 IOException 结束调用(契约内失败)", inv.thrown is IOException)
        assertTrue("不得 setResult(null) 破坏返回值契约", !inv.replacedSet)
    }

    @Test
    fun `benign command passes through untouched`() {
        val (hooker, platform) = hooker()
        hooker.install()
        val execHook = platform.hooks.first { it.first.endsWith("exec") }.second
        val inv = Inv(args = listOf(arrayOf("ls", "-l")))
        execHook.before(inv)
        assertTrue("非拦截命令零干预", inv.thrown == null && !inv.replacedSet)
    }
}
