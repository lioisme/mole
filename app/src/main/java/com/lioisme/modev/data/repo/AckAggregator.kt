package com.lioisme.modev.data.repo

/**
 * ACK 游标聚合(ADR-2):按会话(ACK 回调身份)聚合最大 seq 回执,
 * 每批每会话仅回执一个游标,注入侧据此裁剪该 seq 之前全部 WAL 行,
 * 协议开销 O(批次)。纯函数,可 JVM 单测。
 */
object AckAggregator {

    /**
     * 输入一批 (seq, ack) 对,输出每会话最大 seq 的唯一回执列表。
     * 回调身份同一性:宿主侧由 ConfigServer.ClientSession 提供单一实例,
     * 若每记录新建 lambda,身份将发散,聚合退化为每条一 ACK(本函数无法修复,
     * 属于上游接线职责,见 ClientSession.ack)。
     */
    fun maxSeqPerSession(pairs: List<Pair<Long, (Long) -> Unit>>): List<Pair<Long, (Long) -> Unit>> {
        val byAck = HashMap<(Long) -> Unit, Long>()
        for ((seq, ack) in pairs) {
            val max = byAck[ack] ?: Long.MIN_VALUE
            if (seq > max) byAck[ack] = seq
        }
        return byAck.map { it.value to it.key }
    }
}