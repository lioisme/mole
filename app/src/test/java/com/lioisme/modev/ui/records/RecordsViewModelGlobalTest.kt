package com.lioisme.modev.ui.records

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lioisme.modev.core.model.RecordTypes
import com.lioisme.modev.data.db.CryptoRecordEntity
import com.lioisme.modev.data.db.ModevDatabase
import com.lioisme.modev.data.repo.AppCatalogRepository
import com.lioisme.modev.data.repo.AppCatalogSource
import com.lioisme.modev.data.repo.LogRepository
import com.lioisme.modev.data.server.ConfigServer
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
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecordsViewModelGlobalTest {

    private lateinit var db: ModevDatabase
    private lateinit var logs: LogRepository
    private val collectors = mutableListOf<Job>()

    private class FakeCatalog : AppCatalogSource {
        val appsState = MutableStateFlow(
            listOf(
                AppCatalogRepository.AppInfo("pkg.a", "App A", system = false),
                AppCatalogRepository.AppInfo("pkg.b", "App B", system = false),
            ),
        )
        override val apps: StateFlow<List<AppCatalogRepository.AppInfo>> = appsState
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

    private suspend fun seed() {
        val dao = db.cryptoRecordDao()
        fun e(type: String, pkg: String) = CryptoRecordEntity(
            recordType = type,
            packageName = pkg,
            kind = "DIGEST",
            operation = "DIGEST",
            algorithm = "MD5",
            threadName = "main",
        )
        dao.insertAll(listOf(e(RecordTypes.CRYPTO, "pkg.a"), e(RecordTypes.CRYPTO, "pkg.b")))
        dao.insertAll(listOf(e(RecordTypes.DATABASE, "pkg.a")))
    }

    /** 有界轮询等待 count 达到期望值(Room 流异步首帧;totalCount 为冷流,first 直取)。 */
    private suspend fun awaitCount(vm: RecordsViewModel, expected: Long) {
        val deadline = System.currentTimeMillis() + 5_000
        var current = -1L
        while (System.currentTimeMillis() < deadline) {
            current = vm.totalCount.first()
            if (current == expected) return
            Thread.sleep(20)
        }
        assertEquals("count 未达期望", expected, current)
    }

    /** 有界轮询等待 clearCurrentScope 回调落定(Main.immediate 协程,主 looper 推进)。 */
    private fun awaitDeleted(vm: RecordsViewModel, expected: Long) {
        var deleted = -1L
        vm.clearCurrentScope { deleted = it }
        val deadline = System.currentTimeMillis() + 5_000
        while (deleted == -1L && System.currentTimeMillis() < deadline) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(20)
        }
        assertEquals("清空回调未落定或删除数不符", expected, deleted)
    }

    @Test
    fun globalForm_filtersByTypeAcrossAllApps_andClearsWholeType() = runBlocking {
        seed()
        val vm = RecordsViewModel(logs, presetTypes = setOf(RecordTypes.CRYPTO), presetPackages = null)
        awaitCount(vm, expected = 2L)
        awaitDeleted(vm, expected = 2L)
        // 过滤视角归零(该类型全部应用已清);他类型行不受波及(直查对账)。
        awaitCount(vm, expected = 0L)
        assertEquals(
            "DATABASE 行必须存活",
            1L,
            db.cryptoRecordDao().observeRecordTypeCounts().first().first { it.recordType == RecordTypes.DATABASE }.count,
        )
    }

    @Test
    fun perAppForm_staysScopedToPackage() = runBlocking {
        seed()
        val vm = RecordsViewModel(
            logs,
            presetTypes = setOf(RecordTypes.CRYPTO),
            presetPackages = setOf("pkg.a"),
        )
        awaitCount(vm, expected = 1L)
        awaitDeleted(vm, expected = 1L)
        awaitCount(vm, expected = 0L)
        // pkg.b 的同类记录不受波及。
        val other = RecordsViewModel(logs, setOf(RecordTypes.CRYPTO), presetPackages = setOf("pkg.b"))
        awaitCount(other, expected = 1L)
    }

    @Test
    fun packageLabels_onlyGlobalFormWired() = runBlocking {
        seed()
        val catalog = FakeCatalog()
        val global = RecordsViewModel(logs, setOf(RecordTypes.CRYPTO), presetPackages = null, catalog = catalog)
        // packageLabels = stateIn(WhileSubscribed):持续收集使上游推进(同 AppsViewModelTest)。
        collectors += CoroutineScope(Dispatchers.Main.immediate).launch { global.packageLabels.collect {} }
        val deadline = System.currentTimeMillis() + 5_000
        while (global.packageLabels.value.isEmpty() && System.currentTimeMillis() < deadline) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(20)
        }
        assertEquals("App A", global.packageLabels.value["pkg.a"])
        assertEquals("App B", global.packageLabels.value["pkg.b"])

        val perApp = RecordsViewModel(logs, setOf(RecordTypes.CRYPTO), presetPackages = setOf("pkg.a"))
        assertTrue(perApp.packageLabels.value.isEmpty())
    }
}
