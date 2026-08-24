package com.lioisme.modev

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToLog
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lioisme.modev.R
import com.lioisme.modev.core.model.RecordTypes
import com.lioisme.modev.data.db.CryptoRecordEntity
import com.lioisme.modev.data.db.ModevDatabase
import com.lioisme.modev.data.repo.AppListSettings
import com.lioisme.modev.ui.MainActivity
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
 * 单应用监听列表界面验收(V19 R1;V20 单应用语境化):
 * 种子数据 = 被测包(crypto×2 + db×1)+ 外部包(crypto×1),断言:
 * - 入口页「算法记录 / 数据库记录」直达**单应用**列表页(非设置页),两界面独立;
 * - 单应用隔离:外部包行不出现(行内包名已随语境收敛移除);
 * - AppBar 内仅 SearchBar(搜索占位 + 1 个筛选按钮 + 返回);
 * - 算法列表仅算法行、数据库列表仅数据库行(类型隔离);
 * - 类别筛选(行为级:点击项 → 行现/行隐)与搜索词过滤;
 * - 返回栈:列表页 → 入口页。
 * 行断言一律以 hasTestTag("record-row") 限定(搜索展开时 Scaffold 与 Dialog
 * 双份渲染同一列表,裸文本断言会多命中;与时间线组头同款限定契约)。
 * 前置:清空全库(列表确定性)+ 开启「显示 Xposed 模块」,@After 复位。
 */
