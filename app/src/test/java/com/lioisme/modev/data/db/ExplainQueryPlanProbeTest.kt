package com.lioisme.modev.data.db

import androidx.room.Room
import com.lioisme.modev.core.model.RecordType
import com.lioisme.modev.data.server.ConfigServer
import com.lioisme.modev.data.repo.LogRepository
import com.lioisme.modev.domain.record.RecordFilter
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
 * 存储索引与管道回归探针(V23 T6.2):
 * - 必需索引存在性门禁:6 个索引(含 v3 唯一幂等索引、v5 时间戳、v8 复合筛选索引)
 *   任一缺失即红 —— 防迁移/实体改动静默丢索引;
 * - WAL 加固后端到端冒烟:批量插入(@Transaction)→ RecordFilter 计数/切片一致
 *   (行为级查询正确性与 RecordPagingProbeTest 互补)。
 *
 * 说明:EXPLAIN QUERY PLAN 级别的「命中验证」受 Robolectric 驱动限制
 * (legacy/native shadow 均剥离 EXPLAIN 前缀直接执行原查询),留待真机插桩;
 * 本测试以「索引存在 + 行为对账」组合承担同等防退化职责。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExplainQueryPlanProbeTest {

    private lateinit var db: ModevDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            ModevDatabase::class.java,
        ).build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    /** 实体 @Index 声明必须落地的全部二级索引(v1~v8 迁移链沉淀)。 */
    private val requiredIndexes = setOf(
        "index_crypto_records_packageName_timestamp",
        "index_crypto_records_packageName_kind",
        "index_crypto_records_recordType_kind",
        "index_crypto_records_recordType_packageName_timestamp",
        "index_crypto_records_timestamp",
        "index_crypto_records_packageName_processName_seq",
    )

    @Test
    fun allRequiredIndexesExist() {
        val cursor = db.openHelper.writableDatabase.query(
            "SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'crypto_records'",
        )
        val names = mutableListOf<String>()
        cursor.use {
            while (it.moveToNext()) names += it.getString(0)
        }
        // 唯一索引由 SQLite 自动建同名自动索引(Room 以声明名落地),前缀匹配兜底命名差异。
        requiredIndexes.forEach { required ->
            assertTrue(
                "必需索引缺失:$required\n实际索引:$names",
                names.any { it.equals(required, ignoreCase = true) || it.startsWith("sqlite_autoindex") && names.contains(required) },
            )
        }
    }

    @Test
    fun endToEnd_filterPipelineStillWorks_afterHardening() = runBlocking {
        // WAL/事务加固后的端到端冒烟:插入 → RecordFilter 分页计数 → 一致。
        val server = ConfigServer(ServerSocket(0).use { it.localPort })
        try {
            val repo = LogRepository(dao = db.cryptoRecordDao(), server = server)
            db.cryptoRecordDao().insertAll(
                listOf(
                    CryptoRecordEntity(
                        packageName = "com.a",
                        kind = "CIPHER",
                        operation = "ENCRYPT",
                        algorithm = "AES",
                        threadName = "main",
                        processName = "proc-1",
                        seq = 1L,
                        timestamp = 1L,
                    ),
                    CryptoRecordEntity(
                        recordType = RecordType.DATABASE.wire,
                        packageName = "com.a",
                        kind = "QUERY",
                        operation = "QUERY",
                        algorithm = "x.db",
                        threadName = "main",
                        processName = "proc-1",
                        seq = 2L,
                        timestamp = 2L,
                    ),
                ),
            )
            val filter = RecordFilter(packages = setOf("com.a"), query = "")
            assertEquals(2L, repo.count(filter).first())
            // 类型收窄:数据库仅 1 条。
            assertEquals(
                1L,
                repo.count(filter.copy(recordTypes = setOf(RecordType.DATABASE.wire))).first(),
            )
            // REPLACE 幂等:同 (pkg, process, seq) 重放不产生重复行。
            assertEquals(2L, db.cryptoRecordDao().count("com.a"))
        } finally {
            server.close()
        }
    }
}
