package com.lioisme.modev.data.repo

import androidx.room.Room
import com.lioisme.modev.core.model.RecordType
import com.lioisme.modev.core.model.RecordTypes
import com.lioisme.modev.data.db.ModevDatabase
import com.lioisme.modev.domain.record.CryptoDetail
import com.lioisme.modev.data.server.ConfigServer
import com.lioisme.modev.core.model.CryptoKind
import com.lioisme.modev.core.model.CryptoOperation
import com.lioisme.modev.core.model.CryptoRecord
import com.lioisme.modev.core.model.MonitoredRecord
import com.lioisme.modev.core.model.SqlKind
import com.lioisme.modev.core.model.SqlOperation
import com.lioisme.modev.core.model.SqlRecord
import com.lioisme.modev.domain.record.RecordFilter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * RecordService 契约(T5 专业门面):增→查→改→删闭环、deleteByFilter 删除数 == 前置
 * count(共享 WHERE)、queryPage 与 observe 计数一致、categoryCounts 与 count 一致、
 * 空 kind/operation 交集短路为零、时间范围与 operation 过滤穿透。
 * V38 D1 → V45 T2.5:写入面 = 类型化记录族 MonitoredRecord(domain 零 data.db 依赖,
 * 且不再另立逐列镜像实体的第三份平铺副本)。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecordServiceCrudTest {

    private lateinit var db: ModevDatabase
    private lateinit var repo: LogRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            ModevDatabase::class.java,
        ).build()
        repo = LogRepository(db.cryptoRecordDao(), ConfigServer())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun entity(
        packageName: String,
        kind: String,
        operation: String,
        algorithmOrDbName: String,
        timestamp: Long,
        recordType: String = RecordTypes.CRYPTO,
    ): MonitoredRecord = if (recordType == RecordTypes.DATABASE) {
        SqlRecord(
            packageName = packageName,
            kind = SqlKind.valueOf(kind),
            operation = SqlOperation.valueOf(operation),
            databaseName = algorithmOrDbName,
            threadName = "main",
            processName = "proc-$packageName-$kind-$timestamp",
            timestamp = timestamp,
        )
    } else {
        CryptoRecord(
            packageName = packageName,
            kind = CryptoKind.valueOf(kind),
            operation = CryptoOperation.valueOf(operation),
            algorithm = algorithmOrDbName,
            threadName = "main",
            processName = "proc-$packageName-$kind-$timestamp",
            timestamp = timestamp,
        )
    }

    @Test
    fun create_get_update_delete_lifecycle() = runBlocking {
        val id = repo.create(entity("pkg.a", "CIPHER", "ENCRYPT", "AES", 100))
        assertTrue(id > 0)

        val detail = repo.get(id).first()
        assertNotNull(detail)
        assertEquals("AES", (detail as CryptoDetail).record.algorithm)

        repo.update(id, entity("pkg.a", "CIPHER", "ENCRYPT", "AES-256", 100))
        assertEquals("AES-256", (repo.get(id).first() as CryptoDetail).record.algorithm)

        assertTrue(repo.delete(id))
        assertNull(repo.get(id).first())
        assertFalse(repo.delete(id)) // 重复删除
    }

    @Test
    fun deleteByFilter_matches_count() = runBlocking {
        repo.create(entity("pkg.a", "CIPHER", "ENCRYPT", "AES", 100))
        repo.create(entity("pkg.a", "MESSAGE_DIGEST", "DIGEST", "SHA-256", 200))
        repo.create(
            entity(
                "pkg.b",
                SqlKind.OPEN.name,
                SqlOperation.OPEN_DATABASE.name,
                "db-b",
                300,
                recordType = RecordTypes.DATABASE,
            ),
        )

        assertEquals(3L, repo.count(RecordFilter()).first())
        // 仅删 crypto 类型:删除数 == 前置 crypto 计数
        val cryptoBefore = repo.count(RecordFilter(recordTypes = setOf(RecordTypes.CRYPTO))).first()
        val deleted = repo.deleteByFilter(RecordFilter(recordTypes = setOf(RecordTypes.CRYPTO)))
        assertEquals(cryptoBefore, deleted)
        assertEquals(1L, repo.count(RecordFilter()).first())
    }

    @Test
    fun deleteByFilter_no_constraint_is_noop() = runBlocking {
        repo.create(entity("pkg.a", "CIPHER", "ENCRYPT", "AES", 100))
        assertEquals(0L, repo.deleteByFilter(RecordFilter())) // 空约束拒绝整表误删
        assertEquals(1L, repo.count(RecordFilter()).first())
    }

    @Test
    fun empty_kind_intersection_short_circuits() = runBlocking {
        repo.create(entity("pkg.a", "CIPHER", "ENCRYPT", "AES", 100))
        // 单类型 + 跨类型 kind → 空集 → 零匹配且零误删
        val filter = RecordFilter(recordTypes = setOf(RecordTypes.CRYPTO), kinds = setOf("OPEN"))
        assertEquals(0L, repo.count(filter).first())
        assertEquals(0L, repo.deleteByFilter(filter))
        assertEquals(1L, repo.count(RecordFilter()).first())
    }

    @Test
    fun queryPage_matches_count_and_paginates() = runBlocking {
        for (i in 1..5) repo.create(entity("pkg.a", "CIPHER", "ENCRYPT", "AES$i", i.toLong()))
        val total = repo.count(RecordFilter()).first()
        assertEquals(5L, total)

        val p0 = repo.queryPage(RecordFilter(), 0, 2)
        assertEquals(5L, p0.total)
        assertEquals(2, p0.items.size)
        val p2 = repo.queryPage(RecordFilter(), 2, 2)
        assertEquals(1, p2.items.size)
        // 经记录类型约束:仅 crypto(全部) → 总数仍 5(单类型库)
        val pType = repo.queryPage(RecordFilter(recordTypes = setOf(RecordTypes.CRYPTO)), 0, 10)
        assertEquals(5, pType.items.size)
    }

    @Test
    fun categoryCounts_matches_count() = runBlocking {
        // 种子数据须为合法枚举名(强约束 9:生产链路经 Codec 严格校验,
        // kind 列恒为 CryptoKind.name;MCP 导入面同样应遵守该契约)。
        repo.create(entity("pkg.a", "CIPHER", "ENCRYPT", "AES", 100))
        repo.create(entity("pkg.a", "CIPHER", "ENCRYPT", "AES2", 200))
        repo.create(entity("pkg.a", "MAC", "SIGN", "HmacSHA256", 300))
        val counts = repo.categoryCounts(RecordType.CRYPTO)
        assertEquals(2L, counts["CIPHER"])
        assertEquals(1L, counts["MAC"])
        assertEquals(3L, counts.values.sum())
    }

    @Test
    fun time_range_and_operation_filter() = runBlocking {
        repo.create(entity("pkg.a", "CIPHER", "ENCRYPT", "AES", 100))
        repo.create(entity("pkg.a", "CIPHER", "ENCRYPT", "AES", 200))
        repo.create(entity("pkg.a", "CIPHER", "DECRYPT", "AES", 300))

        assertEquals(1L, repo.count(RecordFilter(timeRange = 150L..250L)).first())
        assertEquals(2L, repo.count(RecordFilter(operations = setOf("ENCRYPT"))).first())
        assertEquals(
            1L,
            repo.count(RecordFilter(operations = setOf("ENCRYPT"), timeRange = 150L..250L)).first(),
        )
    }
}
