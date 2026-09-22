package com.lioisme.modev.ui.records

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lioisme.modev.core.model.RecordTypes
import com.lioisme.modev.data.db.CryptoRecordEntity
import com.lioisme.modev.data.db.ModevDatabase
import com.lioisme.modev.data.repo.AppCatalogRepository
import com.lioisme.modev.domain.app.AppCatalogService
import com.lioisme.modev.data.repo.LogRepository
import com.lioisme.modev.data.server.ConfigServer
import com.lioisme.modev.domain.app.AppInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/**
 * RecordsViewModel 双形态契约(V40 T4,Robolectric,协程模式同 AppsViewModelTest):
 * - 全局形态(presetPackages=null):按类型过滤跨全部应用 + 清空 = 该类型全部应用;
 * - 单应用形态(presetPackages 非空):回归红线——过滤/清空锁定单应用;
 * - packageLabels 仅全局形态接入目录源,单应用形态恒空。
 *
 * 协程纪律(V44 D1 计数改 Room Flow 后强化):`totalCount` 与 `packageLabels` 均为
 * `stateIn(WhileSubscribed)` 的 Main 域 StateFlow,**禁止** `runBlocking { …first() }`
 * ——runBlocking 占住主线程会让 Main 派发的上游永不推进(实测整套测试死锁)。
 * 正解 = 独立收集器激活上游 + `ShadowLooper.idleMainLooper()` 泵动 + 读 `.value`。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecordsViewModelGlobalTest {

    private lateinit var db: ModevDatabase
    private lateinit var logs: LogRepository
    private val collectors = mutableListOf<Job>()

    private class FakeCatalog : AppCatalogService {
        val appsState = MutableStateFlow(
            listOf(
                AppInfo("pkg.a", "App A", system = false),
                AppInfo("pkg.b", "App B", system = false),
            ),
        )
        override val apps: StateFlow<List<AppInfo>> = appsState
        override val loading: StateFlow<Boolean> = MutableStateFlow(false)
        override fun refresh() = Unit
        override suspend fun resolveLaunchable(packageName: String): Boolean = false
    }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ModevDatabase::class.java,
        ).build()
        logs = LogRepository(db.cryptoRecordDao(), ConfigServer())
    }

    @After
    fun tearDown() {
        collectors.forEach { it.cancel() }
        db.close()
    }

    private fun seed() {
        val dao = db.cryptoRecordDao()
        fun e(type: String, pkg: String) = CryptoRecordEntity(
            recordType = type,
            packageName = pkg,
            kind = "DIGEST",
            operation = "DIGEST",
            algorithm = "MD5",
            threadName = "main",
        )
        runBlocking {
            dao.insertAll(listOf(e(RecordTypes.CRYPTO, "pkg.a"), e(RecordTypes.CRYPTO, "pkg.b")))
            dao.insertAll(listOf(e(RecordTypes.DATABASE, "pkg.a")))
        }
    }

    /** 构造 VM 并激活 `totalCount` 上游(WhileSubscribed 需订阅才启动)。 */
    private fun vm(
        presetTypes: Set<String>,
        presetPackages: Set<String>?,
        catalog: AppCatalogService? = null,
    ): RecordsViewModel {
        val viewModel = RecordsViewModel(logs, presetTypes, presetPackages, catalog)
        collectors += CoroutineScope(Dispatchers.Main.immediate).launch { viewModel.totalCount.collect {} }
        ShadowLooper.idleMainLooper()
        return viewModel
    }

    /** 有界轮询等待 count 达到期望值(首帧未达时 StateFlow 值为 null)。 */
    private fun awaitCount(viewModel: RecordsViewModel, expected: Long): Long {
        val deadline = System.currentTimeMillis() + 5_000
        var current = -1L
        while (System.currentTimeMillis() < deadline) {
            ShadowLooper.idleMainLooper()
            val value = viewModel.totalCount.value
            if (value != null) {
                current = value
                if (value == expected) return value
            }
            Thread.sleep(20)
        }
        assertEquals("count 未达期望", expected, current)
        return current
    }

    /** 有界轮询等待 clearCurrentScope 回调落定(Main.immediate 协程,主 looper 推进)。 */
    private fun awaitDeleted(viewModel: RecordsViewModel, expected: Long) {
        var deleted = -1L
        viewModel.clearCurrentScope { deleted = it }
        val deadline = System.currentTimeMillis() + 5_000
        while (deleted == -1L && System.currentTimeMillis() < deadline) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(20)
        }
        assertEquals("清空回调未落定或删除数不符", expected, deleted)
    }

    @Test
    fun globalForm_filtersByTypeAcrossAllApps_andClearsWholeType() {
        seed()
        val viewModel = vm(presetTypes = setOf(RecordTypes.CRYPTO), presetPackages = null)
        awaitCount(viewModel, expected = 2L)
        awaitDeleted(viewModel, expected = 2L)
        // 过滤视角归零(该类型全部应用已清);他类型行不受波及(直查对账)。
        awaitCount(viewModel, expected = 0L)
        assertEquals(
            "DATABASE 行必须存活",
            1L,
            runBlocking {
                db.cryptoRecordDao().observeRecordTypeCounts().first()
                    .first { it.recordType == RecordTypes.DATABASE }.count
            },
        )
    }

    @Test
    fun perAppForm_staysScopedToPackage() {
        seed()
        val viewModel = vm(presetTypes = setOf(RecordTypes.CRYPTO), presetPackages = setOf("pkg.a"))
        awaitCount(viewModel, expected = 1L)
        awaitDeleted(viewModel, expected = 1L)
        awaitCount(viewModel, expected = 0L)
        // pkg.b 的同类记录不受波及。
        val other = vm(presetTypes = setOf(RecordTypes.CRYPTO), presetPackages = setOf("pkg.b"))
        awaitCount(other, expected = 1L)
    }

    @Test
    fun packageLabels_onlyGlobalFormWired() {
        seed()
        val catalog = FakeCatalog()
        val global = vm(setOf(RecordTypes.CRYPTO), presetPackages = null, catalog = catalog)
        // packageLabels = stateIn(WhileSubscribed):持续收集使上游推进(同 AppsViewModelTest)。
        collectors += CoroutineScope(Dispatchers.Main.immediate).launch { global.packageLabels.collect {} }
        val deadline = System.currentTimeMillis() + 5_000
        while (global.packageLabels.value.isEmpty() && System.currentTimeMillis() < deadline) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(20)
        }
        assertEquals("App A", global.packageLabels.value["pkg.a"])
        assertEquals("App B", global.packageLabels.value["pkg.b"])

        val perApp = vm(setOf(RecordTypes.CRYPTO), presetPackages = setOf("pkg.a"))
        assertTrue(perApp.packageLabels.value.isEmpty())
    }
}
