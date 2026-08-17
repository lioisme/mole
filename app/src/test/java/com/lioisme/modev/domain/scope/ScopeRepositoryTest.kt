package com.lioisme.modev.domain.scope

import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
    fun `toggle add rejected - failed with message and scope unchanged`() = runTest {
        val fake = FakeScopeService(requestBehavior = { _, cb -> cb.onFailed("scope request blocked") })
        val gateway = FakeGateway(fake)
        val repo = newRepository(gateway)
        val result = repo.toggle("com.target")
        assertEquals(ScopeChangeResult.Failed("scope request blocked"), result)
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