package com.lioisme.modev.mcp.protocol

/**
 * MCP 协议常量(V38):Model Context Protocol 2025-06-18,Streamable HTTP 传输。
 * 独立于注入侧 wire 协议(VERSION 恒 4),零共享消息形态。
 */
object McpProtocol {

    /** 服务端主版本(spec 2025-06-18)。 */
    const val PROTOCOL_VERSION = "2025-06-18"

    /** initialize 协商接受集(向后兼容)。 */
    val SUPPORTED_VERSIONS = setOf("2024-11-05", "2025-03-26", PROTOCOL_VERSION)

    /** 唯一 HTTP 端点。 */
    const val ENDPOINT_PATH = "/mcp"

    /** 会话头(Streamable HTTP)。 */
    const val HEADER_SESSION = "Mcp-Session-Id"

    /** MCP 默认绑定端口(与 wire 1510 区隔,宿主本地偏好可改)。 */
    const val DEFAULT_PORT = 15110

    /** 协议方法。 */
    const val METHOD_INITIALIZE = "initialize"
    const val NOTIFICATION_INITIALIZED = "notifications/initialized"
    const val METHOD_PING = "ping"
    const val METHOD_TOOLS_LIST = "tools/list"
    const val METHOD_TOOLS_CALL = "tools/call"

    /** 请求体上限。 */
    const val MAX_BODY_BYTES = 1 shl 20

    /** 工具执行超时(防 Agent 面挂死传输线程)。 */
    const val TOOL_TIMEOUT_MS = 30_000L

    /**
     * 协商协议版本:客户端声明版本在支持集内 → 原样回;否则回落服务端主版本
     * (客户端可断开,spec 语义)。
     */
    fun negotiate(clientVersion: String?): String =
        if (clientVersion != null && clientVersion in SUPPORTED_VERSIONS) clientVersion else PROTOCOL_VERSION
}
