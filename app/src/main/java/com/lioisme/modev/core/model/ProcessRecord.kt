package com.lioisme.modev.core.model

import androidx.compose.runtime.Immutable

/**
 * 进程控制监听记录(V33,一等公民):承载退出检测/崩溃检测/录屏检测三类事件。
 *
 * 与 [CryptoRecord]/[SqlRecord]/[BehaviorRecord]/[PrefsRecord]/[WebViewRecord]/
 * [NetworkRecord]/[DeviceEnvRecord]/[UiInteractionRecord]/[SystemRecord]
 * 并列实现 [MonitoredRecord];宿主侧单表存储:复用 `crypto_records` 既有列,
 * 进程控制专属字段落新增可空列(迁移 v11→v12)。
 *
 * @param id 数据库自增主键;0 表示尚未持久化
 * @param seq 注入侧进程级单调序号
 * @param recordType 恒 [RecordTypes.PROCESS_CONTROL]
 * @param packageName 目标包名
 * @param kind 进程控制监听类别([ProcessKind])
 * @param operation 具体动作([ProcessOperation])
 * @param exitCode System.exit 参数(可空)
 * @param processPid killProcess 目标 pid(可空)
 * @param crashType 异常类名(可空)
 * @param crashMessage 异常消息(可空)
 * @param crashBlocked true=已拦截崩溃(可空;V34+ 由 [interventionAction] 替代)
 * @param screenRecordBlocked true=已拦截录屏检测(可空;V34+ 由 [interventionAction] 替代)
 * @param interventionAction 干预动作标记:"BLOCKED"=已阻止,"DETECTED"=仅检测,null=不适用
 * @param processName 目标进程名
 * @param threadName 调用线程
 * @param stack 过滤后的应用调用栈
 * @param timestamp 毫秒时间戳
 */
@Immutable
data class ProcessRecord(
    val id: Long = 0L,
    override val seq: Long = 0L,
    override val recordType: String = RecordTypes.PROCESS_CONTROL,
    val packageName: String,
    val kind: ProcessKind,
    val operation: ProcessOperation,
    val exitCode: Int? = null,
    val processPid: Int? = null,
    val crashType: String? = null,
    val crashMessage: String? = null,
    @Deprecated("V34+ 由 interventionAction 替代", ReplaceWith("interventionAction"))
    val crashBlocked: Boolean? = null,
    @Deprecated("V34+ 由 interventionAction 替代", ReplaceWith("interventionAction"))
    val screenRecordBlocked: Boolean? = null,
    val interventionAction: String? = null,
    val params: Map<String, String>? = null,
    val processName: String? = null,
    val threadName: String,
    val stack: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
) : MonitoredRecord {

    override fun withSeq(seq: Long): ProcessRecord = copy(seq = seq)

    /** 退出摘要(列表预览用)。 */
    fun exitSummary(): String? {
        return when (kind) {
            ProcessKind.EXIT_INTERCEPT -> "exit=${exitCode ?: "?"} pid=${processPid ?: "?"}"
            ProcessKind.CRASH_PREVENT -> "${crashType ?: "Exception"}: ${crashMessage?.take(60) ?: ""}"
            ProcessKind.SCREEN_RECORD -> interventionAction?.lowercase() ?: "detected"
        }
    }
}
