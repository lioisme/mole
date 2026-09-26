package com.lioisme.modev.core.model

import androidx.compose.runtime.Immutable

/**
 * 系统信息监听记录(V33,一等公民):承载 Logcat/App 元数据/签名三类事件。
 *
 * 与 [CryptoRecord]/[SqlRecord]/[BehaviorRecord]/[PrefsRecord]/[WebViewRecord]/
 * [NetworkRecord]/[DeviceEnvRecord]/[UiInteractionRecord] 并列实现 [MonitoredRecord];
 * 宿主侧单表存储:复用 `crypto_records` 既有列,系统专属字段落新增可空列(迁移 v11→v12)。
 *
 * @param id 数据库自增主键;0 表示尚未持久化
 * @param seq 注入侧进程级单调序号
 * @param recordType 恒 [RecordTypes.SYSTEM]
 * @param packageName 目标包名
 * @param kind 系统信息监听类别([SystemKind])
 * @param operation 具体动作([SystemOperation])
 * @param logcatTag Logcat tag(可空)
 * @param logcatLevel Logcat level D/I/W/E/V/F(可空)
 * @param logcatMessage Logcat 消息(可空)
 * @param appVersion 应用版本名(可空)
 * @param appVersionCode 应用版本号(可空)
 * @param appMinSdk 最低 SDK(可空)
 * @param appTargetSdk 目标 SDK(可空)
 * @param appPermissions 权限列表 JSON(可空)
 * @param signatureHash 签名 SHA-256 哈希(可空)
 * @param signatureBase64 签名 Base64(可空)
 * @param processName 目标进程名
 * @param threadName 调用线程
 * @param stack 过滤后的应用调用栈
 * @param timestamp 毫秒时间戳
 */
@Immutable
data class SystemRecord(
    val id: Long = 0L,
    override val seq: Long = 0L,
    override val recordType: String = RecordTypes.SYSTEM,
    val packageName: String,
    val kind: SystemKind,
    val operation: SystemOperation,
    val logcatTag: String? = null,
    val logcatLevel: String? = null,
    val logcatMessage: String? = null,
    val appVersion: String? = null,
    val appVersionCode: Int? = null,
    val appMinSdk: Int? = null,
    val appTargetSdk: Int? = null,
    val appPermissions: List<String>? = null,
    val signatureHash: String? = null,
    val signatureBase64: String? = null,
    val params: Map<String, String>? = null,
    val processName: String? = null,
    val threadName: String,
    val stack: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
) : MonitoredRecord {

    override fun withSeq(seq: Long): SystemRecord = copy(seq = seq)
}
