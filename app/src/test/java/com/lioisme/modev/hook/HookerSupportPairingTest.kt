package com.lioisme.modev.hook

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V43 T3 入参暂存配对回归(V4.4):暂存按调用句柄(token)配对,
 * 交错/嵌套/after 缺失(原方法抛异常)均不再错位或无界驻留。
 */
class HookerSupportPairingTest {

    private class FakeInv(
        private val args: List<Any?>,
        override val token: Any? = Object(),
    ) : Invocation() {
        override val executable: java.lang.reflect.Executable? get() = null
        override val thisObject: Any? get() = null
        override val result: Any? get() = null
        override val argCount: Int get() = args.size
        override fun getArgUnsafe(index: Int): Any? = args[index]
    }

    @Test
    fun `interleaved calls pair by token not stack order`() {
        val a = FakeInv(listOf("A"))
        val b = FakeInv(listOf("B"))
        HookerSupport.stash(a, "hello-a".toByteArray())
        HookerSupport.stash(b, "hello-b".toByteArray())
        // 旧 LIFO 栈:b 的 after 先跑会错拿 a 的字节;token 键控各归其主
        assertArrayEquals("hello-b".toByteArray(), HookerSupport.take(b))
        assertArrayEquals("hello-a".toByteArray(), HookerSupport.take(a))
        // 再取为空(已消费)
        assertNull(HookerSupport.take(a))
    }

    @Test
    fun `skipped after entries are capped not unbounded`() {
        // 模拟原方法持续抛异常(after 缺失)的极端场景:滞留条目受容量上限回收
        repeat(200) {
            val inv = FakeInv(listOf("x"))
            HookerSupport.stash(inv, ByteArray(8))
        }
        // 上限触发清理后,新配对仍可工作
        val fresh = FakeInv(listOf("y"))
        HookerSupport.stash(fresh, "payload".toByteArray())
        assertArrayEquals("payload".toByteArray(), HookerSupport.take(fresh))
    }

    @Test
    fun `bytebuffer duplicate does not move original position`() {
        val buf = java.nio.ByteBuffer.wrap("0123456789".toByteArray())
        val bytes = HookerSupport.bufferInput(buf)
        assertTrue(buf.position() == 0)
        assertEquals(10, bytes?.size)
    }
}
