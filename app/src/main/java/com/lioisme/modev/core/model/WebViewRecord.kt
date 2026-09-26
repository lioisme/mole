package com.lioisme.modev.core.model

import androidx.compose.runtime.Immutable
import com.lioisme.modev.core.util.BytesView

/**
 * WebView 监听记录:承载 WebView 全生命周期行为的结构化记录。
 *
 * @param id       数据库自增主键(插入时 0,Room 分配)。
 * @param seq      注入侧序列号(WireCodec 信封写入,去重 + ACK 裁剪)。
 * @param recordType 存储类型标识(恒为 [RecordTypes.WEBVIEW])。
 * @param packageName 宿主应用包名。
 * @param processName 宿主进程名(多进程区分)。
 * @param kind     WebView 监听类别([WebViewKind])。
 * @param operation 操作类型([WebViewOperation])。
 * @param url      目标 URL(PAGE_LOAD/EVALUATE_JS;其余 null)。
 * @param urlHost  URL 域名(列表预览用,提取自 [url])。
 * @param script   执行的 JS 脚本(EVALUATE_JS;截断至 maxBytesPerField)。
 * @param bridgeName JS 桥名称(ADD_JS_INTERFACE)。
 * @param params   操作参数键值对(JSEncrypt 参数等;可空)。
 * @param input    输入数据(JSEncrypt 明文;可空)。
 * @param output   输出数据(JSEncrypt 密文;可空)。
 * @param debugEnabled 调试开关值(SET_DEBUGGING)。
 * @param threadName 采集线程名。
 * @param stack    调用栈(logStack 开启时采集)。
 * @param timestamp 采集时间戳(毫秒)。
 */
@Immutable
data class WebViewRecord(
    val id: Long = 0L,
    override val seq: Long = 0L,
    override val recordType: String = RecordTypes.WEBVIEW,
    val packageName: String,
    val processName: String = "",
    val kind: WebViewKind,
    val operation: WebViewOperation,
    val url: String? = null,
    val urlHost: String? = null,
    val script: BytesView? = null,
    val bridgeName: String? = null,
    val params: Map<String, String>? = null,
    val input: BytesView? = null,
    val output: BytesView? = null,
    val debugEnabled: Boolean? = null,
    val threadName: String = "",
    val stack: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
) : MonitoredRecord {

    override fun withSeq(seq: Long): WebViewRecord = copy(seq = seq)

    companion object {
        /** 从 URL 提取域名(列表预览用)。 */
        fun extractHost(url: String?): String? {
            if (url.isNullOrBlank()) return null
            return runCatching {
                java.net.URL(url).host?.takeIf { it.isNotBlank() }
            }.getOrNull()
        }
    }
}
