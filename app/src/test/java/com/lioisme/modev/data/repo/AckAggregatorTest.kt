package com.lioisme.modev.data.repo

import org.junit.Assert.assertEquals
import org.junit.Test

/** ACK 游标聚合纯函数:按会话(回调身份)取最大 seq(ADR-2)。 */
class AckAggregatorTest {

    @Test
    fun `groups by ack callback identity and keeps max seq`() {
        var sessionA: Long? = null
        var sessionB: Long? = null
        val ackA: (Long) -> Unit = { sessionA = it }
        val ackB: (Long) -> Unit = { sessionB = it }

        val result = AckAggregator.maxSeqPerSession(
            listOf(
                1L to ackA,
                3L to ackA,
                2L to ackB,
                5L to ackA,
                4L to ackB,
            ),
        )
        assertEquals(2, result.size)
        val ackAOut = result.first { it.second === ackA }
        val ackBOut = result.first { it.second === ackB }
        assertEquals(5L, ackAOut.first)
        assertEquals(4L, ackBOut.first)
        // 回执调用本身由调用方执行;此处仅验证聚合输出
        ackAOut.second(ackAOut.first)
        ackBOut.second(ackBOut.first)
        assertEquals(5L, sessionA)
        assertEquals(4L, sessionB)
    }

    @Test
    fun `empty batch yields empty result`() {
        assertEquals(0, AckAggregator.maxSeqPerSession(emptyList()).size)
    }

    @Test
    fun `per-record lambdas defeat aggregation`() {
        // 身份不稳定的回调(每次新建 lambda)无法聚合 —— 上游接线职责
        val acks = mutableListOf<Long>()
        val result = AckAggregator.maxSeqPerSession(
            listOf(1L to { acks.add(1L) }, 2L to { acks.add(2L) }),
        )
        assertEquals(2, result.size)
    }
}
