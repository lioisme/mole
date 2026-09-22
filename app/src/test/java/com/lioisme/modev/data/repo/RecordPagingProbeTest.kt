package com.lioisme.modev.data.repo

import androidx.paging.PagingSource
import com.lioisme.modev.data.db.RecordQueryFactory
import com.lioisme.modev.domain.record.RecordFilter
import androidx.room.Room
import com.lioisme.modev.data.db.CryptoRecordDao
import com.lioisme.modev.data.db.CryptoRecordEntity
import com.lioisme.modev.data.db.CryptoRecordRow
import com.lioisme.modev.data.db.ModevDatabase
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
 * V20 分页契约探针(R3 滚动测试发现:120 条种子下列表止于第 1 页 ALG-059,
 * 第 2 页从未装载。根因(Room 2.7.2 LimitOffsetPagingSource 机制):
 * 生成式 paging 的计数查询 = COUNT(*) FROM (<原 SQL>),原 SQL 自带
 * `LIMIT :limit OFFSET :offset` 固定绑定 → itemCount 被钳制为 DAO limit
 * (60) → queryLimit=60 → Refresh 后 nextKey=null → 分页死锁。
 * 修复:DAO 的 limit 参数 = 查询窗口上限(传大值,外层 loadSize 由 Room
 * 动态注入);本探针验证大窗口下 Refresh/Append 能翻完全部 120 条)。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecordPagingProbeTest {

    private lateinit var db: ModevDatabase
    private lateinit var dao: CryptoRecordDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            ModevDatabase::class.java,
        ).build()
        dao = db.cryptoRecordDao()
        runBlocking {
            dao.clearPackage(PKG)
            val now = System.currentTimeMillis()
            dao.insertAll(
                (0 until 120).map { i ->
                    CryptoRecordEntity(
                        packageName = PKG,
                        kind = "MESSAGE_DIGEST",
                        operation = "DIGEST",
                        algorithm = "ALG-%03d".format(i),
                        threadName = "probe",
                        timestamp = now - i * 1_000L,
                    )
                },
            )
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun pagingSource_refreshThenAppend_loadsAllPages() = runBlocking {
        val source: PagingSource<Int, CryptoRecordRow> = dao.pagedRows(
            RecordQueryFactory.rows(
                filter = RecordFilter(recordTypes = setOf("crypto"), packages = setOf(PKG)),
                limit = WINDOW_CAP,
                offset = 0,
                previewChars = 32,
            ),
        )

        val refresh = source.load(
            PagingSource.LoadParams.Refresh<Int>(key = null, loadSize = 60, placeholdersEnabled = false),
        )
        assertTrue("Refresh should succeed, got: $refresh", refresh is PagingSource.LoadResult.Page)
        val page1 = (refresh as PagingSource.LoadResult.Page).data
        assertEquals(60, page1.size)
        assertEquals("ALG-000", page1.first().algorithm)
        assertEquals("ALG-059", page1.last().algorithm)

        val nextKey = refresh.nextKey as? Int
        assertTrue("Refresh nextKey should be 60, got: ${refresh.nextKey}", nextKey == 60)

        val append = source.load(
            PagingSource.LoadParams.Append<Int>(key = nextKey!!, loadSize = 60, placeholdersEnabled = false),
        )
        assertTrue("Append should return page 2, got: $append", append is PagingSource.LoadResult.Page)
        val page2 = (append as PagingSource.LoadResult.Page).data
        assertEquals(60, page2.size)
        assertEquals("ALG-060", page2.first().algorithm)
        assertEquals("ALG-119", page2.last().algorithm)
        assertTrue(
            "Append of page 2 (60 rows) should end paging, got nextKey: ${append.nextKey}",
            append.nextKey == null,
        )
    }

    @Test
    fun pagingSource_fixedSmallLimit_deadlocksAfterFirstPage() = runBlocking {
        val source: PagingSource<Int, CryptoRecordRow> = dao.pagedRows(
            RecordQueryFactory.rows(
                filter = RecordFilter(recordTypes = setOf("crypto"), packages = setOf(PKG)),
                limit = 60,
                offset = 0,
                previewChars = 32,
            ),
        )
        val refresh = source.load(
            PagingSource.LoadParams.Refresh<Int>(key = null, loadSize = 60, placeholdersEnabled = false),
        )
        assertTrue(refresh is PagingSource.LoadResult.Page)
        assertEquals(60, (refresh as PagingSource.LoadResult.Page).data.size)
        assertTrue(
            "Room 2.7.2: 固定小 limit 时 COUNT 被钳制,nextKey 应为 null(分页死锁,本行为为回归警戒线)",
            refresh.nextKey == null,
        )
    }

    @Test
    fun pagingSource_refreshCountMatchesTotal() = runBlocking {
        val count = dao.countByQuery(
            RecordQueryFactory.count(RecordFilter(packages = setOf(PKG))),
        ).first()
        assertEquals(120L, count)
    }

    private companion object {
        const val PKG = "com.lioisme.modev"

        /** 查询窗口上限(修复值;外层 loadSize 由 Room 动态注入)。 */
        const val WINDOW_CAP = 1_000_000
    }
}