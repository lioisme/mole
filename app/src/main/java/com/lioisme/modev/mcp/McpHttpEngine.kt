package com.lioisme.modev.mcp

import fi.iki.elonen.NanoHTTPD

/**
 * HTTP 结果(传输无关形态,便于 JVM 直测):状态码 + 体 + 额外头。
 */
data class HttpResult(
    val status: Int,
    val body: String,
    val headers: Map<String, String> = emptyMap(),
)

/**
 * MCP HTTP 引擎(V38):NanoHTTPD 2.3.1 子类,唯一职责 = HTTP 解析 +
 * 单端点路由,把[method]/[uri]/[headers]/[body]交给 [route] 闭包。
 * 不用其静态文件/会话/Cookie 能力;每连接一线程(NanoHTTPD 默认),
 * 阻塞式 runBlocking 承载 suspend 工具执行。
 */
class McpHttpEngine(
    hostname: String,
    port: Int,
    private val route: (method: String, uri: String, headers: Map<String, String>, body: String) -> HttpResult,
) : NanoHTTPD(hostname, port) {

    override fun serve(session: IHTTPSession): Response {
        // body 读取:绕过 parseBody(仅表单/multipart 才填 postData),
        // 按 Content-Length 原样读流(JSON 原文交 route 闭包)
        var body = ""
        if (session.method == Method.POST) {
            val length = session.headers["content-length"]?.toIntOrNull() ?: 0
            if (length > 0) {
                val bytes = ByteArray(length)
                var read = 0
                while (read < length) {
                    val n = session.inputStream.read(bytes, read, length - read)
                    if (n < 0) break
                    read += n
                }
                body = String(bytes, 0, read, Charsets.UTF_8)
            }
        }
        val headers = session.headers
        val result = route(
            session.method.name,
            session.uri.orEmpty(),
            headers,
            body,
        )
        val status = when (result.status) {
            200 -> Response.Status.OK
            202 -> Response.Status.ACCEPTED
            400 -> Response.Status.BAD_REQUEST
            401 -> Response.Status.UNAUTHORIZED
            404 -> Response.Status.NOT_FOUND
            405 -> Response.Status.METHOD_NOT_ALLOWED
            413 -> Response.Status.PAYLOAD_TOO_LARGE
            else -> Response.Status.INTERNAL_ERROR
        }
        val response = newFixedLengthResponse(status, "application/json", result.body)
        result.headers.forEach { (k, v) -> response.addHeader(k, v) }
        return response
    }
}
