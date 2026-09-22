package com.lioisme.modev.domain.server

import com.lioisme.modev.core.model.McpTokenEntry
import kotlinx.coroutines.flow.StateFlow

/**
 * 外部访问与 wire 服务配置契约(V45 ML-1:配置面方法层补齐;AGENTS #19/#29 的接口化收口)。
 *
 * 统一外部访问 = wire 与 MCP **双服务共享**的单一开关(宿主本地偏好,不入远程镜像,
 * 注入侧零感知);端口写入的唯一路径就是本接口,且实现方必须内建
 * [PortValidator] 三闸(见 [PortRejectedException])——校验住在方法层而非 UI 组件,
 * 才不可能被另一侧绕过(V45 DUAL-3 根因)。
 */
interface ServerConfigService {

    /** 统一外部访问开关(false = 127.0.0.1,true = 0.0.0.0)。 */
    val serverExternalAccess: StateFlow<Boolean>

    /** wire ConfigServer 监听端口。 */
    val serverPort: StateFlow<Int>

    /** 写统一外部访问:持久化 → 双服务改绑(重启恒异步)。 */
    suspend fun setExternalAccess(enabled: Boolean)

    /**
     * 写 wire 端口。实现方在落盘前强制可用性校验,被拒即抛
     * [PortRejectedException](偏好零写入、服务零改绑);通过则持久化 + 重启改绑。
     */
    suspend fun setServerPort(port: Int)

    /** 全局资产(词表/画像/黑名单/偏差)变更后向全部在线会话重推各自有效配置(fire-and-forget)。 */
    fun repushAllConnected()
}

/**
 * MCP 服务配置契约(端口/启停/令牌认证/多令牌)。
 *
 * 令牌值是宿主本地秘密:只经本接口读写,**恒不出现在 MCP 工具输出与诊断快照**
 * (AGENTS #26 PII 纪律)。认证与外部访问互相独立(V40 D-4):是否要求 Bearer
 * 仅由 [mcpTokenAuth] 决定。
 */
interface McpServiceConfig {

    /** MCP 服务启用(默认 true = 随宿主进程常驻回环)。 */
    val mcpEnabled: StateFlow<Boolean>

    /** MCP 监听端口(默认 [com.lioisme.modev.core.model.ConfigKeys.MCP_DEFAULT_PORT])。 */
    val mcpPort: StateFlow<Int>

    /** MCP 令牌认证开关(启用后全接口含回环强制 Bearer)。 */
    val mcpTokenAuth: StateFlow<Boolean>

    /** MCP 多令牌列表(含明文值,仅宿主 UI 消费)。 */
    val mcpTokens: StateFlow<List<McpTokenEntry>>

    /** 写启用开关:持久化 → 异步 start/stop。 */
    suspend fun setMcpEnabled(enabled: Boolean)

    /** 写 MCP 端口:实现方内建可用性校验(被拒抛 [PortRejectedException])。 */
    suspend fun setMcpPort(port: Int)

    /** 写令牌认证:启用且令牌为空时先派生一枚(避免「启用即锁死」)。 */
    suspend fun setMcpTokenAuth(enabled: Boolean)

    /** 新建令牌(label 空 = 未命名);校验集原地生效,不重启不踢已连会话。 */
    suspend fun addMcpToken(label: String): McpTokenEntry

    /**
     * 重命名令牌(仅改 [McpTokenEntry.label])。
     *
     * 名称不是秘密、不参与 Bearer 校验 ⇒ 值集合逐元素不变,原地重写校验集亦零影响:
     * **不重启、不踢已连会话**(与 add/revoke 同一原地生效纪律,AGENTS #26)。
     * [label] 两端空白被裁;空串 = 未命名(UI 回落 `token_unnamed`)。未知 [id] 幂等 no-op。
     */
    suspend fun setMcpTokenLabel(id: String, label: String)

    /** 撤销令牌;撤销最后一枚不停用认证(空集合 = 全拒)。 */
    suspend fun revokeMcpToken(id: String)
}

/**
 * 端口写入被拒(Privileged / Conflict / InUse / OutOfRange)。
 *
 * 抛出即代表「偏好零写入、服务零改绑」——UI 据此渲染分型错误行,MCP 侧
 * [com.lioisme.modev.mcp] 据 [check] 映射业务错误,两侧共享同一判定,
 * 不再出现「UI 硬拒、MCP 静默 clamp」的强度分叉(V45 DUAL-3)。
 */
class PortRejectedException(val check: PortValidator.PortCheck) :
    Exception("port rejected: $check")

/**
 * 端口写入闸(V45 DUAL-3 / T2.4):校验逻辑归属方法层,UI 预检与实现方落盘前置
 * 调用**同一个函数**,杜绝「UI 三闸硬拒、MCP 静默 clamp 直写」的强度分叉。
 *
 * 判定本体复用 [PortValidator.check](校验序与豁免语义见其 KDoc,AGENTS #29)。
 */
object PortWriteGuard {

    /** 预检(供输入框行内错误提示)。 */
    suspend fun check(
        candidate: Int,
        selfPort: Int,
        selfLabel: String,
        otherPort: Int?,
        otherLabel: String,
        externalAccess: Boolean,
    ): PortValidator.PortCheck = PortValidator.check(
        candidate = candidate,
        selfPort = selfPort,
        selfLabel = selfLabel,
        otherPort = otherPort,
        otherLabel = otherLabel,
        externalAccess = externalAccess,
    )

    /** 写入前置:非 [PortValidator.PortCheck.Available] 即抛 [PortRejectedException]。 */
    suspend fun requireAvailable(
        candidate: Int,
        selfPort: Int,
        selfLabel: String,
        otherPort: Int?,
        otherLabel: String,
        externalAccess: Boolean,
    ) {
        val result = check(candidate, selfPort, selfLabel, otherPort, otherLabel, externalAccess)
        if (result !is PortValidator.PortCheck.Available) throw PortRejectedException(result)
    }
}