@RunWith(AndroidJUnit4::class)
class MonitorListScreensTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val ownPackage = "com.lioisme.modev"
    private val victimPackage = "com.example.victim"

    private fun str(id: Int) = ctx.getString(id)

    @Before
    fun seed() {
        val dao = ModevDatabase.get(ctx).cryptoRecordDao()
        runBlocking {
            // 全局列表跨应用聚合:清空全库保证断言确定性。
            dao.clearAll()
            val now = System.currentTimeMillis()
            dao.insertAll(
                listOf(
                    record(ownPackage, "MESSAGE_DIGEST", "SHA-256", "DIGEST", now - 60_000),
                    record(ownPackage, "CIPHER", "AES", "ENCRYPT", now - 30_000),
                    dbRecord(ownPackage, "DML", "t14.db", "INSERT", "INSERT INTO t (name) VALUES (?)", now - 10_000),
                    record(victimPackage, "CIPHER", "DES", "ENCRYPT", now - 5_000),
                ),
            )
            val seeded = dao
                .observeCountByFilter(
                    // 哨兵绑定(内容无意义,由 typeCount=0 短路),空集合直接绑会生成非法 IN ()。
                    recordTypes = listOf(EMPTY_IN_SENTINEL),
                    packages = listOf(EMPTY_IN_SENTINEL),
                    kinds = listOf(EMPTY_IN_SENTINEL),
                    typeCount = 0,
                    packageCount = 0,
                    kindCount = 0,
                    query = null,
                )
                .first()
            assertEquals("种子写入须在读回中可见", 4L, seeded)
        }
        // 被测应用默认隐藏(自身是 Xposed 模块),开启显隐使行可见。
        AppListSettings.setShowModules(true)
        rule.waitForIdle()
    }

    @After
    fun restore() {
        AppListSettings.setShowModules(false)
    }

    private fun record(pkg: String, kind: String, algorithm: String, operation: String, ts: Long) =
        CryptoRecordEntity(
            packageName = pkg,
            kind = kind,
            operation = operation,
            algorithm = algorithm,
            threadName = "monitor-list-test",
            timestamp = ts,
        )

    private fun dbRecord(
        pkg: String,
        kind: String,
        databaseName: String,
        operation: String,
        sql: String,
        ts: Long,
    ) = CryptoRecordEntity(
        recordType = RecordTypes.DATABASE,
        packageName = pkg,
        kind = kind,
        operation = operation,
        algorithm = databaseName,
        sql = sql,
        threadName = "monitor-list-test",
        timestamp = ts,
    )

    /** 等待被测包行出现并进入应用监听入口页(与 TimelineListTest 同款轮询)。 */
    private fun openMonitorEntry() {
        val deadline = System.currentTimeMillis() + 10_000
        var rowFound = false
        while (System.currentTimeMillis() < deadline) {
            rule.waitForIdle()
            val scrollables = rule.onAllNodes(hasScrollAction()).fetchSemanticsNodes()
            if (scrollables.isNotEmpty()) {
                try {
                    rule.onAllNodes(hasScrollAction()).onFirst()
                        .performScrollToNode(hasText(ownPackage))
                    rowFound = true
                    break
                } catch (_: AssertionError) {
                    // 目标行未加载/被模块显隐过滤,下一轮重试。
                }
            }
            Thread.sleep(100)
        }
        assertTrue("等待「显示 Xposed 模块」生效后被测包行应出现", rowFound)
        rule.onNodeWithText(ownPackage).performClick()
        rule.waitForIdle()
        // V20:入口页两记录入口(单应用列表)。
        rule.onNodeWithText(str(R.string.algorithm_records_title)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.database_records_title)).assertIsDisplayed()
    }

    private fun openList(entryLabel: String) {
        rule.onNodeWithText(entryLabel).performClick()
        rule.waitForIdle()
    }

    /** 有界轮询等待行出现(substring 供库名等非整串文本)。 */
    private fun waitForVisible(text: String, substring: Boolean = false) {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            rule.waitForIdle()
            if (rule.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty()) return
            Thread.sleep(100)
        }
        dumpTree()
        rule.onNode(hasText(text, substring = substring)).assertIsDisplayed()
    }

    /** 有界轮询等待行消失(substring 供库名等非整串文本)。 */
    private fun waitForHidden(text: String, substring: Boolean = false) {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            rule.waitForIdle()
            if (rule.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isEmpty()) return
            Thread.sleep(100)
        }
        dumpTree()
        rule.onAllNodes(hasText(text, substring = substring)).assertCountEquals(0)
    }

    /** 行级断言:含某文本的记录行(列表渲染两份时按节点计数)。 */
    private fun rowCount(text: String, substring: Boolean = false) =
        rule.onAllNodes(hasTestTag("record-row") and hasAnyDescendant(hasText(text, substring = substring)))
            .fetchSemanticsNodes().size

    /** 超时诊断:打印当前语义树(全部 root 窗口,含 Dialog)。 */
    private fun dumpTree() {
        val rootCount = rule.onAllNodes(isRoot()).fetchSemanticsNodes().size
        for (i in 0 until rootCount) {
            try {
                rule.onAllNodes(isRoot())[i].printToLog("MonitorList")
            } catch (_: Throwable) {
                // 树变化竞态,忽略单次失败。
            }
        }
    }

    @Test
    fun algorithmList_isolatedType_onlySearchBarInAppBar() {
        rule.waitForIdle()
        openMonitorEntry()

        // 入口页 → 算法记录 → 单应用算法记录列表(非设置页:无总开关行)。
        openList(str(R.string.algorithm_records_title))
        rule.onNodeWithText(str(R.string.algorithm_records_search)).assertIsDisplayed()
        rule.onAllNodesWithContentDescription(str(R.string.algorithm_monitor_master)).assertCountEquals(0)

        // AppBar 内仅 SearchBar:返回 1(搜索条内)+ 筛选 1,无设置按钮。
        rule.onAllNodesWithContentDescription(str(R.string.cd_back)).assertCountEquals(1)
        rule.onAllNodesWithContentDescription(str(R.string.cd_filter)).assertCountEquals(1)
        rule.onAllNodesWithContentDescription(str(R.string.cd_settings)).assertCountEquals(0)
        // 无应用头部(无启动按钮)。
        rule.onAllNodesWithText(str(R.string.apps_launch)).assertCountEquals(0)

        // 类型 + 单应用隔离:仅被测包算法行(外部包 DES 与数据库行均不出现)。
        waitForVisible("SHA-256")
        assertEquals(1, rowCount("SHA-256"))
        assertEquals(1, rowCount("AES"))
        waitForHidden("DES")
        waitForHidden("t14.db", substring = true)
    }

    @Test
    fun databaseList_isolatedType_onlySearchBarInAppBar() {
        rule.waitForIdle()
        openMonitorEntry()

        openList(str(R.string.database_records_title))
        rule.onNodeWithText(str(R.string.database_records_search)).assertIsDisplayed()
        rule.onAllNodesWithContentDescription(str(R.string.database_monitor_master)).assertCountEquals(0)
        rule.onAllNodesWithContentDescription(str(R.string.cd_back)).assertCountEquals(1)
        rule.onAllNodesWithContentDescription(str(R.string.cd_filter)).assertCountEquals(1)
        rule.onAllNodesWithContentDescription(str(R.string.cd_settings)).assertCountEquals(0)

        // 类型隔离:仅数据库行(算法行消失)。
        waitForVisible("t14.db", substring = true)
        assertEquals(1, rowCount("t14.db", substring = true))
        waitForHidden("SHA-256")
        waitForHidden("AES")
    }

    @Test
    fun algorithmList_kindFilterAndSearchFilter() {
        rule.waitForIdle()
        openMonitorEntry()
        openList(str(R.string.algorithm_records_title))

        // 类别筛选(行为级):点击 Cipher → AES 行现、SHA-256 行隐。
        rule.onNodeWithContentDescription(str(R.string.cd_filter)).performClick()
        rule.waitForIdle()
        rule.onNode(hasText(str(R.string.kind_cipher)) and hasClickAction()).performClick()
        rule.waitForIdle()
        waitForVisible("AES")
        assertEquals(1, rowCount("AES"))
        waitForHidden("SHA-256")

        // 复位全部类别:两行回归。
        rule.onNodeWithContentDescription(str(R.string.cd_filter)).performClick()
        rule.waitForIdle()
        rule.onNode(hasText(str(R.string.record_types_all)) and hasClickAction()).performClick()
        rule.waitForIdle()
        waitForVisible("SHA-256")
        assertEquals(1, rowCount("AES"))

        // 搜索词过滤(展开搜索视图输入 → SQL 侧过滤)。
        rule.onNodeWithText(str(R.string.algorithm_records_search)).performClick()
        rule.waitForIdle()
        rule.onNode(hasSetTextAction()).performTextInput("AES")
        rule.waitForIdle()
        waitForHidden("SHA-256")
        // 收起搜索视图(Dialog 内返回键;物理返回键在该测试环境不达 Dialog 窗口)。
        rule.onAllNodes(
            hasAnyAncestor(isDialog()) and hasContentDescription(str(R.string.cd_back)),
        ).onFirst().performClick()
        rule.waitForIdle()
        // 展开期间查询已同步:折叠条回显 AES;退出动画期间 Scaffold + Dialog
        // 双份列表并存,有界轮询等待收拢为单份(仅 Scaffold 列表)。
        val deadline = System.currentTimeMillis() + 10_000
        var collapsed = false
        while (System.currentTimeMillis() < deadline) {
            rule.waitForIdle()
            if (rowCount("AES") == 1 && rowCount("SHA-256") == 0) {
                collapsed = true
                break
            }
            Thread.sleep(100)
        }
        assertTrue("收起后应仅剩单份 AES 行,实际 AES=${rowCount("AES")} SHA=${rowCount("SHA-256")}", collapsed)
    }

    @Test
    fun backFromListReturnsToMonitorEntry() {
        rule.waitForIdle()
        openMonitorEntry()

        openList(str(R.string.algorithm_records_title))
        rule.onNodeWithText(str(R.string.algorithm_records_search)).assertIsDisplayed()
        // 返回键(搜索条内)→ 入口页;再进数据库列表 → 返回同样回入口页。
        rule.onNodeWithContentDescription(str(R.string.cd_back)).performClick()
        rule.waitForIdle()
        rule.onNodeWithText(str(R.string.database_records_title)).assertIsDisplayed()
        openList(str(R.string.database_records_title))
        rule.onNodeWithText(str(R.string.database_records_search)).assertIsDisplayed()
        rule.onNodeWithContentDescription(str(R.string.cd_back)).performClick()
        rule.waitForIdle()
        rule.onNodeWithText(str(R.string.algorithm_records_title)).assertIsDisplayed()
    }

    companion object {
        /** Room IN 哨兵(与 LogRepository.toBindList 同源:内容无意义,由计数短路)。 */
        private const val EMPTY_IN_SENTINEL = ""
    }
}