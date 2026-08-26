package com.lioisme.modev.core.model

import com.lioisme.modev.core.util.BytesView

/**
 * 一条完整的行为监听记录(V27,一等公民)。
 *
 * 与 [CryptoRecord]/[SqlRecord] 并列实现 [MonitoredRecord]:注入侧出口/宿主侧分发共用;
 * 宿主侧单表存储:复用 `crypto_records` 既有列(paramsJson、stackJson、contextId、
 * threadName 等),行为专属字段落新增可空列(ADR-1,迁移 v8→v9)。
 *
 * @param id 宿主侧数据库主键;0 表示尚未持久化
 * @param seq 注入侧进程级单调序号(协议可靠性:ACK 与幂等去重依据;0 = 无)
 * @param recordType 记录类型(恒 [RecordTypes.BEHAVIOR])
 * @param packageName 目标包名
 * @param kind 行为类别([BehaviorKind])
 * @param operation 具体动作([BehaviorOperation])
 * @param path 主体绝对路径(文件/目录;asset 名;shell 为 null)
 * @param destPath 重命名/移动目标路径(RENAME 操作)
 * @param command Shell argv 列表(EXEC;序列化为 JSON 数组字符串落 command 列)
 * @param fileSize 文件字节数(仅调用即可得时写入,禁止额外探测;未知为 null)
 * @param content 内容预览三态视图(仅 fileContentCapture 开启的整读 API;其余恒 null)
 * @param params 标量参数承载(append/api/dirCount/accessMode/fileLength/truncated 标记等)
 * @param processName 目标进程名(多进程 App 区分主/子进程)
 * @param contextId 关联同一对象(File/Process 等)多次调用的实例标识
 * @param threadName 调用线程
 * @param stack 过滤后的应用调用栈(最内层在前)
 * @param timestamp 毫秒时间戳
 */
data class BehaviorRecord(
    val id: Long = 0L,
    override val seq: Long = 0L,
    override val recordType: String = RecordTypes.BEHAVIOR,
    val packageName: String,
    val kind: BehaviorKind,
    val operation: BehaviorOperation,
    val path: String? = null,
    val destPath: String? = null,
    val command: List<String>? = null,
    val fileSize: Long? = null,
    val content: BytesView? = null,
    val params: Map<String, String>? = null,
    val processName: String? = null,
    val contextId: Long = 0L,
    val threadName: String,
    val stack: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
) : MonitoredRecord {

    override fun withSeq(seq: Long): BehaviorRecord = copy(seq = seq)

    /** Shell argv 展示串("a b c" 空格连接;非 shell 行为 null)。 */
    fun commandDisplay(): String? = command?.joinToString(" ")
}
