package com.lioisme.modev.core.model

/**
 * 监听能力记录的统一模型基座(ADR-2,数据库监听 T1):
 * 注入侧出口([com.lioisme.modev.hook.RecordSink])与宿主侧分发
 * ([com.lioisme.modev.core.codec.RecordCodecRegistry])共用的类型化入口;
 * 各能力模型(算法 / 数据库 / 未来行为 / 交互)实现本接口,
 * 传输 / 存储 / UI 主链路按 [recordType] 泛化分发,新增能力不改动主链路。
 */
sealed interface MonitoredRecord {
    /** 注入侧进程级单调序号(协议可靠性:ACK 与幂等去重依据)。 */
    val seq: Long

    /** 记录类型标识(与 [RecordTypes] 对齐)。 */
    val recordType: String

    /** 返回携带指定 [seq] 的新实例(不可变模型语义,由出口分配序号)。 */
    fun withSeq(seq: Long): MonitoredRecord
}