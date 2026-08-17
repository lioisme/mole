package com.lioisme.modev.hook

/**
 * Hooker 公共工具:ByteBuffer 安全拷贝与跨阶段(before → after)输入暂存。
 *
 * ByteBuffer 入参在方法执行后 position 已前移(通常耗尽),after 阶段读取必然为空;
 * 因此输入须在 before 阶段经 [stash] 暂存,after 阶段 [take] 取出。
 * 暂存使用线程栈(ThreadLocal + ArrayDeque),天然配对,嵌套 hook 时按 LIFO 正确归还。
 */
object HookerSupport {

    private val pendingInput = ThreadLocal.withInitial { ArrayDeque<ByteArray?>() }

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

    /** before 阶段暂存入参字节,供同线程 after 阶段消费。 */
    fun stash(arg: Any?): ByteArray? {
        val bytes = bufferInput(arg)
        pendingInput.get().addLast(bytes)
        return bytes
    }

    /** after 阶段取出 before 暂存的入参;无暂存时返回 null。 */
    fun take(): ByteArray? {
        val stack = pendingInput.get()
        return if (stack.isEmpty()) null else stack.removeLast()
    }
}