package com.lioisme.modev.domain.scope

/**
 * 框架作用域状态机:
 *
 * - [Connecting]:服务已连接,正在读取作用域;
 * - [Ready]:作用域可读,与 LSPosed 管理器同一数据源;
 * - [Unavailable]:无现代框架服务(模块未启用 / legacy 框架 / 服务异常)。
 */
sealed interface ScopeState {
    data object Connecting : ScopeState

    data class Ready(
        val frameworkName: String,
        val frameworkVersion: String,
        val scope: Set<String>,
    ) : ScopeState {
        fun isScoped(packageName: String): Boolean = packageName in scope
    }

    data class Unavailable(
        val reason: UnavailableReason,
        val message: String? = null,
    ) : ScopeState

    enum class UnavailableReason {
        /** 未收到框架投递的服务(模块未启用 / 运行于 legacy 框架)。 */
        NO_SERVICE,

        /** 服务存在但调用异常(如模块已停用)。 */
        SERVICE_ERROR,
    }
}