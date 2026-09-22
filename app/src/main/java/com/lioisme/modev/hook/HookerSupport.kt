package com.lioisme.modev.hook

/**
 * Hooker 公共工具:ByteBuffer 安全拷贝与跨阶段(before → after)输入暂存。
 *
 * ByteBuffer 入参在方法执行后 position 已前移(通常耗尽),after 阶段读取必然为空;
 * 因此输入须在 before 阶段经 [stash] 暂存,after 阶段 [take] 取出。
 *
 * V43 T3(V4.4 修复):暂存按**调用句柄配对**([Invocation.token] 即底层
 * chain/MethodHookParam,before/after 共享同一实例)——旧 ThreadLocal 栈方案在
 * 「原方法抛异常跳过 after」时把字节滞留线程栈(慢泄漏),嵌套/异常混排后
 * `take()` 与操作错配(记录数据错位);键控 + 容量上限把两种失效模式同时关闭。
 */
object HookerSupport {

    /** 暂存条目:字节 + 登记时刻(纳秒,逐条过期判定用)。 */
    private class Stash(val bytes: ByteArray?, val atNanos: Long)

    private val pending = ThreadLocal.withInitial { java.util.IdentityHashMap<Any, Stash>() }

    /**
     * 逐 token 过期窗口(V44 T2.6,审计 P14):`after` 缺失(原方法抛异常)时条目
     * 会滞留线程暂存表。旧形态用「`size > 32` → `clear()`」兜底——**误伤在途**:
     * 并发/嵌套调用超过 32 条时把别的调用尚未消费的字节一起清掉,导致记录数据错位。
     * 改为按时间窗口逐条淘汰:远超任何单次 hook 调用的存活期(30s),
     * 只回收真正的孤儿条目,在途条目零波及。
     */
    private const val STALE_TTL_NANOS = 30_000_000_000L

    /** 硬上限(病态场景最后兜底:超过即按最旧时间淘汰,不整体清空)。 */
    private const val HARD_CAP = 512

    /** 将 ByteArray/ByteBuffer 入参转为字节数组;duplicate 拷贝不影响原缓冲。 */
    fun bufferInput(arg: Any?): ByteArray? = when (arg) {
        is ByteArray -> arg
        is java.nio.ByteBuffer -> {
            val dup = arg.duplicate()
            val out = ByteArray(dup.remaining())
            dup.get(out)
            out
        }
        else -> null
    }

    /** 读取 ByteBuffer 当前剩余内容(用于 after 阶段捕获输出缓冲,duplicate 不移动原位置)。 */
    fun bufferRemaining(buffer: java.nio.ByteBuffer?): ByteArray? {
        if (buffer == null) return null
        val dup = buffer.duplicate()
        if (dup.remaining() <= 0) return null
        val out = ByteArray(dup.remaining())
        dup.get(out)
        return out
    }

    /** 按 [offset, offset+length) 安全切片;越界自动收敛,空切片返回 null。 */
    fun slice(arg: ByteArray?, offset: Int?, length: Int?): ByteArray? {
        if (arg == null || offset == null || length == null) return null
        val off = offset.coerceIn(0, arg.size)
        val len = length.coerceIn(0, arg.size - off)
        if (len == 0) return null
        return arg.copyOfRange(off, off + len)
    }

    /** before 阶段按调用句柄暂存入参字节,供同调用 after 阶段 [take] 消费。 */
    fun stash(inv: Invocation, arg: Any?): ByteArray? {
        val bytes = bufferInput(arg)
        val token = inv.token
        if (token != null) {
            val map = pending.get()
            evictStale(map)
            map[token] = Stash(bytes, System.nanoTime())
        }
        return bytes
    }

    /** after 阶段取回本调用暂存的入参;无暂存(含被回收)时返回 null。 */
    fun take(inv: Invocation): ByteArray? {
        val token = inv.token ?: return null
        val map = pending.get()
        evictStale(map)
        return map.remove(token)?.bytes
    }

    /**
     * 逐条过期清理(调用方持有线程暂存表):
     * 1) 超 [STALE_TTL_NANOS] 的条目逐条移除(孤儿:原方法抛异常跳过 after);
     * 2) 仍超 [HARD_CAP] 时按最旧时间逐条淘汰,绝不整体清空(不误伤在途条目)。
     */
    private fun evictStale(map: java.util.IdentityHashMap<Any, Stash>) {
        if (map.isEmpty()) return
        val now = System.nanoTime()
        val it = map.entries.iterator()
        while (it.hasNext()) {
            if (now - it.next().value.atNanos > STALE_TTL_NANOS) it.remove()
        }
        if (map.size <= HARD_CAP) return
        val victims = map.entries.sortedBy { it.value.atNanos }.take(map.size - HARD_CAP)
        victims.forEach { map.remove(it.key) }
    }
}