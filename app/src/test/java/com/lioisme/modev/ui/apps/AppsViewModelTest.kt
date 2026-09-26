package com.lioisme.modev.ui.apps

import androidx.room.Room
import com.lioisme.modev.data.db.ModevDatabase
import com.lioisme.modev.data.repo.AppCatalogRepository
import com.lioisme.modev.domain.app.AppCatalogService
import com.lioisme.modev.data.repo.AppListSettings
import com.lioisme.modev.data.repo.LogRepository
import com.lioisme.modev.data.server.ConfigServer
import com.lioisme.modev.domain.app.AppInfo
import com.lioisme.modev.domain.scope.ScopeGateway
import com.lioisme.modev.domain.scope.ScopeRepository
import com.lioisme.modev.domain.server.ConnectionMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/**
 * AppsViewModel 契约(V16 R4 加载态):loading 随目录源传播(首帧 progress 依据,
 * 防空态误显);行投影(label/作用域/连接/计数)不受 loading 干扰。
 * 依赖全部走接口替身:AppCatalogSource 假实现 + 空内存 Room 的真实 LogRepository +
 * 无服务 ScopeGateway(作用域不可用) + 空连接集 ConnectionMonitor。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppsViewModelTest {

    private lateinit var db: ModevDatabase
    private lateinit var logs: LogRepository
    private lateinit var fakeCatalog: FakeCatalog
    private lateinit var vm: AppsViewModel
    private lateinit var collector: kotlinx.coroutines.Job

    private class FakeCatalog : AppCatalogService {
        val appsState = MutableStateFlow<List<AppInfo>>(emptyList())
        val loadingState = MutableStateFlow(false)
        override val apps: StateFlow<List<AppInfo>> = appsState
        override val loading: StateFlow<Boolean> = loadingState
        override fun refresh() = Unit
        override suspend fun resolveLaunchable(packageName: String): Boolean = true
    }

    private class FakeConnection : ConnectionMonitor {
        override val connectedPackagesFlow: StateFlow<Set<String>> =
            MutableStateFlow(setOf("com.online.app"))
        override val boundAddress: StateFlow<String?> = MutableStateFlow(null)
        override val bindError: StateFlow<String?> = MutableStateFlow(null)
    }

    private class NoServiceGateway : ScopeGateway {
        override fun serviceFlow(): Flow<com.lioisme.modev.domain.scope.ScopeService?> = flowOf(null)
    }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            ModevDatabase::class.java,
        ).build()
        logs = LogRepository(db.cryptoRecordDao(), ConfigServer())
        fakeCatalog = FakeCatalog()
        fakeCatalog.appsState.value = listOf(
            AppInfo("com.online.app", "Online App", system = false),
            AppInfo("com.sys.app", "Sys App", system = true),
        )
        vm = AppsViewModel(
            catalog = fakeCatalog,
            listPrefs = AppListSettings,
            logs = logs,
            server = FakeConnection(),
            scopeRepository = ScopeRepository(NoServiceGateway()),
        )
        // WhileSubscribed 流需订阅才启动上游;持续收集使 combine 推进(stateIn 值生效)
        collector = CoroutineScope(Dispatchers.Main.immediate).launch { vm.uiState.collect {} }
        ShadowLooper.idleMainLooper()
    }

    @After
    fun tearDown() {
        // AppListSettings 单例跨测试存活,复位模块显隐开关防串扰
        AppListSettings.setShowModules(false)
        db.close()
    }

    private fun awaitState(predicate: (AppsViewModel.UiState) -> Boolean): AppsViewModel.UiState {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            ShadowLooper.idleMainLooper()
            val state = vm.uiState.value
            if (predicate(state)) return state
            Thread.sleep(20)
        }
        return vm.uiState.value
    }

    @Test
    fun loadingPropagates_fromCatalog() {
        fakeCatalog.loadingState.value = true
        val loading = awaitState { it.loading }
        assertTrue("loading 须随目录源传播", loading.loading)
        // loading 期间行照常投影(不阻塞既有数据)
        assertEquals(2, loading.rows.size)

        fakeCatalog.loadingState.value = false
        val idle = awaitState { !it.loading }
        assertFalse(idle.loading)
    }

    @Test
    fun rowsProject_labelScopedConnectedAndCount() {
        val state = awaitState { it.rows.size == 2 }
        val online = state.rows.first { it.packageName == "com.online.app" }
        assertEquals("Online App", online.label)
        assertTrue(online.connected)
        assertFalse(online.scoped)
        assertEquals(0L, online.recordCount)
    }

    @Test
    fun scopeUnavailable_disablesScopeReady() {
        val state = awaitState { it.rows.size == 2 }
        assertFalse(state.scopeReady)
    }

    @Test
    fun modulesHiddenByDefault_andRevealedBySetting() {
        fakeCatalog.appsState.value = listOf(
            AppInfo("com.module.app", "Module App", system = false, isXposedModule = true),
            AppInfo("com.plain.app", "Plain App", system = false),
        )
        val hidden = awaitState {
            it.rows.any { r -> r.packageName == "com.plain.app" } &&
                it.rows.none { r -> r.packageName == "com.module.app" }
        }
        assertEquals(listOf("com.plain.app"), hidden.rows.map { it.packageName })

        AppListSettings.setShowModules(true)
        val shown = awaitState { it.rows.any { r -> r.packageName == "com.module.app" } }
        assertEquals(
            listOf("com.module.app", "com.plain.app"),
            shown.rows.map { it.packageName },
        )
    }
}
