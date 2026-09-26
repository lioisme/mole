package com.lioisme.modev.ui.records

import androidx.room.Room
import com.lioisme.modev.core.model.CryptoKind
import com.lioisme.modev.core.model.CryptoOperation
import com.lioisme.modev.core.model.RecordType
import com.lioisme.modev.core.model.SqlKind
import com.lioisme.modev.data.db.CryptoRecordEntity
import com.lioisme.modev.data.db.ModevDatabase
import com.lioisme.modev.data.repo.LogRepository
import com.lioisme.modev.data.server.ConfigServer
import java.net.ServerSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/**
 * RecordsViewModel 契约(V24 G2 收敛:预设语境必选,全局页退役):
 * - 类型/包名锁定(单应用两列表页);
 * - 查询词过滤 + 类别过滤生效(D6 由预设类型唯一性天然满足);
 * - 计数副标口径= `RecordService.count`(SQL COUNT),与筛选条件同步收敛。
 * 内存 Room + 真实 LogRepository + 真实 PagingSource,数据流与生产一致。
 *
 * 协程纪律(同 [com.lioisme.modev.ui.apps.AppsViewModelTest]):`totalCount` 为
 * `stateIn(WhileSubscribed)` 的 Main 域 StateFlow,**不得**用 `runBlocking { …first }`
 * 读取——runBlocking 占住主线程会让 Main 派发的上游永不推进(实测死锁)。
 * 正解 = 独立收集器激活上游 + `ShadowLooper.idleMainLooper` 泵动 + 读 `.value`。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecordsViewModelTest {

    private lateinit var db: ModevDatabase
    private lateinit var repo: LogRepository
    private lateinit var server: ConfigServer
    private val collectors = mutableListOf<Job>()

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
        collectors.forEach { it.cancel() }
        db.close()
        server.close()
    }

    private fun insert(
        packageName: String,
        kind: String,
        operation: String,
        recordType: String,
        timestamp: Long,
    ) {
        runBlocking {
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
    }

    /** 构造 VM 并激活 `totalCount` 上游(WhileSubscribed 需订阅才启动)。 */
    private fun vm(type: RecordType, pkg: String): RecordsViewModel {
        val viewModel = RecordsViewModel(
            logs = repo,
            presetTypes = setOf(type.wire),
            presetPackages = setOf(pkg),
        )
        collectors += CoroutineScope(Dispatchers.Main.immediate).launch { viewModel.totalCount.collect {} }
        ShadowLooper.idleMainLooper()
        return viewModel
    }

    /** 轮询 totalCount 直至满足谓词(主 Looper 泵动 + 真实超时);未达返回最后观测值。 */
    private fun awaitTotal(viewModel: RecordsViewModel, predicate: (Long) -> Boolean): Long {
        val deadline = System.currentTimeMillis() + 5_000
        var last = -1L
        while (System.currentTimeMillis() < deadline) {
            ShadowLooper.idleMainLooper()
            val current = viewModel.totalCount.value
            if (current != null) {
                last = current
                if (predicate(current)) return current
            }
            Thread.sleep(20)
        }
        return last
    }

    @Test
    fun `preset context locks type and package`() {
        seedTwoAppsTwoTypes()
        val viewModel = vm(RecordType.CRYPTO, "com.a")
        assertEquals(1L, awaitTotal(viewModel) { it == 1L })
    }

    @Test
    fun `query narrows within preset context`() {
        seedTwoAppsTwoTypes()
        val viewModel = vm(RecordType.DATABASE, "com.a")
        assertEquals(1L, awaitTotal(viewModel) { it == 1L })

        viewModel.setQuery("不存在的查询词")
        assertEquals(0L, awaitTotal(viewModel) { it == 0L })
    }

    @Test
    fun `kind filter applies within single preset type`() {
        seedTwoAppsTwoTypes()
        // a 的数据库侧仅 QUERY(b 是 DML);DML 过滤 → 0 条;QUERY 过滤 → 1 条。
        val viewModel = vm(RecordType.DATABASE, "com.a")
        viewModel.setKindName(SqlKind.DML.name)
        assertEquals(0L, awaitTotal(viewModel) { it == 0L })

        viewModel.setKindName(SqlKind.QUERY.name)
        assertEquals(1L, awaitTotal(viewModel) { it == 1L })
    }

    @Test
    fun `crypto kind filter works on crypto preset`() {
        seedTwoAppsTwoTypes()
        val viewModel = vm(RecordType.CRYPTO, "com.a")
        viewModel.setKindName(CryptoKind.CIPHER.name)
        assertEquals(1L, awaitTotal(viewModel) { it == 1L })
    }

    private fun seedTwoAppsTwoTypes() {
        insert("com.a", CryptoKind.CIPHER.name, CryptoOperation.INIT.name, "crypto", 1000)
        insert("com.b", CryptoKind.MESSAGE_DIGEST.name, CryptoOperation.DIGEST.name, "crypto", 2000)
        insert("com.a", SqlKind.QUERY.name, "QUERY", "db", 3000)
        insert("com.b", SqlKind.DML.name, "INSERT", "db", 4000)
    }
}
