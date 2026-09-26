package com.lioisme.modev.core.model

/**
 * 捕获配置键单一事实源(ADR-5):
 * 宿主侧([com.lioisme.modev.data.repo.SettingsRepository])与
 * 注入侧([com.lioisme.modev.framework.RemotePrefsBridge])必须引用本对象,
 * 禁止双端各写字面量(键漂移即双端失联)。
 */
object ConfigKeys {

    /** 配置键前缀(旧版按包配置与全局配置共用)。 */
    const val SWITCH_PREFIX = "switch_"

    /** 全局捕获配置键:全应用共享一份,与包名解耦。 */
    const val GLOBAL_SWITCH = SWITCH_PREFIX + "global"

    /** 单应用监听覆盖键前缀(V23 三态继承,惰性创建;与已清理的旧 `switch_{pkg}` 前缀隔离)。 */
    const val OVERRIDE_PREFIX = "override_"

    /**
     * 包名 → 覆盖存储键。
     * @param packageName 已作用域应用的包名(调用方保证非空)。
     */
    fun overrideKey(packageName: String): String = OVERRIDE_PREFIX + packageName

    /**
     * 统一外部访问键(V41,取代 V26/V38 双键分裂):wire ConfigServer 与
     * MCP McpServer **双服务消费**的唯一事实源——false(默认)= 127.0.0.1,
     * true = 0.0.0.0。宿主本地 SharedPreferences 专用,不入远程镜像
     * (仅宿主进程消费,注入侧零感知,wire VERSION 恒 4)。
     */
    const val SERVER_EXTERNAL_ACCESS = "server_external_access"

    /** 服务监听端口键:宿主本地 SharedPreferences 专用,默认 [WIRE_DEFAULT_PORT]。 */
    const val SERVER_PORT = "server_port"

    /**
     * wire ConfigServer 默认端口(V43 T7/V5.13:端口默认值单点——此前以散文
     * 散布多注释,V39 改端口即逐点改文案;引用面一律经本常量)。
     */
    const val WIRE_DEFAULT_PORT = 1511

    /** MCP 服务默认端口( 1510;V43 T7 单点化,见 [WIRE_DEFAULT_PORT] 同注)。 */
    const val MCP_DEFAULT_PORT = 1510

    /**
     * 全局功能配置键:宿主本地 SharedPreferences 专用,
     * 存 [FeatureConfig] JSON(伪装画像选择 + 黑名单追加项);
     * init 时并入 switch_global 种子,注入侧直读兜底可见。
     */
    const val GLOBAL_FEATURE = "feature_global"

    /** MCP 服务启用键:宿主本地专用,默认 true(回环常驻,与 ConfigServer 先例一致)。 */
    const val MCP_ENABLED = "mcp_enabled"

    /** MCP 服务端口键:宿主本地专用,默认 1510。 */
    const val MCP_PORT = "mcp_port"

    /**
     * MCP 外部访问键(V38;**V41 起退役为迁移输入**):init 读到后 OR 合并进
     * [SERVER_EXTERNAL_ACCESS] 并删除本键(幂等,合并后不再出现)。
     */
    const val MCP_EXTERNAL_ACCESS = "mcp_external_access"

    /** MCP 令牌认证开关键:宿主本地专用;默认 false = 停用(全接口免 Token)。 */
    const val MCP_TOKEN_AUTH = "mcp_token_auth"

    /**
     * MCP 多令牌列表键:宿主本地专用,存 `List<McpTokenEntry>` JSON;
     * 不镜像注入侧。取代 V38 单键 [MCP_TOKEN](升级时惰性导入为「默认令牌」)。
     */
    const val MCP_TOKENS = "mcp_tokens"

    /** MCP Bearer Token 单键(V38 遗留,V39 起仅迁移读取,不再写入)。 */
    const val MCP_TOKEN = "mcp_token"
}
