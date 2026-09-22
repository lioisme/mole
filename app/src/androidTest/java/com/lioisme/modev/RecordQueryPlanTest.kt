package com.lioisme.modev

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.sqlite.db.SimpleSQLiteQuery
import com.lioisme.modev.core.model.RecordTypes
import com.lioisme.modev.data.db.CryptoRecordEntity
import com.lioisme.modev.data.db.ModevDatabase
import com.lioisme.modev.data.db.RecordQueryFactory
import com.lioisme.modev.domain.record.RecordFilter
import com.lioisme.modev.domain.record.SortOrder
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 查询计划真机门禁(V45 T1.1 验收项 / ST-1、ST-2,R1「底层数据架构高效」的可执行证据)。
 *
 * 为什么必须在设备上跑:Robolectric 的 legacy 与 native shadow 两种模式都会**剥掉
 * `EXPLAIN` 前缀直接执行原 SQL**(AGENTS 已知坑,`ExplainQueryPlanProbeTest` 实证),
 * 因此「有索引但用不上」这类低效在 JVM 层不可见——V44 之前的哨兵 WHERE 片段
 * (`(:typeCount = 0 OR recordType IN (:recordTypes))`)正是这样在全表扫描下活了数轮。
 *
 * 判定口径:谓词命中索引的查询**不得出现裸 `SCAN crypto_records`**(表全扫);
 * 计划文本原样打日志,失败即可归因。种子量取 4000 行(足够让规划器放弃小表全扫偏好)。
 */
@RunWith(AndroidJUnit4::class)
class RecordQueryPlanTest {

    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val db get() = ModevDatabase.get(ctx)

    @After
    fun unseed(): Unit = runBlocking {
        // 4000 行种子**必须**在本类结束时清空:设备宿主库跨用例类共享,
        // 残留会让后续「种子写入须在读回中可见」类断言集体错位(V45 首轮实锤 6→4006)。
        db.cryptoRecordDao().clearAll()
    }

    @Before
    fun seed(): Unit = runBlocking {
        val dao = db.cryptoRecordDao()
        dao.clearAll()
        dao.insertAll(
            (1..SEED_ROWS).map { i ->
                CryptoRecordEntity(
                    recordType = if (i % 3 == 0) RecordTypes.DATABASE else RecordTypes.CRYPTO,
                    kind = if (i % 2 == 0) "CIPHER" else "MESSAGE_DIGEST",
                    operation = "ENCRYPT",
                    algorithm = "AES/$i",
                    threadName = "main",
                    packageName = if (i % 2 == 0) PKG_A else PKG_B,
                    timestamp = BASE_TS + i,
                    seq = i.toLong(),
                    processName = "p1",
                )
            },
        )
    }

    @Test
    fun timelineBrowseUsesTimestampIndexNotFullScan() {
        // 默认列表(无过滤,时间倒序取一页):必须由 index_timestamp 供序,不排序不全扫
        val plan = explain(RecordQueryFactory.rows(RecordFilter(), limit = 60, offset = 0, previewChars = 48))
        assertNoBareScan("timeline browse", plan)
        assertTrue("计划应使用 timestamp 索引,实际:\n$plan", plan.contains("index_crypto_records_timestamp"))
        assertFalse("计划不应出现临时 B-tree 排序,实际:\n$plan", plan.contains("USE TEMP B-TREE"))
    }

    @Test
    fun typeAndPackageFilterUsesCompositeIndex() {
        val plan = explain(
            RecordQueryFactory.rows(
                filter = RecordFilter(recordTypes = setOf(RecordTypes.CRYPTO), packages = setOf(PKG_A)),
                limit = 60,
                offset = 0,
                previewChars = 48,
            ),
        )
        assertNoBareScan("type+package filter", plan)
        assertTrue(
            "计划应使用 (recordType, packageName, timestamp) 复合索引,实际:\n$plan",
            plan.contains("index_crypto_records_recordType_packageName_timestamp"),
        )
    }

    @Test
    fun kindFilterUsesTypeKindIndex() {
        val plan = explain(
            RecordQueryFactory.rows(
                filter = RecordFilter(recordTypes = setOf(RecordTypes.CRYPTO), kinds = setOf("CIPHER")),
                limit = 60,
                offset = 0,
                previewChars = 48,
            ),
        )
        assertNoBareScan("type+kind filter", plan)
        assertTrue(
            "计划应使用 (recordType, kind) 索引,实际:\n$plan",
            plan.contains("index_crypto_records_recordType_kind"),
        )
    }

    @Test
    fun countMatchesRowPredicateAndAvoidsFullScan() {
        val filter = RecordFilter(recordTypes = setOf(RecordTypes.CRYPTO))
        assertNoBareScan("count", explain(RecordQueryFactory.count(filter)))
    }

    @Test
    fun neighborKeysetQueryAvoidsFullScanAndTempSort() {
        // ST-2 主修点:旧形态哨兵 OR + (ts,id) 复合比较 → 每次翻记录一次全表扫描 + 排序
        val plan = explain(
            RecordQueryFactory.neighborId(
                filter = RecordFilter(
                    recordTypes = setOf(RecordTypes.CRYPTO),
                    packages = setOf(PKG_A),
                ),
                ts = BASE_TS + SEED_ROWS / 2,
                id = 1,
                newer = false,
            ),
        )
        assertNoBareScan("neighbor keyset", plan)
        assertFalse("邻接查询不得临时排序(应借索引供序),实际:\n$plan", plan.contains("USE TEMP B-TREE"))
    }

    @Test
    fun deleteCandidatesShareRowPredicatePlan() {
        assertNoBareScan("delete candidates", explain(RecordQueryFactory.ids(RecordFilter(packages = setOf(PKG_A)))))
    }

    @Test
    fun ascSortUsesSameIndex() {
        val plan = explain(
            RecordQueryFactory.rows(RecordFilter(sort = SortOrder.ASC), limit = 60, offset = 0, previewChars = 48),
        )
        assertNoBareScan("asc browse", plan)
        assertTrue("计划应使用 timestamp 索引,实际:\n$plan", plan.contains("index_crypto_records_timestamp"))
    }

    private fun assertNoBareScan(case: String, plan: String) {
        // 「SCAN crypto_records」= 全表扫描;走索引时形如 SCAN ... USING INDEX / SEARCH ... USING INDEX
        val bare = plan.lineSequence()
            .filter { it.contains("SCAN") && !it.contains("USING INDEX") && !it.contains("USING COVERING INDEX") }
            .joinToString("\n")
        assertTrue("$case 不得全表扫描计划:\n$plan\n命中裸 SCAN:\n$bare", bare.isEmpty())
    }

    /** 取 EXPLAIN QUERY PLAN 文本(detail 列即计划描述);绑定原样复用生成器产物。 */
    private fun explain(query: RecordQueryFactory.Built): String {
        val rows = ArrayList<String>()
        db.openHelper.readableDatabase.query(
            SimpleSQLiteQuery("EXPLAIN QUERY PLAN " + query.sql, query.args.toTypedArray()),
        ).use { cursor ->
            val detail = cursor.getColumnIndexOrThrow("detail")
            while (cursor.moveToNext()) rows += cursor.getString(detail)
        }
        val plan = rows.joinToString("\n")
        android.util.Log.i("RecordQueryPlan", "plan for [${query.sql}]:\n$plan")
        return plan
    }

    private companion object {
        const val PKG_A = "com.lioisme.modev.plna"
        const val PKG_B = "com.lioisme.modev.plnb"
        const val SEED_ROWS = 4_000
        const val BASE_TS = 1_700_000_000_000L
    }
}
