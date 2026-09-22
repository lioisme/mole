package com.lioisme.modev.hook

import java.util.Collections
import java.util.WeakHashMap

/**
 * 条带化弱键映射(V44 T2.5,审计 P10):
 *
 * 旧形态 `Collections.synchronizedMap(WeakHashMap())` 是**全局单锁**——算法/数据库
 * hook 事件在多线程(宿主线程池)下被串行化:MessageDigest 每次 update 两次加锁、
 * SQL 五张表全事件共用一把锁,高并发加解密/数据库场景直接成为宿主瓶颈。
 *
 * 本实现把键空间按 `System.identityHashCode` 分成 [STRIPES] 条,每条独立同步
 * (锁竞争降为 1/16),弱引用语义与容量特征不变(条目随键 GC 自动回收)。
 *
 * 一致性契约:单键操作(含 [getOrPut])在条内原子;跨条操作(如
 * [CryptoContextTracker.cloneContext] 的 from→to)不保证跨条原子,但两键本就
 * 分属不同实例,不存在需要原子性的不变式。
 */
internal class StripedWeakMap<V> {

    private val stripes: Array<MutableMap<Any, V>> =
        Array(STRIPES) { Collections.synchronizedMap(WeakHashMap<Any, V>()) }

    private fun stripeOf(key: Any): MutableMap<Any, V> =
        stripes[(System.identityHashCode(key) ushr 4) and (STRIPES - 1)]

    operator fun get(key: Any): V? = stripeOf(key)[key]

    operator fun set(key: Any, value: V) {
        stripeOf(key)[key] = value
    }

    fun remove(key: Any) {
        stripeOf(key).remove(key)
    }

    /** 条内原子的「取或建」:旧 [Map.getOrPut] 扩展在同步 Map 上 get/put 分离,存在重复构造。 */
    fun getOrPut(key: Any, default: () -> V): V {
        val stripe = stripeOf(key)
        synchronized(stripe) {
            stripe[key]?.let { return it }
            val created = default()
            stripe[key] = created
            return created
        }
    }

    private companion object {
        /** 条数(2 的幂,便于位运算取模)。 */
        const val STRIPES = 16
    }
}
