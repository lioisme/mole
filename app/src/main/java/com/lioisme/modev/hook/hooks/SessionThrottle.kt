package com.lioisme.modev.hook.hooks

/**
 * 会话级事件节流(V27 行为降噪):同一 (kind|op|path) 事件在 [windowMs] 窗口内
 * 重复发生即跳过——抑制框架/库启动风暴(同一配置文件被反复打开等),
 * 同时保留周期性访问的可观测性(窗口过后放行一次)。
 *
 * 实现:access-order LRU(容量 [capacity] 上限,淘汰最久未命中键)+ 惰性过期;
 * 单实例绑定单进程会话,锁粒度为对象级(hook 线程竞争极低)。
 * 纯 JVM 可测(nanoTime 由调用方注入)。
 */
class SessionThrottle(
    private val capacity: Int = DEFAULT_CAPACITY,
    private val windowMs: Long = DEFAULT_WINDOW_MS,
) {

    // accessOrder=true:get 命中也会移到队尾;removeEldest 容量淘汰。
    private val access =
        object : LinkedHashMap<String, Long>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>): Boolean =
                size > capacity
        }

    /**
     * 是否应跳过该事件:键在窗口内已出现过 → true 并刷新无操作;
     * 键不存在或已过期 → false 并登记本次时间。
     */
    fun shouldSkip(key: String, nowMs: Long = System.nanoTime() / 1_000_000L): Boolean {
        if (key.isEmpty()) return false
        synchronized(access) {
            val last = access[key]
            if (last != null && nowMs - last < windowMs) {
                return true
            }
            access[key] = nowMs
            return false
        }
    }

    companion object {
        /** LRU 容量上限(内存护栏;超容量按最久未命中淘汰)。 */
        const val DEFAULT_CAPACITY = 256

        /** 同键事件去重窗口(毫秒)。 */
        const val DEFAULT_WINDOW_MS = 1_000L
    }
}
