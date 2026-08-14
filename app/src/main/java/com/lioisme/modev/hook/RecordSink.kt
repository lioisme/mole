package com.lioisme.modev.hook

import com.lioisme.modev.core.model.CryptoRecord
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicLong

/**
 * 记录出口:有界内存队列 + 异步发送线程。
 *
 * - 容量有界([CAPACITY]),满时丢弃最旧记录并累计丢弃计数(防宿主进程内存膨胀);
 * - 发送失败(宿主 App 未运行等)时同样丢弃并计数;
 * - 全部逻辑在工作线程内完成,不阻塞被 Hook 的调用线程。
 */
class RecordSink(private val sender: (CryptoRecord) -> Boolean) {

    private val queue = LinkedBlockingQueue<CryptoRecord>(CAPACITY)
    private val dropped = AtomicLong()

    @Volatile
    private var running = false
    private var worker: Thread? = null

    val droppedCount: Long get() = dropped.get()

    fun start() {
        if (running) return
        running = true
        worker = Thread(::loop, "modev-record-sink").apply {
            isDaemon = true
            start()
        }
    }

    fun emit(record: CryptoRecord) {
        if (!running) return
        if (!queue.offer(record)) {
            // 队列已满:丢弃最旧一条后重试
            queue.poll()
            dropped.incrementAndGet()
            if (!queue.offer(record)) {
                dropped.incrementAndGet()
            }
        }
    }

    private fun loop() {
        while (running) {
            val record = queue.take()
            try {
                val sent = sender(record)
                if (!sent) dropped.incrementAndGet()
            } catch (t: Throwable) {
                dropped.incrementAndGet()
            }
        }
    }

    companion object {
        private const val CAPACITY = 2048
    }
}