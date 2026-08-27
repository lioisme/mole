package com.lioisme.modev.core.model

import androidx.compose.runtime.Immutable
import com.lioisme.modev.core.util.BytesView

/**
 * 网络环境监听记录(V30,一等公民):承载 SSL 绕过/代理隐藏/VPN 隐藏/请求监控四类事件。
 *
 * 与 [CryptoRecord]/[SqlRecord]/[BehaviorRecord]/[PrefsRecord]/[WebViewRecord]
 * 并列实现 [MonitoredRecord]:注入侧出口/宿主侧分发共用;
 * 宿主侧单表存储:复用 `crypto_records` 既有列,网络专属字段落新增可空列(迁移 v10→v11)。
 *
 * @param id 数据库自增主键;0 表示尚未持久化
 * @param seq 注入侧进程级单调序号
 * @param recordType 恒 [RecordTypes.NETWORK]
 * @param packageName 目标包名
 * @param kind 网络监听类别([NetworkKind])
 * @param operation 具体动作([NetworkOperation])
 * @param url 请求 URL(REQUEST 类别)
 * @param method HTTP 方法(GET/POST 等,REQUEST 类别)
 * @param statusCode HTTP 状态码(REQUEST 响应,可空)
 * @param requestHeaders 请求头(REQUEST 类别)
 * @param responseHeaders 响应头(REQUEST 类别)
 * @param requestBody 请求体预览(REQUEST 类别)
 * @param responseBody 响应体预览(REQUEST 类别)
 * @param sslProtocol TLS 协议版本(REQUEST 类别,如 TLSv1.3)
 * @param cipherSuite 密码套件(REQUEST 类别)
 * @param peerCertificates 对端证书链(REQUEST 类别,SHA-256 哈希列表)
 * @param proxyHost 代理主机(PROXY 类别)
 * @param proxyPort 代理端口(PROXY 类别)
 * @param vpnActive VPN 是否活跃(VPN 类别)
 * @param processName 目标进程名
 * @param threadName 调用线程
 * @param stack 过滤后的应用调用栈(Reqable 堆栈追溯)
 * @param timestamp 毫秒时间戳
 */
@Immutable
data class NetworkRecord(
    val id: Long = 0L,
    override val seq: Long = 0L,
    override val recordType: String = RecordTypes.NETWORK,
    val packageName: String,
    val kind: NetworkKind,
    val operation: NetworkOperation,
    val url: String? = null,
    val method: String? = null,
    val statusCode: Int? = null,
    val requestHeaders: Map<String, String>? = null,
    val responseHeaders: Map<String, String>? = null,
    val requestBody: BytesView? = null,
    val responseBody: BytesView? = null,
    val sslProtocol: String? = null,
    val cipherSuite: String? = null,
    val peerCertificates: List<String>? = null,
    val proxyHost: String? = null,
    val proxyPort: Int? = null,
    val vpnActive: Boolean? = null,
    val params: Map<String, String>? = null,
    val processName: String? = null,
    val threadName: String,
    val stack: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
) : MonitoredRecord {

    override fun withSeq(seq: Long): NetworkRecord = copy(seq = seq)

    /** URL 域名提取(列表预览用)。 */
    fun urlHost(): String? {
        if (url.isNullOrBlank()) return null
        return runCatching { java.net.URL(url).host?.takeIf { it.isNotBlank() } }.getOrNull()
    }
}
