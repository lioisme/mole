package com.lioisme.modev.data.repo

import androidx.room.Room
import com.lioisme.modev.core.model.RecordTypes
import com.lioisme.modev.data.db.CryptoRecordDao
import com.lioisme.modev.data.db.CryptoRecordEntity
import com.lioisme.modev.data.db.ModevDatabase
import com.lioisme.modev.data.server.ConfigServer
import com.lioisme.modev.domain.record.RecordFilter
import com.lioisme.modev.domain.record.RecordService
import com.lioisme.modev.domain.record.SortOrder
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
 * 查询核心双端一致性与底层数据第一性门禁(V44 T1.5,审计缺口 G):
 *
 * 旧门禁只锁工具名与 DAO 旁路,不锁**语义**——本测试钉死三组不变量:
 * 1. 计数三口径合一:UI 副标消费的 [RecordService.count] == [RecordService.queryPage]
 *    返回的 total == DAO 投影切片真实规模(全量加载时);
 * 2. `RecordFilter.sort` 双实现兑现:queryPage ASC 全量 == DESC 全量之逆序;
 *    observe ASC 变体可用(流式路径 sort 不再静默忽略);
 * 3. 「删 = 筛」:deleteByFilter 谓词与 count 共享 WHERE_FILTER,删除后计数归零。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecordCoreConsistencyTest {

    private lateinit var db: ModevDatabase
    private lateinit var dao: CryptoRecordDao
    private lateinit var repo: LogRepository
    private lateinit var server: ConfigServer

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            ModevDatabase::class.java,
        ).build()
        dao = db.cryptoRecordDao()
        server = ConfigServer(ServerSocket(0).use { it.localPort })
        repo = LogRepository(dao, server)
        runBlocking {
            // 130 条(> UI 页大小 60)+ 20 条数据库类型,跨包 / 跨类别分布。
            repeat(130) { i ->
                dao.insert(entity("pkg.a", if (i % 2 == 0) "CIPHER" else "MESSAGE_DIGEST", i.toLong()))
            }
            repeat(20) { i ->
                dao.insert(entity("pkg.b", "QUERY", 1000L + i, recordType = RecordTypes.DATABASE))
            }
        }
    }

    @After
    fun tearDown() {
        db.close()
        server.close()
    }

    private fun entity(pkg: String, kind: String, ts: Long, recordType: String = RecordTypes.CRYPTO) =
        CryptoRecordEntity(
            packageName = pkg,
            kind = kind,
            operation = if (recordType == RecordTypes.DATABASE) "QUERY" else "ENCRYPT",
            algorithm = if (recordType == RecordTypes.DATABASE) "main.db" else "AES",
            threadName = "main",
            timestamp = ts,
            recordType = recordType,
        )

    private val probes: List<Pair<String, RecordFilter>> = listOf(
        "全部" to RecordFilter(),
        "按类型" to RecordFilter(recordTypes = setOf(RecordTypes.CRYPTO)),
        "按包" to RecordFilter(packages = setOf("pkg.a")),
        "按类别" to RecordFilter(kinds = setOf("CIPHER")),
        "类型+类别" to RecordFilter(recordTypes = setOf(RecordTypes.DATABASE), kinds = setOf("QUERY")),
        "关键字" to RecordFilter(query = "AES"),
        "时间窗" to RecordFilter(timeRange = 50L..99L),
    )

    @Test
    fun `count is single-source across service and slice`() = runBlocking {
        for ((label, filter) in probes) {
            val viaCount = repo.count(filter).first()
            val viaSlice = repo.queryPage(filter, page = 0, pageSize = Int.MAX_VALUE / 2)
            assertEquals("[$label] count==queryPage.total", viaCount, viaSlice.total)
            assertEquals("[$label] queryPage 全量装载行数==count", viaCount, viaSlice.items.size.toLong())
        }
    }

    @Test
    fun `sort contract holds on the slice path`() = runBlocking {
        val desc = repo.queryPage(RecordFilter(sort = SortOrder.DESC), 0, Int.MAX_VALUE / 2).items
            .map { it.id }
        val asc = repo.queryPage(RecordFilter(sort = SortOrder.ASC), 0, Int.MAX_VALUE / 2).items
            .map { it.id }
        assertEquals(150, desc.size)
        assertEquals("ASC 全量 == DESC 全量之逆序", desc.reversed(), asc)
    }

    @Test
    fun `observe honors sort on the streaming path (no silent ignore)`() = runBlocking {
        // V44 Q1 语义锁:observe() 按 filter.sort 选择投影源(ASC 走升序投影变体)。
        // 切片路径:全量集合方向一致性(同一 WHERE_FILTER,投影同源)双向对账;
        // 流式路径:observe 两个方向都必须可物化(旧形态恒走 DESC 分支,ASC 分支
        // 从未被执行 = sort 静默失效);ASC 分支所依赖的升序 PagingSource 单独
        // 断言真实翻页顺序,防「接线存在但排序错」。
        val desc = repo.queryPage(RecordFilter(sort = SortOrder.DESC), 0, Int.MAX_VALUE / 2).items.map { it.id }
        val asc = repo.queryPage(RecordFilter(sort = SortOrder.ASC), 0, Int.MAX_VALUE / 2).items.map { it.id }
        assertEquals(desc.reversed(), asc)
        repo.observe(RecordFilter(sort = SortOrder.DESC)).first()
        repo.observe(RecordFilter(sort = SortOrder.ASC)).first()

        // V45 T1.1:升序流式路径由谓词生成器 + 同一 @RawQuery 执行位承载
        // (旧形态另写一份 DESC/ASC 双查询,两副面孔的根源)
        val source = dao.pagedRows(
            com.lioisme.modev.data.db.RecordQueryFactory.rows(
                filter = RecordFilter(sort = SortOrder.ASC),
                limit = 1_000,
                offset = 0,
                previewChars = 32,
            ),
        )
        val page = source.load(
            androidx.paging.PagingSource.LoadParams.Refresh<Int>(null, 20, false),
        )
        assertTrue("升序 PagingSource 必须可 Refresh,得到 $page", page is androidx.paging.PagingSource.LoadResult.Page)
        val ids = (page as androidx.paging.PagingSource.LoadResult.Page).data.map { it.id }
        assertEquals("升序投影首页 = 全量升序前 20 条", asc.take(20), ids)
    }

    @Test
    fun `delete equals filter predicate`() = runBlocking {
        val filter = RecordFilter(packages = setOf("pkg.a"), kinds = setOf("CIPHER"))
        val matched = repo.count(filter).first()
        assertTrue(matched > 0)
        val deleted = repo.deleteByFilter(filter)
        assertEquals("删=筛:删除数==匹配数", matched, deleted)
        assertEquals(0L, repo.count(filter).first())
        // 其余谓词不受波及
        assertEquals(20L, repo.count(RecordFilter(packages = setOf("pkg.b"))).first())
    }

    @Test
    fun `page size default is single source`() {
        assertEquals("UI 分页与 MCP 默认页大小同契约常量", 60, RecordService.DEFAULT_PAGE_SIZE)
    }
}
