package com.lioisme.modev.data.repo

import androidx.room.Room
import com.lioisme.modev.core.model.CryptoKind
import com.lioisme.modev.core.model.CryptoOperation
import com.lioisme.modev.core.model.CryptoRecord
import com.lioisme.modev.core.model.RecordType
import com.lioisme.modev.core.model.RecordTypes
import com.lioisme.modev.core.model.SqlKind
import com.lioisme.modev.core.model.SqlOperation
import com.lioisme.modev.core.model.SqlRecord
import com.lioisme.modev.data.db.ModevDatabase
import com.lioisme.modev.data.server.ConfigServer
import com.lioisme.modev.domain.record.RecordFilter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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

/**
 * 单包按类型分组计数对账(V45 ST-6 回归门禁):
 * `observeTypeCountsForPackage` 的「一次 GROUP BY」必须与逐类型 `observeRecordCounts`
 * **逐键等值**,否则监控页 10 个徽标与 MCP `app_monitor` 在收敛为单查询后静默改数;
 * 计数口径同时与 [RecordService.count] 的总量对账(同谓词单一事实源)。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecordTypeCountParityTest {

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

    private fun crypto(pkg: String, kind: CryptoKind, operation: CryptoOperation, ts: Long) = CryptoRecord(
        packageName = pkg,
        kind = kind,
        operation = operation,
        algorithm = "AES",
        threadName = "main",
        timestamp = ts,
    )

    private fun sql(pkg: String, ts: Long) = SqlRecord(
        packageName = pkg,
        kind = SqlKind.QUERY,
        operation = SqlOperation.QUERY,
        databaseName = "db-$pkg",
        threadName = "main",
        timestamp = ts,
    )

    @Test
    fun `grouped per-package type counts equal the per-type queries`() = runBlocking {
        repo.create(crypto("pkg.a", CryptoKind.CIPHER, CryptoOperation.ENCRYPT, 100))
        repo.create(crypto("pkg.a", CryptoKind.MAC, CryptoOperation.SIGN, 200))
        repo.create(crypto("pkg.a", CryptoKind.CIPHER, CryptoOperation.DECRYPT, 300))
        repo.create(sql("pkg.a", 400))
        repo.create(crypto("pkg.b", CryptoKind.CIPHER, CryptoOperation.ENCRYPT, 500))

        val grouped = repo.observeTypeCountsForPackage("pkg.a").first()
        assertEquals(2, grouped.size)
        assertEquals(3L, grouped[RecordTypes.CRYPTO])
        assertEquals(1L, grouped[RecordTypes.DATABASE])

        // 逐键与旧口径(每类型一次查询)完全一致
        for (type in RecordType.entries) {
            val perType = repo.observeRecordCounts(type.wire).first()["pkg.a"] ?: 0L
            assertEquals(
                "类型 ${type.wire} 分组计数与逐类型计数不一致",
                perType,
                grouped[type.wire] ?: 0L,
            )
        }
        // 总量对账:分组和 == 同过滤条件 COUNT(*)(谓词单一事实源)
        val total = repo.count(RecordFilter(packages = setOf("pkg.a"))).first()
        assertEquals(total, grouped.values.sum())
    }

    @Test
    fun `zero count types are absent and other packages never leak`() = runBlocking {
        repo.create(crypto("pkg.a", CryptoKind.CIPHER, CryptoOperation.ENCRYPT, 100))

        val grouped = repo.observeTypeCountsForPackage("pkg.a").first()
        assertTrue("缺席类型不落零键(投影侧 ?: 0L 承载)", !grouped.containsKey(RecordTypes.BEHAVIOR))
        assertEquals(0L, grouped[RecordTypes.BEHAVIOR] ?: 0L)
        assertTrue("无记录包的分组计数恒为空", repo.observeTypeCountsForPackage("pkg.z").first().isEmpty())

        repo.create(sql("pkg.b", 200))
        assertEquals(1, repo.observeTypeCountsForPackage("pkg.a").first().size)
        assertFalse(
            "pkg.a 不应看到 pkg.b 的 database 记录",
            repo.observeTypeCountsForPackage("pkg.a").first().containsKey(RecordTypes.DATABASE),
        )
    }
}
