package com.lioisme.modev.core.codec

import com.google.gson.JsonObject
import com.lioisme.modev.core.model.MonitoredRecord
import java.util.Collections

/**
 * 记录解码注册表(ADR-1):宿主侧按 [WireRecord.recordType] 类型化分发,
 * 新增监听能力 = 注册一个解码器,协议/传输/存储管线零改动。
 *
 * 值类型收口 [MonitoredRecord](V45 T2.5):返回 `Any?` 曾迫使摄入侧写路径手抄
 * 10 臂 `when`(每新增记录类型要同步一处,漏臂 = 静默丢档);类型化后
 * 「解码 → 落库」一条分派即够。
 *
 * 线程安全:注册发生在宿主启动装配期(单线程),读取发生在协议线程
 * (多线程),故注册表内部以不可变快照 + 写入锁保证读侧无锁安全。
 */
class RecordCodecRegistry {

    private var decoders: Map<String, (JsonObject) -> MonitoredRecord?> = emptyMap()

    /**
     * 注册解码器。同一 recordType 重复注册以最后一次为准(装配期确定性覆盖)。
     */
    @Synchronized
    fun register(recordType: String, decode: (JsonObject) -> MonitoredRecord?) {
        val next = HashMap(decoders)
        next[recordType] = decode
        decoders = Collections.unmodifiableMap(next)
    }

    /** 该类型是否已注册。 */
    fun isRegistered(recordType: String): Boolean = decoders.containsKey(recordType)

    /** 解码信封载荷;未注册类型或解码失败返回 null(调用方计数丢弃)。 */
    fun decode(wire: WireRecord): MonitoredRecord? =
        decoders[wire.recordType]?.invoke(wire.payload)
}