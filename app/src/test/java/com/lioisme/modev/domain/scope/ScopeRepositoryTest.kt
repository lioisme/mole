package com.lioisme.modev.domain.scope

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ScopeRepository 状态机单测:覆盖服务连接/断开、作用域读取、请求批准/拒绝/超时、
 * 移除、未就绪防护、外部变更刷新与变更串行化。
 */
class ScopeRepositoryTest {

    private class FakeScopeService(
        var scope: Set<String> = emptySet(),
        var failGetScope: Throwable? = null,
        // 真实挂起(模拟 binder 调用):让 Connecting 瞬时态可被观测
        private val getScopeDelayMillis: Long = 0L,
        var requestBehavior: (FakeScopeService, ScopeRequestCallback) -> Unit = { svc, cb ->
            // 模拟框架批准后把包加入作用域
            svc.scope = svc.scope + svc.requested.lastOrNull().orEmpty().toSet()
            cb.onApproved()
        },
    ) : ScopeService {
        val requested = mutableListOf<List<String>>()
        val removed = mutableListOf<List<String>>()

        override val frameworkName: String = "FakeLSPosed"
        override val frameworkVersion: String = "1.0"

        override suspend fun getScope(): List<String> {
            if (getScopeDelayMillis > 0L) delay(getScopeDelayMillis)
            failGetScope?.let { throw it }
            return scope.toList()
        }

        override fun requestScope(packages: List<String>, callback: ScopeRequestCallback) {
            requested += packages
            requestBehavior(this, callback)
        }

        override suspend fun removeScope(packages: List<String>) {
            removed += packages
            scope = scope - packages.toSet()
        }
    }

    private class FakeGateway(initial: ScopeService? = null) : ScopeGateway {
        private val flow = MutableStateFlow(initial)

        fun emit(service: ScopeService?) {
            flow.value = service
        }

        override fun serviceFlow(): Flow<ScopeService?> = flow
    }

    private fun newRepository(gateway: FakeGateway, timeoutMillis: Long = 30_000): ScopeRepository {
        val scope = kotlinx.coroutines.test.TestScope(UnconfinedTestDispatcher())
        return ScopeRepository(gateway, requestTimeoutMillis = timeoutMillis, stateScope = scope.backgroundScope)
    }

    @Test
    fun `no service - unavailable NO_SERVICE`() = runTest {
        val repo = newRepository(FakeGateway(null))
        assertEquals(ScopeState.UnavailableReason.NO_SERVICE, (repo.state.value as ScopeState.Unavailable).reason)
    }

    @Test
    fun `service connects - state becomes ready with scope`() = runTest {
        val gateway = FakeGateway(null)
        val repo = newRepository(gateway)
        gateway.emit(FakeScopeService(scope = setOf("com.a", "com.b")))
        val ready = repo.state.value as ScopeState.Ready
        assertEquals(setOf("com.a", "com.b"), ready.scope)
        assertTrue(ready.isScoped("com.a"))
        assertTrue(!ready.isScoped("com.c"))
        assertEquals("FakeLSPosed", ready.frameworkName)
    }

    @Test
    fun `getScope throws - unavailable SERVICE_ERROR`() = runTest {
        val gateway = FakeGateway(FakeScopeService(failGetScope = RuntimeException("module disabled")))
        val repo = newRepository(gateway)
        val unavailable = repo.state.value as ScopeState.Unavailable
        assertEquals(ScopeState.UnavailableReason.SERVICE_ERROR, unavailable.reason)
        assertEquals("module disabled", unavailable.message)
    }

    @Test
    fun `service dies - unavailable NO_SERVICE`() = runTest {
        val gateway = FakeGateway(FakeScopeService())
        val repo = newRepository(gateway)
        gateway.emit(null)
        assertEquals(ScopeState.UnavailableReason.NO_SERVICE, (repo.state.value as ScopeState.Unavailable).reason)
    }

    @Test
    fun `toggle add approved - scope updated`() = runTest {
        val gateway = FakeGateway(FakeScopeService())
        val repo = newRepository(gateway)
        val result = repo.toggle("com.target")
        assertEquals(ScopeChangeResult.ScopeRequested, result)
        val ready = repo.state.value as ScopeState.Ready
        assertTrue(ready.isScoped("com.target"))
    }

    @Test
    fun `toggle add rejected - user rejected and scope unchanged`() = runTest {
        val fake = FakeScopeService(requestBehavior = { _, cb -> cb.onFailed("scope request blocked") })
        val gateway = FakeGateway(fake)
        val repo = newRepository(gateway)
        val result = repo.toggle("com.target")
        assertEquals(ScopeChangeResult.Failed.UserRejected, result)
        assertEquals(emptySet<String>(), (repo.state.value as ScopeState.Ready).scope)
    }

    @Test
    fun `toggle add timeout - failed and scope unchanged`() = runTest {
        val fake = FakeScopeService(requestBehavior = { _, _ -> /* 永不回调 */ })
        val gateway = FakeGateway(fake)
        val repo = newRepository(gateway, timeoutMillis = 1_000)
        val result = repo.toggle("com.target")
        assertTrue(result is ScopeChangeResult.Failed)
        assertEquals(emptySet<String>(), (repo.state.value as ScopeState.Ready).scope)
    }

    @Test
    fun `toggle remove - scope updated`() = runTest {
        val fake = FakeScopeService(scope = setOf("com.target"))
        val gateway = FakeGateway(fake)
        val repo = newRepository(gateway)
        val result = repo.toggle("com.target")
        assertEquals(ScopeChangeResult.ScopeRemoved, result)
        assertTrue(!(repo.state.value as ScopeState.Ready).isScoped("com.target"))
        assertEquals(listOf(listOf("com.target")), fake.removed)
    }

