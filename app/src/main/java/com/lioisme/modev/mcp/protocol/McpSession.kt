package com.lioisme.modev.mcp.protocol

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * MCP 会话:initialize 握手建立,`Mcp-Session-Id` 头贯穿后续请求;
 * 服务端 restart 时整体失效(spec: 客户端收到 404 后重新 initialize)。
 */
data class McpSession(
    val id: String,
    val protocolVersion: String,
)

/** 会话表(线程安全;restart 一次性清空 = 全部客户端强制重握手)。 */
class McpSessionManager {

    private val sessions = ConcurrentHashMap<String, McpSession>()

    fun create(protocolVersion: String): McpSession {
        val session = McpSession(UUID.randomUUID().toString(), protocolVersion)
        sessions[session.id] = session
        return session
    }

    /** 校验会话;缺失/未知返回 null(调用方 404/400)。 */
    fun validate(id: String?): McpSession? =
        id?.let { sessions[it] }

    fun close(id: String?) {
        if (id != null) sessions.remove(id)
    }

    fun count(): Int = sessions.size

    fun closeAll() = sessions.clear()
}
