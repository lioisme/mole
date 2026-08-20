package com.lioisme.modev

import android.text.format.DateFormat
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lioisme.modev.R
import com.lioisme.modev.data.db.CryptoRecordEntity
import com.lioisme.modev.data.db.ModevDatabase
import com.lioisme.modev.data.repo.AppListSettings
import com.lioisme.modev.i18n.LocaleManager
import com.lioisme.modev.ui.MainActivity
import com.lioisme.modev.ui.common.groupLabelOrNull
import com.lioisme.modev.ui.common.timelineLabel
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 秒级时间线分组验收(V17 R3;V20 单应用语境化,算法/数据库列表分路径):
 * 种子数据 = 同秒 3 行 + 跨秒 1 行 + 跨日 1 行 + DB 行 1 条,断言:
 * - 首项(r4)恒有组头 = 日期 + 秒级时间;
 * - 跨秒新头 = 同日仅秒级时间;
 * - 同秒 3 行仅 1 个头(单头不重复);
 * - 跨日头带日期前缀;
 * - DB 行渲染(库名 + SQL 预览 + 详情 SQL 节)。
 * 前置:被测应用自身是 Xposed 模块(默认隐藏),@Before 开启「显示 Xposed 模块」
 * 使行可见,@After 复位(与单元测试同源,避免污染其他用例)。
 * 期望文案经 TimelineGroup 纯函数以设备 locale / 12-24h 时制复算,与生产同源。
 */
