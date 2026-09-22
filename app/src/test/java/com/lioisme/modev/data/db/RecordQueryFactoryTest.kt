package com.lioisme.modev.data.db

import com.lioisme.modev.core.model.RecordTypes
import com.lioisme.modev.domain.record.RecordFilter
import com.lioisme.modev.domain.record.SortOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 谓词生成器契约测试(V45 T1.1 / ST-1、ST-2,R1 高效底层数据的可执行规格)。
 *
 * 锁三件事:
 * 1. **缺省维度不出列**——旧哨兵形态(`(:typeCount = 0 OR recordType IN (:recordTypes))`)
 *    会把每个维度恒写进 WHERE,使 SQLite 无法选索引;本测试逐维度断言「无谓词 = SQL 里没有这一列」;
 * 2. **SQL 文本与绑定参数严格同序**(位置 `?` 错位 = 静默错结果,是 raw query 的经典故障);
 * 3. **四路执行位同谓词**(rows / count / ids / neighbor 共用 [RecordQueryFactory.predicate]),
 *    使「删 = 筛 = 计数」不再靠两份手写 SQL 维持。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecordQueryFactoryTest {

    private val previewArgs = Regex(":previewChars").findAll(CryptoRecordDao.ROW_SELECT).count()

    @Test
    fun emptyFilterEmitsNoWhereClauseAtAll() {
        val built = RecordQueryFactory.rows(RecordFilter(), limit = 60, offset = 0, previewChars = 48)
        assertFalse("缺省过滤不得出现 WHERE:${built.sql}", built.sql.contains("WHERE"))
        assertTrue(built.sql.contains("ORDER BY timestamp DESC, id DESC"))
        assertTrue(built.sql.endsWith("LIMIT ? OFFSET ?"))
        // 绑定 = 投影预览参数 + limit + offset(零谓词参数)
        assertEquals(previewArgs + 2, built.args.size)
        assertEquals(60, built.args[previewArgs])
        assertEquals(0, built.args[previewArgs + 1])
    }

    @Test
    fun eachDimensionEmitsOnlyItsOwnClause() {
        val cases = listOf(
            RecordFilter(recordTypes = setOf(RecordTypes.CRYPTO)) to "recordType IN (?)",
            RecordFilter(packages = setOf("pkg.a")) to "packageName IN (?)",
            RecordFilter(kinds = setOf("CIPHER")) to "kind IN (?)",
            RecordFilter(operations = setOf("ENCRYPT")) to "operation IN (?)",
        )
        val allColumns = listOf("recordType", "packageName", "kind", "operation")
        for ((filter, expected) in cases) {
            val where = RecordQueryFactory.predicate(filter)
            assertEquals(expected, where.sql)
            // 其余维度不得出现在谓词里(这正是旧哨兵形态的病灶)
            val others = allColumns.filterNot { expected.startsWith(it) }
            others.forEach { assertFalse("$it 不应出现在 ${where.sql}", where.sql.contains(it)) }
        }
    }

    @Test
    fun multiValueInKeepsPlaceholderAndArgAlignment() {
        val where = RecordQueryFactory.predicate(
            RecordFilter(packages = setOf("pkg.a", "pkg.b", "pkg.c")),
        )
        assertEquals("packageName IN (?,?,?)", where.sql)
        assertEquals(listOf("pkg.a", "pkg.b", "pkg.c"), where.args)
    }

    @Test
    fun dimensionsComposeInStableOrderWithAlignedArgs() {
        val where = RecordQueryFactory.predicate(
            RecordFilter(
                recordTypes = setOf(RecordTypes.CRYPTO, RecordTypes.DATABASE),
                packages = setOf("pkg.a"),
                kinds = setOf("CIPHER"),
                operations = setOf("ENCRYPT", "DO_FINAL"),
                timeRange = 10L..20L,
            ),
        )
        assertEquals(
            "recordType IN (?,?) AND packageName IN (?) AND kind IN (?) AND " +
                "operation IN (?,?) AND timestamp >= ? AND timestamp <= ?",
            where.sql,
        )
        assertEquals(
            listOf(RecordTypes.CRYPTO, RecordTypes.DATABASE, "pkg.a", "CIPHER", "ENCRYPT", "DO_FINAL", 10L, 20L),
            where.args,
        )
    }

    @Test
    fun keywordIsEscapedAndWrappedOnceAcrossAllSearchColumns() {
        val raw = "100%_x\\y"
        val where = RecordQueryFactory.predicate(RecordFilter(query = raw))
        val likeCount = Regex("LIKE \\?").findAll(where.sql).count()
        assertEquals("每个检索列恰一个占位", 24, likeCount)
        assertEquals(24, where.args.size)
        // 每个绑定都是同一转义模式:`%` `_` `\` 全部转义,通配符不参与匹配
        assertTrue(where.args.all { it == "%100\\%\\_x\\\\y%" })
    }

    @Test
    fun emptyIntersectionShortCircuitsToFalsePredicate() {
        // SIGNATURE 属 crypto/system,不属于 database → normalize 收敛后空集
        val where = RecordQueryFactory.predicate(
            RecordFilter(recordTypes = setOf(RecordTypes.DATABASE), kinds = setOf("CIPHER")),
        )
        assertTrue(where.noMatch)
        assertEquals("0", where.sql)
        assertTrue(
            RecordQueryFactory.count(
                RecordFilter(recordTypes = setOf(RecordTypes.CRYPTO), kinds = setOf("NOT_A_CRYPTO_KIND")),
            ).sql.contains("WHERE 0"),
        )
    }

    @Test
    fun allFourPathsShareTheSamePredicateText() {
        val filter = RecordFilter(recordTypes = setOf(RecordTypes.CRYPTO), packages = setOf("pkg.a"))
        val expected = RecordQueryFactory.predicate(filter).whereClause()
        assertTrue(RecordQueryFactory.count(filter).sql.contains(expected))
        assertTrue(RecordQueryFactory.ids(filter).sql.contains(expected))
        assertTrue(
            RecordQueryFactory.rows(filter, limit = 5, offset = 5, previewChars = 32).sql.contains(expected),
        )
        assertTrue(
            RecordQueryFactory.neighborId(filter, ts = 9L, id = 3L, newer = true).sql.contains(expected),
        )
    }

    @Test
    fun sortDrivesBothOrderByPositions() {
        val desc = RecordQueryFactory.rows(RecordFilter(), limit = 1, offset = 0, previewChars = 8).sql
        val asc = RecordQueryFactory.rows(
            RecordFilter(sort = SortOrder.ASC),
            limit = 1,
            offset = 0,
            previewChars = 8,
        ).sql
        assertTrue(desc.contains("ORDER BY timestamp DESC, id DESC"))
        assertTrue(asc.contains("ORDER BY timestamp ASC, id ASC"))
    }

    @Test
    fun neighborUsesKeysetComparisonMatchedToDirection() {
        val older = RecordQueryFactory.neighborId(RecordFilter(), ts = 100L, id = 7L, newer = false)
        assertTrue(older.sql.contains("(timestamp < ? OR (timestamp = ? AND id < ?))"))
        assertTrue(older.sql.endsWith("ORDER BY timestamp DESC, id DESC LIMIT 1"))
        // 无其他谓词时尾部三参 = ts, ts, id
        assertEquals(listOf(100L, 100L, 7L), older.args.takeLast(3))

        val newer = RecordQueryFactory.neighborId(
            RecordFilter(packages = setOf("pkg.a")),
            ts = 100L,
            id = 7L,
            newer = true,
        )
        assertTrue(newer.sql.contains("(timestamp > ? OR (timestamp = ? AND id > ?))"))
        assertTrue(newer.sql.endsWith("ORDER BY timestamp ASC, id ASC LIMIT 1"))
        assertTrue(newer.sql.contains("WHERE packageName IN (?) AND (timestamp > ?"))
        assertEquals(listOf("pkg.a", 100L, 100L, 7L), newer.args)
    }

    @Test
    fun argCountMatchesPlaceholderCountExactly() {
        val filter = RecordFilter(
            recordTypes = setOf(RecordTypes.CRYPTO),
            packages = setOf("pkg.a", "pkg.b"),
            query = "AES",
        )
        val built = RecordQueryFactory.rows(filter, limit = 10, offset = 20, previewChars = 48)
        val placeholders = Regex("\\?").findAll(built.sql).count()
        assertEquals("占位数与参数数必须相等", placeholders, built.argCount)
        assertEquals(built.args.size, built.argCount)
    }

    @Test
    fun projectionUsesSameColumnListAsBeforeForPagingAndSlice() {
        // ROW_SELECT 单一投影源:两条路径列集逐字一致(列表 ≠ MCP 的旧分叉源头之一)
        val rows = RecordQueryFactory.rows(RecordFilter(), limit = 1, offset = 0, previewChars = 48).sql
        val ids = RecordQueryFactory.ids(RecordFilter()).sql
        assertTrue(rows.contains("substr(keyHex, 1, ?) AS keyPreview"))
        assertTrue(ids.startsWith("SELECT id FROM ${CryptoRecordEntity.TABLE_NAME}"))
    }
}
