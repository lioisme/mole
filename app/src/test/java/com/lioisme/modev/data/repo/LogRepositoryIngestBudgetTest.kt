package com.lioisme.modev.data.repo

import androidx.room.Room
import com.google.gson.JsonObject
import com.lioisme.modev.core.codec.CryptoRecordCodec
import com.lioisme.modev.core.codec.WireRecord
import com.lioisme.modev.core.model.CryptoKind
import com.lioisme.modev.core.model.CryptoOperation
import com.lioisme.modev.core.model.CryptoRecord
import com.lioisme.modev.core.model.RecordTypes
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
 * 宿主摄入载荷预算门禁(V45 T1.6 / ST-5):
 * 通道旧形态只有**条数**上限(8192 槽),多条 MB 级记录即可把宿主推到 GC 风暴/OOM。
 * 本测试锁定两条互斥语义 ——
 *  ① 结构性超大(单条即超整条预算)= 拒收**且回执**(不回执 = 注入侧 WAL 永久重放死循环);
 *  ② 在途占满 = 拒收**且不回执**(反压可恢复,由 WAL 稍后重放);
 *  ③ 两类均计 [bytesDropped] 而不计入 [dropped](丢弃原因分型是调参依据,合并计数即失真)。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LogRepositoryIngestBudgetTest {

    private lateinit var db: ModevDatabase
    private lateinit var server: ConfigServer

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            ModevDatabase::class.java,
        ).build()
        server = ConfigServer()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun repo(budgetChars: Int) = LogRepository(
        dao = db.cryptoRecordDao(),
        server = server,
        maxPendingPayloadChars = budgetChars,
    ).also { it.start() }

    /** 载荷体积 = `{"pad":"xxx…"}` 的 JSON 字符数。 */
    private fun wire(seq: Long, pad: Int) = WireRecord(
        seq = seq,
        recordType = RecordTypes.CRYPTO,
        payload = JsonObject().apply { addProperty("pad", "x".repeat(pad)) },
    )

    @Test
    fun `record larger than the whole budget is dropped but acknowledged`() = runBlocking {
        val repo = repo(budgetChars = 64)
        var acked = -1L
        val huge = wire(seq = 7L, pad = 4_000)

        server.onRecord(huge) { seq -> acked = seq }

        assertEquals("结构性超大必须回执(否则注入侧 WAL 永久重放)", 7L, acked)
        assertEquals(1L, repo.stats.value.bytesDropped)
        assertEquals("载荷预算丢弃不得混入条数丢弃计数", 0L, repo.stats.value.dropped)
        assertEquals(0L, repo.count(RecordFilter()).first())
    }

    @Test
    fun `record within budget still lands and is never budget-dropped`() = runBlocking {
        val repo = repo(budgetChars = 1_000_000)
        val payload = CryptoRecordCodec.encode(
            CryptoRecord(
                packageName = "pkg.a",
                kind = CryptoKind.CIPHER,
                operation = CryptoOperation.ENCRYPT,
                algorithm = "AES",
                threadName = "main",
                timestamp = 100L,
            ),
        )
        var acked = -1L

        server.onRecord(WireRecord(seq = 1L, recordType = RecordTypes.CRYPTO, payload = payload)) { acked = it }

        assertEquals(0L, repo.stats.value.bytesDropped)
        // 批写窗口 200ms:轮询至落库(预算闸门不得改变正常吞吐语义)
        val total = kotlinx.coroutines.withTimeout(5_000) {
            repo.count(RecordFilter()).first { it >= 1L }
        }
        assertEquals(1L, total)
        assertEquals("正常记录须回执推进注入侧游标", 1L, acked)
    }
}
