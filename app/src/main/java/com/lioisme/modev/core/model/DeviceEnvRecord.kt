package com.lioisme.modev.core.model

import androidx.compose.runtime.Immutable

/**
 * 设备环境监听记录(V31,一等公民):承载 Root 检测干预/Xposed 检测干预/模拟器检测干预/检测行为监控四类事件。
 *
 * 与 [CryptoRecord]/[SqlRecord]/[BehaviorRecord]/[PrefsRecord]/[WebViewRecord]/[NetworkRecord]
 * 并列实现 [MonitoredRecord]:注入侧出口/宿主侧分发共用;
 * 宿主侧单表存储:复用 `crypto_records` 既有列,设备环境专属字段落新增可空列。
 *
 * @param id 数据库自增主键;0 表示尚未持久化
 * @param seq 注入侧进程级单调序号
 * @param recordType 恒 [RecordTypes.DEVICE_ENV]
 * @param packageName 目标包名
 * @param kind 设备环境监听类别([DeviceEnvKind])
 * @param operation 具体动作([DeviceEnvOperation])
 * @param originalValue 干预前的原始返回值(可空)
 * @param maskedValue 干预后的替换值(可空)
 * @param intervention true=干预(隐藏),false=仅监控
 * @param targetPath 被检测的文件路径/su 路径(可空)
 * @param targetClass 被检测的类名(可空)
 * @param targetProperty 被检测的属性名(可空)
 * @param detectionPattern 检测模式/关键词(可空)
 * @param processName 目标进程名
 * @param threadName 调用线程
 * @param stack 过滤后的应用调用栈
 * @param timestamp 毫秒时间戳
 */
@Immutable
data class DeviceEnvRecord(
    val id: Long = 0L,
    override val seq: Long = 0L,
    override val recordType: String = RecordTypes.DEVICE_ENV,
    val packageName: String,
    val kind: DeviceEnvKind,
    val operation: DeviceEnvOperation,
    val originalValue: String? = null,
    val maskedValue: String? = null,
    val intervention: Boolean = true,
    val targetPath: String? = null,
    val targetClass: String? = null,
    val targetProperty: String? = null,
    val detectionPattern: String? = null,
    val params: Map<String, String>? = null,
    val processName: String? = null,
    val threadName: String,
    val stack: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
) : MonitoredRecord {

    override fun withSeq(seq: Long): DeviceEnvRecord = copy(seq = seq)

    /** 检测目标摘要(列表预览用)。 */
    fun targetSummary(): String? =
        targetPath?.takeIf { it.isNotBlank() }
            ?: targetClass?.takeIf { it.isNotBlank() }
            ?: targetProperty?.takeIf { it.isNotBlank() }
}
