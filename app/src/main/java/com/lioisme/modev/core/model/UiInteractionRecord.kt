package com.lioisme.modev.core.model

import androidx.compose.runtime.Immutable

/**
 * UI 交互监听记录(V33,一等公民):承载弹窗/控件文本/Activity 跳转三类事件。
 *
 * 与 [CryptoRecord]/[SqlRecord]/[BehaviorRecord]/[PrefsRecord]/[WebViewRecord]/
 * [NetworkRecord]/[DeviceEnvRecord] 并列实现 [MonitoredRecord]:注入侧出口/宿主侧分发共用;
 * 宿主侧单表存储:复用 `crypto_records` 既有列,UI 专属字段落新增可空列(迁移 v11→v12)。
 *
 * @param id 数据库自增主键;0 表示尚未持久化
 * @param seq 注入侧进程级单调序号
 * @param recordType 恒 [RecordTypes.UI_INTERACTION]
 * @param packageName 目标包名
 * @param kind UI 交互监听类别([UiInteractionKind])
 * @param operation 具体动作([UiInteractionOperation])
 * @param dialogTitle 弹窗标题(可空)
 * @param dialogMessage 弹窗消息(可空)
 * @param dialogKeywords 命中的关键字列表(JSON 数组,可空)
 * @param dialogIntercepted true=拦截,false=放行(可空;V34+ 由 [interventionAction] 替代)
 * @param interventionAction 干预动作标记:"BLOCKED"=已阻止弹窗,"DETECTED"=仅检测,null=不适用
 * @param widgetText 控件文本内容(可空)
 * @param widgetClassName 控件所在 Activity 类名(可空)
 * @param activityName 目标 Activity 类名(可空)
 * @param intentAction Intent action(可空)
 * @param intentData Intent data URI(可空)
 * @param intentExtrasJson Intent extras 序列化 JSON(可空)
 * @param intentFlags Intent flags(可空)
 * @param processName 目标进程名
 * @param threadName 调用线程
 * @param stack 过滤后的应用调用栈
 * @param timestamp 毫秒时间戳
 */
@Immutable
data class UiInteractionRecord(
    val id: Long = 0L,
    override val seq: Long = 0L,
    override val recordType: String = RecordTypes.UI_INTERACTION,
    val packageName: String,
    val kind: UiInteractionKind,
    val operation: UiInteractionOperation,
    val dialogTitle: String? = null,
    val dialogMessage: String? = null,
    val dialogKeywords: List<String>? = null,
    @Deprecated("V34+ 由 interventionAction 替代", ReplaceWith("interventionAction"))
    val dialogIntercepted: Boolean? = null,
    val interventionAction: String? = null,
    val widgetText: String? = null,
    val widgetClassName: String? = null,
    val activityName: String? = null,
    val intentAction: String? = null,
    val intentData: String? = null,
    val intentExtrasJson: String? = null,
    val intentFlags: Int? = null,
    val params: Map<String, String>? = null,
    val processName: String? = null,
    val threadName: String,
    val stack: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
) : MonitoredRecord {

    override fun withSeq(seq: Long): UiInteractionRecord = copy(seq = seq)
}
