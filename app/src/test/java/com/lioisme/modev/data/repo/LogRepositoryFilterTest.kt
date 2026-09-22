package com.lioisme.modev.data.repo

import androidx.room.Room
import com.lioisme.modev.core.model.RecordTypes
import com.lioisme.modev.data.db.CryptoRecordEntity
import com.lioisme.modev.data.db.ModevDatabase
import com.lioisme.modev.data.server.ConfigServer
import com.lioisme.modev.domain.record.RecordFilter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * LogRepository RecordFilter 契约(V17 R2 记录底层一体化):
 * 全字段空集 = 不过滤(跨类型"整体");单/多值集合 IN 语义;query 转义穿透;
 * 类型分组计数。REST/MCP 层未来仅依赖本契约面。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LogRepositoryFilterTest {

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
        algorithm: String,
        recordType: String = RecordTypes.CRYPTO,
    ) = CryptoRecordEntity(
        packageName = packageName,
        kind = kind,
        operation = "op",
        algorithm = algorithm,
        threadName = "main",
        timestamp = System.currentTimeMillis(),
        recordType = recordType,
    )

    @Test
    fun `counts with empty filter totals all record types`() = runBlocking {
        db.cryptoRecordDao().insert(entity("pkg.a", "cipher", "AES"))
        db.cryptoRecordDao().insert(entity("pkg.a", "digest", "SHA-256", recordType = "database"))
        db.cryptoRecordDao().insert(entity("pkg.b", "mac", "HmacSHA256"))

        assertEquals(3L, repo.count(RecordFilter()).first())
    }

    @Test
    fun `counts filter by type package kind and query`() = runBlocking {
        db.cryptoRecordDao().insert(entity("pkg.a", "cipher", "AES"))
        db.cryptoRecordDao().insert(entity("pkg.a", "digest", "SHA-256", recordType = "database"))
        db.cryptoRecordDao().insert(entity("pkg.b", "mac", "HmacSHA256"))

        assertEquals(2L, repo.count(RecordFilter(recordTypes = setOf(RecordTypes.CRYPTO))).first())
        // pkg.a 两行 + pkg.b 一行 = 3
        assertEquals(
            3L,
            repo.count(RecordFilter(packages = setOf("pkg.a", "pkg.b"))).first(),
        )
        assertEquals(1L, repo.count(RecordFilter(kinds = setOf("mac"))).first())
        assertEquals(1L, repo.count(RecordFilter(query = "SHA-256")).first())
        assertEquals(
            1L,
            repo.count(RecordFilter(recordTypes = setOf("database"), packages = setOf("pkg.a"))).first(),
        )
    }

    @Test
    fun `counts with LIKE metacharacters stays literal`() = runBlocking {
        db.cryptoRecordDao().insert(entity("pkg.a", "cipher", "AES_100%"))
        assertEquals(1L, repo.count(RecordFilter(query = "AES_100%")).first())
    }

    @Test
    fun `record type counts group across types`() = runBlocking {
        db.cryptoRecordDao().insert(entity("pkg.a", "cipher", "AES"))
        db.cryptoRecordDao().insert(entity("pkg.a", "digest", "SHA-256", recordType = "database"))

        val counts = repo.observeTypeCounts().first()
        assertEquals(
            mapOf(RecordTypes.CRYPTO to 1L, "database" to 1L),
            counts.associate { it.recordType to it.count },
        )
    }
}