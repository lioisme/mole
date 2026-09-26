package com.lioisme.modev.mcp.tools

import com.google.gson.JsonObject

/**
 * MCP 工具:Agent 面向能力的最小契约。
 * 实现仅允许依赖 UI 同源上游(domain、data.repo、core 三族包,B2 allowlist,
 * `McpLayerBoundaryTest` 强制);工具文本一律英文(B4,非用户可见文本)。
 */
interface McpTool {

    /** 工具名(snake_case,协议内唯一)。 */
    val name: String

    /** 面向 Agent 的英文描述(含危险等级声明)。 */
    val description: String

    /** JSON Schema 形态的输入声明。 */
    fun inputSchema(): JsonObject

    /**
     * 执行(已过 schema 校验);业务错误抛 [McpToolException]
     * (经注册表包装为 `result.isError=true`,与协议错误分型)。
     */
    suspend fun execute(args: JsonObject): JsonObject

    /** 业务错误(参数合法但执行失败:目标不存在/约束拒绝等)。 */
    class McpToolException(message: String) : Exception(message)
}
