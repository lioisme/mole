package com.lioisme.modev.hook

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ClassValueCache] 契约测试(V45 P0 / T0.7):`java.lang.ClassValue`(API 34,本模块
 * minSdk 26 上不存在)的安全替身必须保持 ClassValue 四条语义 —— 每类恰好一次计算、
 * `null` 同样缓存、按类隔离、并发下同一类返回同一实例。
 */
class ClassValueCacheTest {

    private class A
    private class B
    private class C

    @Test
    fun computes_exactly_once_per_class() {
        val calls = AtomicInteger()
        val cache = ClassValueCache { calls.incrementAndGet() }
        assertEquals(1, cache.get(A::class.java))
        assertEquals(1, cache.get(A::class.java))
        assertEquals(2, cache.get(B::class.java))
        assertEquals(2, calls.get())
    }

    /** 缺失字段缓存为 null 必须生效:否则热路径每次访问都重付反射解析 + 异常构造。 */
    @Test
    fun null_value_is_cached_not_recomputed() {
        val calls = AtomicInteger()
        val cache = ClassValueCache<String?> { calls.incrementAndGet(); null }
        repeat(5) { assertNull(cache.get(A::class.java)) }
        assertEquals(1, calls.get())
    }

    @Test
    fun values_are_isolated_per_class() {
        val cache = ClassValueCache { it.name }
        assertEquals(A::class.java.name, cache.get(A::class.java))
        assertEquals(B::class.java.name, cache.get(B::class.java))
    }

    @Test
    fun concurrent_get_yields_single_instance_and_single_compute() {
        val threads = 16
        val created = AtomicInteger()
        val cache = ClassValueCache { _ -> created.incrementAndGet(); Any() }
        val pool = Executors.newFixedThreadPool(threads)
        try {
            // 16 任务 / 16 线程:invokeAll 提交后即并发执行,无需额外栅栏
            val results = pool.invokeAll(
                List(threads) {
                    java.util.concurrent.Callable { cache.get(C::class.java) }
                },
            )
            val values = results.map { it.get(10, TimeUnit.SECONDS) }
            assertEquals(1, created.get())
            assertEquals(1, values.toSet().size)
            values.forEach { assertSame(values.first(), it) }
        } finally {
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    @Test
    fun repeated_get_returns_same_reference() {
        val cache = ClassValueCache { Any() }
        assertSame(cache.get(A::class.java), cache.get(A::class.java))
    }
}
