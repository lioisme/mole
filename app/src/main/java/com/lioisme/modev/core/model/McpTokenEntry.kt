package com.lioisme.modev.core.model

import java.security.SecureRandom

/**
 * MCP 访问令牌条目:多令牌列表的存储单元,宿主本地 SharedPreferences
 * 专用(`ConfigKeys.MCP_TOKENS` JSON 数组),**不入远程镜像**(仅宿主进程消费,
 * 同 [ConfigKeys.SERVER_EXTERNAL_ACCESS] 纪律)。
 *
 * - [id]:撤销定位用稳定标识(UUID,不含秘密);
 * - [label]:用户命名(可空 = 未命名,UI 回落显示「令牌 {短码}」);
 * - [value]:Bearer 秘密(SecureRandom 32 hex;明文仅存宿主本地,
 *   UI 默认掩码、MCP 工具输出恒不含值);
 * - [createdAt]:创建毫秒时间戳(列表排序与展示)。
 */
data class McpTokenEntry(
    val id: String,
    val label: String,
    val value: String,
    val createdAt: Long,
) {
    companion object {

        private val RANDOM = SecureRandom()

        /** 派生一枚新令牌值:32 位小写 hex(SecureRandom,非 UUID)。 */
        fun generateValue(): String {
            val bytes = ByteArray(16)
            RANDOM.nextBytes(bytes)
            return bytes.joinToString("") { "%02x".format(it) }
        }

        /** 创建条目:id = 去连字符 UUID,label 可空。 */
        fun create(label: String): McpTokenEntry =
            McpTokenEntry(
                id = java.util.UUID.randomUUID().toString().replace("-", ""),
                label = label,
                value = generateValue(),
                createdAt = System.currentTimeMillis(),
            )
    }
}