    @Test
    fun `toggle while unavailable - failed`() = runTest {
        val repo = newRepository(FakeGateway(null))
        val result = repo.toggle("com.target")
        assertTrue(result is ScopeChangeResult.Failed)
    }

    @Test
    fun `refresh picks up external scope change`() = runTest {
        val fake = FakeScopeService(scope = setOf("com.a"))
        val gateway = FakeGateway(fake)
        val repo = newRepository(gateway)
        fake.scope = setOf("com.a", "com.external") // LSPosed 管理器侧变更
        repo.refresh()
        val ready = repo.state.value as ScopeState.Ready
        assertTrue(ready.isScoped("com.external"))
    }

    // ---- V10 稳态状态流(需求 1:UI 零闪烁)----

    @Test
    fun `refresh with no change does not emit - state instance stays stable`() = runTest {
        val fake = FakeScopeService(scope = setOf("com.a"))
        val gateway = FakeGateway(fake)
        val repo = newRepository(gateway)
        val before = repo.state.value

        // 连续刷新(前台兜底路径):数据未变 → 内容判等不发射,实例不变
        repeat(3) { repo.refresh() }
        assertTrue(repo.state.value === before)
        assertEquals(setOf("com.a"), (repo.state.value as ScopeState.Ready).scope)
    }

    @Test
    fun `refresh path never emits Connecting - only steady Ready states`() = runTest {
        val fake = FakeScopeService(scope = setOf("com.a"))
        val gateway = FakeGateway(fake)
        val repo = newRepository(gateway)

        val seen = mutableListOf<ScopeState>()
        val job = launch { repo.state.collect { seen += it } }
        testScheduler.advanceUntilIdle() // collect 启动并收到当前值

        // 前台兜底路径:多次无变化刷新 + 一次真实变更刷新
        repeat(2) { repo.refresh() }
        testScheduler.advanceUntilIdle()
        fake.scope = setOf("com.a", "com.b")
        repo.refresh()
        testScheduler.advanceUntilIdle()
        repo.refresh() // 再次无变化
        testScheduler.advanceUntilIdle()
        job.cancel()

        // 全程无 Connecting 中间态(旧 5s 轮询的 Ready→Connecting→Ready 翻转已消灭)
        assertTrue(seen.none { it is ScopeState.Connecting })
        // 无变化刷新不追加发射:序列 = 首连 Ready + 变更后 Ready,共 2 次
        assertEquals(2, seen.size)
        assertEquals(setOf("com.a"), (seen[0] as ScopeState.Ready).scope)
        assertEquals(setOf("com.a", "com.b"), (seen[1] as ScopeState.Ready).scope)
    }

    @Test
    fun `service connect emits Connecting transiently then steady Ready`() = runTest {
        val gateway = FakeGateway(null) // 服务未连接:lastEmitted = Unavailable

        // 共享 runTest 调度器:backgroundScope 内 delay(挂起式 fake)由 testScheduler 推进,
        // 使 Connecting 与 Ready 分两次发射(否则 StateFlow 同步合流吞掉瞬时态)。
        val sharedScope = kotlinx.coroutines.test.TestScope(UnconfinedTestDispatcher(testScheduler))
        val repo = ScopeRepository(gateway, stateScope = sharedScope.backgroundScope)

        val seen = mutableListOf<ScopeState>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { repo.state.collect { seen += it } }

        // 首连:允许一次性 Connecting,之后保持 Ready 稳态
        // (getScopeDelayMillis 使 getScope 真实挂起,Connecting 与 Ready 分次发射;
        // 共享调度器下 delay 任务需显式推进时间)
        gateway.emit(FakeScopeService(scope = setOf("com.a", "com.b"), getScopeDelayMillis = 1L))
        testScheduler.advanceTimeBy(1_000)
        testScheduler.runCurrent()
        repo.refresh() // 同框架无变化刷新
        testScheduler.runCurrent()
        job.cancel()

        assertEquals(1, seen.count { it is ScopeState.Connecting })
        assertEquals(setOf("com.a", "com.b"), (seen.last() as ScopeState.Ready).scope)
        // 首连后无变化刷新不再追加发射
        assertEquals(3, seen.size) // Unavailable + Connecting + Ready(a,b)
    }

    @Test
    fun `toggle request not yet answered - mutex serializes concurrent calls`() = runTest {
        val fake = FakeScopeService(requestBehavior = { _, _ -> /* 挂起 */ })
        val gateway = FakeGateway(fake)
        val repo = newRepository(gateway, timeoutMillis = 5_000)
        val first = async { repo.toggle("com.a") }
        val second = async { repo.toggle("com.b") }
        // 互斥串行:第一次调用超时后,第二次才开始
        val r1 = first.await()
        val r2 = second.await()
        assertTrue(r1 is ScopeChangeResult.Failed)
        assertTrue(r2 is ScopeChangeResult.Failed)
        assertEquals(listOf(listOf("com.a"), listOf("com.b")), fake.requested)
    }

    @Test
    fun `remove is serialized with refresh and stays consistent`() = runTest {
        val fake = FakeScopeService(scope = setOf("com.a"))
        val gateway = FakeGateway(fake)
        val repo = newRepository(gateway)
        // 并发:移除 + 外部刷新(前台轮询路径)共用 mutationMutex,读-改-读串行
        val remove = async { repo.remove("com.a") }
        val refresh = async { repo.refresh() }
        assertTrue(remove.await() is ScopeChangeResult.ScopeRemoved)
        refresh.await()
        val ready = repo.state.value as ScopeState.Ready
        assertTrue(!ready.isScoped("com.a"))
        assertEquals(listOf(listOf("com.a")), fake.removed)
    }
}
