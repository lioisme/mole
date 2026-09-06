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

    /** 全局捕获配置键(V10):全应用共享一份,与包名解耦。 */
    const val GLOBAL_SWITCH = SWITCH_PREFIX + "global"

    /** 单应用监听覆盖键前缀(V23 三态继承,惰性创建;与已清理的旧 `switch_{pkg}` 前缀隔离)。 */
    const val OVERRIDE_PREFIX = "override_"

    /**
     * 包名 → 覆盖存储键。
     * @param packageName 已作用域应用的包名(调用方保证非空)。
     */
    fun overrideKey(packageName: String): String = OVERRIDE_PREFIX + packageName

    /**
     * 服务监听外部访问键(V26):宿主本地 SharedPreferences 专用,
     * 不入远程镜像(仅宿主进程消费,注入侧零感知)。
     */
    const val SERVER_EXTERNAL_ACCESS = "server_external_access"

    /** 服务监听端口键(V29):宿主本地 SharedPreferences 专用,默认1510。 */
    const val SERVER_PORT = "server_port"

    /**
     * 全局功能配置键(V37 T3.5):宿主本地 SharedPreferences 专用,
     * 存 [FeatureConfig] JSON(伪装画像选择 + 黑名单追加项);
     * init 时并入 switch_global 种子,注入侧直读兜底可见。
     */
    const val GLOBAL_FEATURE = "feature_global"

    /** MCP 服务启用键(V38):宿主本地专用,默认 true(回环常驻,与 ConfigServer 先例一致)。 */
    const val MCP_ENABLED = "mcp_enabled"

    /** MCP 服务端口键(V38):宿主本地专用,默认 15110。 */
    const val MCP_PORT = "mcp_port"

    /** MCP 外部访问键(V38):宿主本地专用;true = 0.0.0.0 且强制 Bearer Token。 */
    const val MCP_EXTERNAL_ACCESS = "mcp_external_access"

    /** MCP Bearer Token 键(V38):宿主本地自动生成,诊断页展示/复制;不镜像注入侧。 */
    const val MCP_TOKEN = "mcp_token"
}