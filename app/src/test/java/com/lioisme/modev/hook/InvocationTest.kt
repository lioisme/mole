package com.lioisme.modev.hook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.lang.reflect.Executable

/**
 * 中性 Hook 抽象契约测试:Invocation 越界安全取值与 MethodHook 空实现。
 * 不依赖任何框架类(纯逻辑,可 JVM 直跑)。
 */
class InvocationTest {

    @Test
    fun `getArg 越界返回 null 且不抛异常`() {
        val invocation = FakeInvocation(arrayOf("alg", 42))

        assertEquals("alg", invocation.getArg(0))
        assertEquals(42, invocation.getArg(1))
        assertNull(invocation.getArg(2))
        assertNull(invocation.getArg(-1))
        assertNull(invocation.getArg(Int.MAX_VALUE))
        assertEquals(2, invocation.argCount)
    }

    @Test
    fun `空参数调用 getArg 恒为 null`() {
        val invocation = FakeInvocation(emptyArray())
        assertNull(invocation.getArg(0))
        assertEquals(0, invocation.argCount)
    }

    @Test
    fun `MethodHook 默认实现为空操作`() {
        val hook = object : MethodHook() {}
        val invocation = FakeInvocation(arrayOf("x"))

        hook.before(invocation)
        hook.after(invocation)
        // 无异常即通过;再验证子类覆写路径可正常分发
        val recorder = object : MethodHook() {
            var beforeCalls = 0
            var afterCalls = 0
            override fun before(invocation: Invocation) {
                beforeCalls++
            }

            override fun after(invocation: Invocation) {
                afterCalls++
            }
        }
        recorder.before(invocation)
        recorder.after(invocation)
        assertEquals(1, recorder.beforeCalls)
        assertEquals(1, recorder.afterCalls)
    }

    private class FakeInvocation(private val args: Array<Any?>) : Invocation() {
        override val executable: Executable? = null
        override val thisObject: Any? = null
        override val result: Any? = null
        override val argCount: Int get() = args.size

        override fun getArgUnsafe(index: Int): Any? = args[index]
    }
}
