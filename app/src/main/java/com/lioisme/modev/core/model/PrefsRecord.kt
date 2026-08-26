package com.lioisme.modev.core.model

import com.lioisme.modev.core.util.BytesView

/**
 * 一条完整的 SharedPreferences 监听记录(V28,一等公民)。
 *
 * 与 [CryptoRecord]/[SqlRecord]/[BehaviorRecord] 并列实现 [MonitoredRecord]:
 * 注入侧出口/宿主侧分发共用;宿主侧单表存储复用既有列(ADR-1 同构):
 * - [path] → 配置文件绝对路径(OPEN 时由构造器 File 参数被动取得);
 * - [value] → 键值内容(BytesView 文本视图;GET/PUT 值域,受 fileContentCapture 闸门);
 * - [key] → `prefKey` 新列(迁移 v9→v10)。
 *
 * 被动红线:仅采集「随调用即可得」的实参/返回值;绝不反射读取
 * SharedPreferencesImpl 内部字段、绝不触碰磁盘文件。
 *
 * @param id 宿主侧数据库主键;0 表示尚未持久化
 * @param seq 注入侧进程级单调序号(ACK 与幂等去重依据;0 = 无)
 * @param recordType 记录类型(恒 [RecordTypes.PREFS])
 * @param packageName 目标包名
 * @param kind 偏好类别([PrefsKind])
 * @param operation 具体动作([PrefsOperation])
 * @param key 配置键(get/put/remove 系列首参;getAll/clear 为 null)
 * @param value 值内容三态视图(fileContentCapture 开启时承载;contains/clear 恒 null)
 * @param path 配置文件路径(OPEN 恒有;get/put 经实例映射尽力回填,映射逐出为 null)
 * @param params 标量参数(valueType/mode/found/api 等标记)
 * @param processName 目标进程名(多进程 App 区分主/子进程)
 * @param contextId 关联同一实例多次调用的标识(预留,当前恒 0)
 * @param threadName 调用线程
 * @param stack 过滤后的应用调用栈(最内层在前)
 * @param timestamp 毫秒时间戳
 */
data class PrefsRecord(
    val id: Long = 0L,
    override val seq: Long = 0L,
    override val recordType: String = RecordTypes.PREFS,
    val packageName: String,
    val kind: PrefsKind,
    val operation: PrefsOperation,
    val key: String? = null,
    val value: BytesView? = null,
    val path: String? = null,
    val params: Map<String, String>? = null,
    val processName: String? = null,
    val contextId: Long = 0L,
    val threadName: String,
    val stack: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
) : MonitoredRecord {
    override fun withSeq(seq: Long): PrefsRecord = copy(seq = seq)
}
