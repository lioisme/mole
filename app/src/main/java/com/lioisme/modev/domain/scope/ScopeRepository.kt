package com.lioisme.modev.domain.scope

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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
 *
 * ## V10 稳态状态流(需求 1:界面零闪烁)
 * - [state] 只发射「真实变化」:刷新路径经内容判等(Ready → Ready 且 scope/版本一致
 *   不发射)+ [distinctUntilChanged] 兜底;
 * - `Connecting` 中间态仅在服务首连/切换时出现,前台兜底刷新不再产生
 *   Ready → Connecting → Ready 翻转(此前 5s 轮询导致 UI 周期性闪烁);
 * - [syncForeground] 封装前台同步策略(立即一次 + 低频静默兜底),生命周期感知
 *   由调用方(repeatOnLifecycle)负责。
 */
class ScopeRepository(
    private val gateway: ScopeGateway,
    private val requestTimeoutMillis: Long = REQUEST_TIMEOUT_MILLIS,
    private val stateScope: CoroutineScope = repositoryScope,
) {

    private val refreshTrigger = MutableStateFlow(0)
    private val mutationMutex = Mutex()

    /** 最近一次已发射给下游的状态(刷新路径内容判等与中间态抑制的依据)。 */
    private var lastEmitted: ScopeState? = null

    val state: StateFlow<ScopeState> =
        combine(gateway.serviceFlow(), refreshTrigger) { service, _ -> service }
            .flatMapLatest { service ->
                flow {
                    if (service == null) {
                        val unavailable = ScopeState.Unavailable(ScopeState.UnavailableReason.NO_SERVICE)
                        lastEmitted = unavailable
                        emit(unavailable)
                        return@flow
                    }
                    // 仅服务首连/框架切换时发射 Connecting;静默兜底刷新不产生中间态
                    val last = lastEmitted
                    if (last !is ScopeState.Ready || last.frameworkName != service.frameworkName) {
                        lastEmitted = ScopeState.Connecting
                        emit(ScopeState.Connecting)
                    }
                    val loaded = loadScope(service)
                    // 内容判等:Ready → Ready 且数据一致时不发射(无谓重组即闪烁之源)
                    if (loaded == lastEmitted) return@flow
                    lastEmitted = loaded
                    emit(loaded)
                }
            }
            .distinctUntilChanged()
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
     * 前台同步策略(V10):进入前台立即同步一次,此后每 [FOREGROUND_SYNC_INTERVAL_MS]
     * 静默兜底(内容判等,无变化不发射 → UI 零重组零闪烁)。
     * 框架侧无公开变更回调,外部勾选(LSPosed 管理器)以低频兜底感知;
     * 本应用内变更([toggle]/[remove])已由各自 refresh 即时同步。
     * 随 repeatOnLifecycle(RESUMED) 取消:退后台即停,零后台开销。
     */
    suspend fun syncForeground() {
        refresh()
        while (true) {
            delay(FOREGROUND_SYNC_INTERVAL_MS)
            refresh()
        }
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
            return ScopeChangeResult.Failed.FrameworkNotReady
        }
        if (current.isScoped(packageName)) removeNow(packageName) else addNow(packageName)
    }

    private suspend fun addNow(packageName: String): ScopeChangeResult {
        val service = gateway.serviceFlow().first()
            ?: return ScopeChangeResult.Failed.ServiceNotConnected
        val outcome = withTimeoutOrNull(requestTimeoutMillis) {
            suspendCancellableCoroutine<ScopeChangeResult> { cont ->
                service.requestScope(listOf(packageName), object : ScopeRequestCallback {
                    override fun onApproved() {
                        if (cont.isActive) cont.resume(ScopeChangeResult.ScopeRequested)
                    }

                    override fun onFailed(message: String) {
                        if (cont.isActive) cont.resume(ScopeChangeResult.Failed.UserRejected)
                    }
                })
            }
        } ?: ScopeChangeResult.Failed.Timeout
        refreshNow()
        return outcome
    }

    /** 移出作用域(等价于 LSPosed 作用域取消勾选),框架直接生效。 */
    suspend fun remove(packageName: String): ScopeChangeResult = mutationMutex.withLock {
        removeNow(packageName)
    }

    private suspend fun removeNow(packageName: String): ScopeChangeResult {
        val service = gateway.serviceFlow().first()
            ?: return ScopeChangeResult.Failed.ServiceNotConnected
        val result = runCatching { service.removeScope(listOf(packageName)) }
        refreshNow()
        return if (result.isSuccess) {
            ScopeChangeResult.ScopeRemoved
        } else {
            ScopeChangeResult.Failed.RemoveFailed
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

        /** 前台兜底同步间隔:30s(远低于旧 5s 轮询;配合内容判等,UI 无感知)。 */
        const val FOREGROUND_SYNC_INTERVAL_MS = 30_000L

        val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    }
}