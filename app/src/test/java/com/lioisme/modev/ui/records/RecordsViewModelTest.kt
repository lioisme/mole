package com.lioisme.modev.ui.records

import androidx.room.Room
import com.lioisme.modev.core.model.CryptoKind
import com.lioisme.modev.core.model.CryptoOperation
import com.lioisme.modev.core.model.RecordType
import com.lioisme.modev.core.model.SqlKind
import com.lioisme.modev.data.db.CryptoRecordEntity
import com.lioisme.modev.data.db.ModevDatabase
import com.lioisme.modev.data.repo.LogRepository
import com.lioisme.modev.data.repo.RecordFilter
import com.lioisme.modev.data.server.ConfigServer
import java.net.ServerSocket
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * RecordsViewModel 统一契约(V23 T4.1/D6):
 * - 预置语境锁定(单应用页):类型/包名固定,类别过滤生效;
 * - 全局页:跨类型聚合 + 应用多选 + 类型切换后类别失效;
 * - totalCount 与分页内容对账。
 * 内存 Room + 真实 LogRepository,数据流与生产一致。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecordsViewModelTest {

    private lateinit var db: ModevDatabase
    private lateinit var repo: LogRepository
    private lateinit var server: ConfigServer

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            ModevDatabase::class.java,
        ).build()
        server = ConfigServer(ServerSocket(0).use { it.localPort })
        repo = LogRepository(db.cryptoRecordDao(), server)
    }

    @After
    fun tearDown() {
        db.close()
        server.close()
    }

    private fun insert(
        packageName: String,
        kind: String,
        operation: String,
        recordType: String,
        timestamp: Long,
    ) = runBlocking {
        db.cryptoRecordDao().insertReturningId(
            CryptoRecordEntity(
                packageName = packageName,
                kind = kind,
                operation = operation,
                algorithm = if (recordType == "db") "test.db" else "AES",
                threadName = "main",
                timestamp = timestamp,
                recordType = recordType,
            ),
        )
    }

    /** 轮询 totalCount 直至满足谓词(主 Looper 泵动 + 真实超时)。 */
    private fun awaitTotal(vm: RecordsViewModel, predicate: (Long) -> Boolean): Long {
        val deadline = System.currentTimeMillis() + 5_000
        var last = -1L
        while (System.currentTimeMillis() < deadline) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            last = runBlocking { vm.totalCount.first() }
            if (predicate(last)) return last
            Thread.sleep(20)
        }
        return last
    }

    @Test
    fun `preset context locks type and package`() = runBlocking {
        seedTwoAppsTwoTypes()
        val vm = RecordsViewModel(
            logs = repo,
            presetTypes = setOf(RecordType.CRYPTO.wire),
            presetPackages = setOf("com.a"),
        )

        val total = awaitTotal(vm) { it == 1L }

        assertEquals(1L, total)
    }

    @Test
    fun `global page aggregates all types and filters by packages`() = runBlocking {
        seedTwoAppsTwoTypes()
        val vm = RecordsViewModel(logs = repo)

        assertEquals(4L, awaitTotal(vm) { it == 4L })

        vm.setPackages(setOf("com.a", "com.b"))
        assertEquals(4L, awaitTotal(vm) { it == 4L })

        vm.setPackages(setOf("com.a"))
        assertEquals(2L, awaitTotal(vm) { it == 2L })

        vm.setPackages(emptySet())
        assertEquals(4L, awaitTotal(vm) { it == 4L })
    }

    @Test
    fun `type filter narrows across types`() = runBlocking {
        seedTwoAppsTwoTypes()
        val vm = RecordsViewModel(logs = repo)

        vm.setTypeFilter(RecordType.DATABASE)
        assertEquals(2L, awaitTotal(vm) { it == 2L })

        vm.setTypeFilter(null)
        assertEquals(4L, awaitTotal(vm) { it == 4L })
    }

    @Test
    fun `kind only applies when single type selected`() = runBlocking {
        seedTwoAppsTwoTypes()
        val vm = RecordsViewModel(logs = repo)

        // 全部类型下类别被 D6 归一化忽略(即使误下发也不产生零结果)
        vm.setTypeFilter(null)
        vm.setKindName(CryptoKind.CIPHER.name)
        assertEquals(4L, awaitTotal(vm) { it == 4L })

        // 单选算法后类别生效:a 的 CIPHER 1 条(b 是 MESSAGE_DIGEST)
        vm.setTypeFilter(RecordType.CRYPTO)
        vm.setKindName(CryptoKind.CIPHER.name)
        assertEquals(1L, awaitTotal(vm) { it == 1L })

        // 切回全部类型:kind 自动失效
        vm.setTypeFilter(null)
        assertEquals(4L, awaitTotal(vm) { it == 4L })
    }

    @Test
    fun `sql kind filter works on database type`() = runBlocking {
        seedTwoAppsTwoTypes()
        val vm = RecordsViewModel(logs = repo)
        vm.setTypeFilter(RecordType.DATABASE)
        vm.setKindName(SqlKind.DML.name)
        assertEquals(1L, awaitTotal(vm) { it == 1L })
    }

    private suspend fun seedTwoAppsTwoTypes() {
        insert("com.a", CryptoKind.CIPHER.name, CryptoOperation.INIT.name, "crypto", 1000)
        insert("com.b", CryptoKind.MESSAGE_DIGEST.name, CryptoOperation.DIGEST.name, "crypto", 2000)
        insert("com.a", SqlKind.QUERY.name, "QUERY", "db", 3000)
        insert(
            "com.b",
            SqlKind.DML.name,
            "INSERT",
            "db",
            4000,
        )
    }
}