@RunWith(AndroidJUnit4::class)
class TimelineListTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val packageName = "com.lioisme.modev"

    /** 种子时间戳(@Before 固定,测试体内复算会因导航耗时跨秒漂移,禁止)。 */
    private var s1 = 0L
    private var s2 = 0L
    private var d1 = 0L

    @Before
    fun seed() {
        val dao = ModevDatabase.get(ctx).cryptoRecordDao()
        // 清空被测包既有记录,断言确定性(与运行库共享单例,直接操作同一 DB)。
        runBlocking { dao.clearPackage(packageName) }
        val now = System.currentTimeMillis()
        s1 = now - 120_000
        s2 = s1 + 1_000
        d1 = now - 2 * 24 * 3600 * 1_000L
        runBlocking {
            dao.insertAll(
                listOf(
                    record(s1, "MESSAGE_DIGEST", "SHA-256", "DIGEST"),
                    record(s1, "MESSAGE_DIGEST", "MD5", "DIGEST"),
                    record(s1, "CIPHER", "AES", "ENCRYPT"),
                    record(s2, "CIPHER", "DES", "ENCRYPT"),
                    record(d1, "MAC", "HmacSHA256", "SIGN"),
                    // DB 行(s1 同秒,不新增组头;V17 R3 双形态行渲染)
                    dbRecord(s1, "DML", "t14.db", "INSERT", "INSERT INTO t (name) VALUES (?)"),
                ),
            )
            // 写回读验:与 UI 同一 DB 单例,必须即刻可见(哨兵计数语义同 DAO 契约)。
            val seeded = dao
                .observeCountByFilter(
                    recordTypes = emptyList(),
                    packages = listOf(packageName),
                    kinds = emptyList(),
                    typeCount = 0,
                    packageCount = 1,
                    kindCount = 0,
                    query = null,
                )
                .first()
            assertEquals("种子写入须在读回中可见", 6L, seeded)
        }
        // 被测应用默认隐藏(自身是 Xposed 模块),开启显隐使行可见。
        // 仅作用域筛选会按进程隐藏行(rootHasOnly 勾选后持久化),一并复位保证确定性。
        AppListSettings.setOnlyScoped(false, ctx)
        AppListSettings.setShowModules(true, ctx)
        rule.waitForIdle()
    }

    @After
    fun restore() {
        AppListSettings.setShowModules(false, ctx)
    }

    /**
     * V18:等待被测包行出现(目录异步加载 + 模块显隐传播,滚动惰性列表查找,
     * 找不到超时即失败——种子数据恒在,仅目录加载有延迟),点击进入应用监听
     * 入口页。V20:入口页两记录入口直通单应用列表,不再有「查看记录」。
     */
    private fun openMonitorEntry() {
        val deadline = System.currentTimeMillis() + 10_000
        var rowFound = false
        while (System.currentTimeMillis() < deadline) {
            rule.waitForIdle()
            val scrollables = rule.onAllNodes(hasScrollAction()).fetchSemanticsNodes()
            if (scrollables.isNotEmpty()) {
                try {
                    rule.onAllNodes(hasScrollAction()).onFirst()
                        .performScrollToNode(hasText(packageName))
                    rowFound = true
                    break
                } catch (_: AssertionError) {
                    // 目标行未加载/被模块显隐过滤,下一轮重试。
                }
            }
            Thread.sleep(100)
        }
        assertTrue("等待「显示 Xposed 模块」生效后被测包行应出现", rowFound)
        rule.onNodeWithText(packageName).performClick()
        rule.waitForIdle()
    }

    /** 进入单应用算法记录列表(入口页 → 算法记录)。 */
    private fun openAlgorithmList() {
        openMonitorEntry()
        rule.onNodeWithText(ctx.getString(R.string.algorithm_records_title)).performClick()
        rule.waitForIdle()
    }

    /** 进入单应用数据库记录列表(入口页 → 数据库记录)。 */
    private fun openDatabaseList() {
        openMonitorEntry()
        rule.onNodeWithText(ctx.getString(R.string.database_records_title)).performClick()
        rule.waitForIdle()
    }

    private fun record(ts: Long, kind: String, algorithm: String, operation: String) =
        CryptoRecordEntity(
            packageName = packageName,
            kind = kind,
            operation = operation,
            algorithm = algorithm,
            threadName = "timeline-test",
            timestamp = ts,
        )

    private fun dbRecord(
        ts: Long,
        kind: String,
        databaseName: String,
        operation: String,
        sql: String,
    ) = CryptoRecordEntity(
        recordType = com.lioisme.modev.core.model.RecordTypes.DATABASE,
        packageName = packageName,
        kind = kind,
        operation = operation,
        algorithm = databaseName,
        sql = sql,
        threadName = "timeline-test",
        timestamp = ts,
    )

    @Test
    fun timelineGroups_sameSecondSingleHeader_crossSecondAndCrossDayHeaders() {
        rule.waitForIdle()
        openAlgorithmList()

        val locale: Locale =
            LocaleManager.wrap(ctx).resources.configuration.locales[0] ?: Locale.getDefault()
        val is24h = DateFormat.is24HourFormat(ctx)

        // 分页异步加载,有界轮询等待首个组头出现(与既有 androidTest 轮询风格一致)。
        val headersDeadline = System.currentTimeMillis() + 20_000
        while (System.currentTimeMillis() < headersDeadline) {
            rule.waitForIdle()
            if (rule.onAllNodesWithTag("timeline-header").fetchSemanticsNodes().isNotEmpty()) break
            Thread.sleep(100)
        }
        rule.onAllNodesWithTag("timeline-header").onFirst().assertIsDisplayed()
        // 首项(r4)恒为组起始:日期 + 秒级时间。
        rule.onNodeWithText(timelineLabel(s2, locale, is24h)).assertIsDisplayed()
        // 跨秒新头(同日仅秒级时间),同秒组仅此一个头(单头不重复)。
        // 计数须限定在组头节点内:行尾时间与组头同日格式同串,防误伤。
        val sameSecondLabel = groupLabelOrNull(s1, s2, locale, is24h)
        rule.onAllNodes(hasTestTag("timeline-header") and hasAnyDescendant(hasText(sameSecondLabel!!)))
            .assertCountEquals(1)
        // 跨日头带日期前缀。
        rule.onNodeWithText(groupLabelOrNull(d1, s1, locale, is24h)!!).assertIsDisplayed()
    }

    @Test
    fun timelineGroups_dbRowRendersWithDbNameAndSqlPreview() {
        rule.waitForIdle()
        openDatabaseList()

        // DB 行:数据库名(元信息行)与 SQL 预览(摘要行)均可见;算法行不出现。
        rule.onAllNodes(hasScrollAction()).onFirst()
            .performScrollToNode(hasText("t14.db", substring = true))
        rule.onNodeWithText("t14.db", substring = true).assertIsDisplayed()
        rule.onNodeWithText("INSERT INTO t (name) VALUES (?)", substring = true).assertIsDisplayed()
        rule.onAllNodes(hasText("SHA-256")).assertCountEquals(0)
    }

    @Test
    fun timelineGroups_dbKindFilterShowsOnlyDbKinds() {
        rule.waitForIdle()
        openDatabaseList()
        rule.onNodeWithText("t14.db", substring = true).assertIsDisplayed()

        // 类别筛选(行为级):点击「数据库打开」→ DML 行隐(只读 Radio 语义不可断言;
        // DropdownMenuItem 与分组标题同串,用 hasClickAction 限定菜单项)。
        rule.onAllNodesWithContentDescription(ctx.getString(R.string.cd_filter)).onFirst().performClick()
        rule.waitForIdle()
        rule.onNode(hasText(ctx.getString(R.string.kind_db_open)) and hasClickAction()).performClick()
        rule.waitForIdle()
        rule.onAllNodes(hasText("t14.db", substring = true)).assertCountEquals(0)

        // 复位全部类别:DML 行回归。
        rule.onAllNodesWithContentDescription(ctx.getString(R.string.cd_filter)).onFirst().performClick()
        rule.waitForIdle()
        rule.onNode(hasText(ctx.getString(R.string.record_types_all)) and hasClickAction()).performClick()
        rule.waitForIdle()
        rule.onNodeWithText("t14.db", substring = true).assertIsDisplayed()
    }

    @Test
    fun timelineGroups_dbRowDetailShowsSqlSection() {
        rule.waitForIdle()
        openDatabaseList()

        // 点击 DB 行进入详情页。
        rule.onAllNodes(hasScrollAction()).onFirst()
            .performScrollToNode(hasText("t14.db", substring = true))
        rule.onNodeWithText("t14.db", substring = true).performClick()
        rule.waitForIdle()

        // 详情页 SQL 节:节标题 + 全量 SQL 可见(短文本不折叠)。
        val sectionDeadline = System.currentTimeMillis() + 10_000
        var sectionFound = false
        while (System.currentTimeMillis() < sectionDeadline) {
            rule.waitForIdle()
            val nodes = rule.onAllNodes(hasText(ctx.getString(R.string.section_sql))).fetchSemanticsNodes()
            if (nodes.isNotEmpty()) {
                sectionFound = true
                break
            }
            Thread.sleep(100)
        }
        assertTrue("详情页应渲染 SQL 节标题", sectionFound)
        // SQL 文本同时出现在顶栏标题(sqlHeadline)与 SQL 节内,取首节点断言可见。
        rule.onAllNodes(hasText("INSERT INTO t (name) VALUES (?)", substring = true)).onFirst().assertIsDisplayed()
    }
}