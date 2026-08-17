package com.lioisme.modev.domain.scope

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * 作用域仓库:以框架 DB 为单一事实源(R2)。
 *
 * - 订阅 [ScopeGateway.serviceFlow],服务连接后读取 [ScopeService.getScope];
 * - [toggle] 勾选 / 取消 = [ScopeService.requestScope] / [ScopeService.removeScope],
 *   与 LSPosed 管理器修改同一份数据;
 * - 变更后经 [refresh] 重新读取,UI 与框架始终保持一致。
 */
class ScopeRepository(
    private val gateway: ScopeGateway,
    private val requestTimeoutMillis: Long = REQUEST_TIMEOUT_MILLIS,
    private val stateScope: CoroutineScope = repositoryScope,
) {

    private val refreshTrigger = MutableStateFlow(0)
    private val mutationMutex = Mutex()

    val state: StateFlow<ScopeState> =
        combine(gateway.serviceFlow(), refreshTrigger) { service, _ -> service }
            .flatMapLatest { service ->
                flow {
                    if (service == null) {
                        emit(ScopeState.Unavailable(ScopeState.UnavailableReason.NO_SERVICE))
                    } else {
                        emit(ScopeState.Connecting)
                        emit(loadScope(service))
                    }
                }
            }
            .stateIn(
                stateScope,
                SharingStarted.Eagerly,
                ScopeState.Unavailable(ScopeState.UnavailableReason.NO_SERVICE),
            )

    /**
 * 重新读取作用域(服务连接 / 前台轮询 / 变更后调用)。
 * 与 [toggle]/[remove] 共用 [mutationMutex],保证读-改-读串行,杜绝竞态脏读。
 */
    suspend fun refresh() {
        mutationMutex.withLock { refreshNow() }
    }

    /** 不加锁的内部刷新:仅限已持有 [mutationMutex] 的路径调用(防死锁)。 */
    private suspend fun refreshNow() {
        refreshTrigger.value++
    }

    /**
     * 勾选 / 取消勾选应用(作用域变更)。
     *
     * - 加入:请求框架(弹系统通知确认),回调成功后状态自动同步;
     * - 移除:框架直接生效。
     */
    suspend fun toggle(packageName: String): ScopeChangeResult = mutationMutex.withLock {
        val current = state.value
        if (current !is ScopeState.Ready) {
            return ScopeChangeResult.Failed("框架未就绪,无法修改作用域")
        }
        if (current.isScoped(packageName)) removeNow(packageName) else addNow(packageName)
    }

    private suspend fun addNow(packageName: String): ScopeChangeResult {
        val service = gateway.serviceFlow().first() ?: return ScopeChangeResult.Failed("框架服务未连接")
        val outcome = withTimeoutOrNull(requestTimeoutMillis) {
            suspendCancellableCoroutine<ScopeChangeResult> { cont ->
                service.requestScope(listOf(packageName), object : ScopeRequestCallback {
                    override fun onApproved() {
                        if (cont.isActive) cont.resume(ScopeChangeResult.ScopeRequested)
                    }

                    override fun onFailed(message: String) {
                        if (cont.isActive) cont.resume(ScopeChangeResult.Failed(message))
                    }
                })
            }
        } ?: ScopeChangeResult.Failed("等待系统通知确认超时,确认后下拉刷新即可同步")
        refreshNow()
        return outcome
    }

    /** 移出作用域(等价于 LSPosed 作用域取消勾选),框架直接生效。 */
    suspend fun remove(packageName: String): ScopeChangeResult = mutationMutex.withLock {
        removeNow(packageName)
    }

    private suspend fun removeNow(packageName: String): ScopeChangeResult {
        val service = gateway.serviceFlow().first() ?: return ScopeChangeResult.Failed("框架服务未连接")
        val result = runCatching { service.removeScope(listOf(packageName)) }
        refreshNow()
        return if (result.isSuccess) {
            ScopeChangeResult.ScopeRemoved
        } else {
            ScopeChangeResult.Failed("移除失败:${result.exceptionOrNull()?.message ?: "未知错误"}")
        }
    }

    private suspend fun loadScope(service: ScopeService): ScopeState =
        try {
            val scope = service.getScope().toSet()
            runCatching { android.util.Log.i("Modev", "scope loaded: $scope (${service.frameworkName} ${service.frameworkVersion})") }
            ScopeState.Ready(
                frameworkName = service.frameworkName,
                frameworkVersion = service.frameworkVersion,
                scope = scope,
            )
        } catch (t: Throwable) {
            runCatching { android.util.Log.w("Modev", "scope load failed: ${t.message}") }
            ScopeState.Unavailable(
                reason = ScopeState.UnavailableReason.SERVICE_ERROR,
                message = t.message,
            )
        }

    private companion object {
        const val REQUEST_TIMEOUT_MILLIS = 30_000L
        val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    }
}