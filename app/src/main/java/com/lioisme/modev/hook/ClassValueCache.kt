package com.lioisme.modev.hook

/**
 * 按类缓存的反射句柄容器(V45 P0 / PLAN T0.7):`java.lang.ClassValue` 的 API 26 安全替身。
 *
 * 为什么不能用 ClassValue:它自 **API 34** 才存在,而本模块 `minSdk = 26` —— V44 T2.5 用它
 * 做热路径字段缓存,在 Android 8–13 宿主上会在 Hooker 构造期直接 `NoClassDefFoundError`
 * (注入即崩,且崩在装钩阶段与功能开关无关),lint `NewApi` 报 9 error 即此。
 *
 * 语义与 ClassValue 对齐:
 * - 弱键([StripedWeakMap])→ 条目随类卸载回收,不持有 Class 强引用;
 * - [get] 幂等且每类只计算一次([Box] 承载,`null` 同样被缓存 —— 否则字段缺失类
 *   每次访问都重付 `getDeclaredField` + 异常构造,正是本次要消除的热路径成本);
 * - 无 API 级别要求,条带化锁沿用 V44 T2.5 的低竞争特征。
 */
internal class ClassValueCache<V>(private val compute: (Class<*>) -> V) {

    /** 承载盒:使 `null` 也是「已计算」的缓存值。 */
    private class Box<V>(val value: V)

    private val cache = StripedWeakMap<Box<V>>()

    fun get(clazz: Class<*>): V = cache.getOrPut(clazz) { Box(compute(clazz)) }.value
}
