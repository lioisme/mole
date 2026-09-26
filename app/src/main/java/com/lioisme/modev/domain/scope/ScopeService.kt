package com.lioisme.modev.domain.scope

/**
 * 框架作用域服务抽象(与 libxposed service 解耦,便于单元测试)。
 * 现代框架(Vector 2.x / LSPosed 1.9+)由适配器实现;legacy 框架下为 null。
 */
interface ScopeService {

    /** 框架名称,如 "LSPosed"。 */
    val frameworkName: String

    /** 框架版本,如 "1.10.2"。 */
    val frameworkVersion: String

    /** 读取当前模块作用域(框架 DB 为单一事实源)。 */
    suspend fun getScope(): List<String>

    /**
     * 请求将应用加入作用域。框架会弹系统通知由用户确认,结果经 [callback] 异步返回
     * (成功 / 拒绝;可能永不回调,由调用方超时处理)。
     */
    fun requestScope(packages: List<String>, callback: ScopeRequestCallback)

    /** 从作用域移除应用(框架直接生效,无确认弹窗)。 */
    suspend fun removeScope(packages: List<String>)
}

/** [ScopeService.requestScope] 的结果回调。 */
interface ScopeRequestCallback {
    fun onApproved()
    fun onFailed(message: String)
}

/** 作用域变更结果(供 UI 提示)。 */
sealed interface ScopeChangeResult {
    /** 已向框架发出作用域请求,等待系统通知确认。 */
    data object ScopeRequested : ScopeChangeResult

    /** 已从作用域移除。 */
    data object ScopeRemoved : ScopeChangeResult

    /**
     * 变更失败(M17 错误码化):domain 零硬编码文案,Snackbar 文案由 UI 层
     * 按错误码本地化(en/zh)。错误码为稳定契约,新增失败类型不得破坏现有码。
     */
    sealed interface Failed : ScopeChangeResult {

        /** 框架状态未就绪(非 Ready,无法修改作用域)。 */
        data object FrameworkNotReady : Failed

        /** 框架服务未连接。 */
        data object ServiceNotConnected : Failed

        /** 系统通知确认被拒绝 / 框架回调失败。 */
        data object UserRejected : Failed

        /** 等待系统通知确认超时。 */
        data object Timeout : Failed

        /** 移出作用域执行失败。 */
        data object RemoveFailed : Failed
    }
}

/** 框架作用域服务流(宿主进程单例,可空 = 无现代框架服务)。 */
interface ScopeGateway {
    fun serviceFlow(): kotlinx.coroutines.flow.Flow<ScopeService?>
}
