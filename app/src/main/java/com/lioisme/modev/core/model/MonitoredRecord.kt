package com.lioisme.modev.core.model

import com.lioisme.modev.core.util.BytesView

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

/**
 * 记录内容重量估算(V44 T2.3 出口字节预算):近似驻留字节数。
 *
 * 背景:注入侧队列旧形态只有**条数**上限(2048),多 MB/条的记录(大字段 hex/base64/text)
 * 最坏可驻留 GB 级内存——宿主 GC 风暴/OOM 的直接来源。出口以本估算做字节闸门,
 * 与条数闸门共同约束峰值。
 *
 * 口径:只统计字符串/字节承载字段的物化字符数(×2 = UTF-16 字节),标量忽略;
 * 密封接口穷尽 `when`,新增记录类型由编译器强制同步(不会静默漏算)。
 */
val MonitoredRecord.contentWeight: Int
    get() = when (this) {
        is CryptoRecord ->
            listOf(key, iv, input, output, salt, aad, publicKey).weight() +
                stack.size * STACK_FRAME_WEIGHT

        is SqlRecord ->
            (bindArgs?.sumOf { it.weight() } ?: 0) +
                (rows?.sumOf { it.weight() } ?: 0) +
                (sql?.length ?: 0) * 2 +
                stack.size * STACK_FRAME_WEIGHT

        is BehaviorRecord ->
            (content.weightOrZero()) + (command?.sumOf { it.length } ?: 0) * 2 +
                stack.size * STACK_FRAME_WEIGHT

        is PrefsRecord ->
            value.weightOrZero() + stack.size * STACK_FRAME_WEIGHT

        is WebViewRecord ->
            listOf(script, input, output).weight() + stack.size * STACK_FRAME_WEIGHT

        is NetworkRecord ->
            listOf(requestBody, responseBody).weight() +
                (peerCertificates?.sumOf { it.length } ?: 0) * 2 +
                stack.size * STACK_FRAME_WEIGHT

        is DeviceEnvRecord ->
            (originalValue?.length ?: 0) * 2 + stack.size * STACK_FRAME_WEIGHT

        is UiInteractionRecord ->
            (dialogKeywords?.sumOf { it.length } ?: 0) * 2 +
                (dialogTitle?.length ?: 0) * 2 + (dialogMessage?.length ?: 0) * 2 +
                stack.size * STACK_FRAME_WEIGHT

        is SystemRecord ->
            (appPermissions?.sumOf { it.length } ?: 0) * 2 +
                (logcatMessage?.length ?: 0) * 2 +
                stack.size * STACK_FRAME_WEIGHT

        is ProcessRecord ->
            stack.size * STACK_FRAME_WEIGHT
    }

/** 三态视图重量(hex + base64 + text 的物化字符数 × 2)。 */
internal fun BytesView?.weightOrZero(): Int =
    this?.let { (it.hex.length + (it.base64?.length ?: 0) + (it.text?.length ?: 0)) * 2 } ?: 0

private fun List<BytesView?>.weight(): Int = sumOf { it.weightOrZero() }

/** 绑定值重量(标量/字符串/hex 三态)。 */
private fun SqlBindValue.weight(): Int =
    (v?.length ?: 0) * 2 + (hex?.length ?: 0) * 2 + 8

/** 结果行重量(单元格 v/hex 字符数 + 固定开销)。 */
private fun SqlResultRow.weight(): Int = c.sumOf { (it.v?.length ?: 0) * 2 + (it.hex?.length ?: 0) * 2 + 8 }

/** 单个栈帧的近似驻留字节(类名+方法名+文件行号字符串化后的典型值)。 */
private const val STACK_FRAME_WEIGHT = 96
