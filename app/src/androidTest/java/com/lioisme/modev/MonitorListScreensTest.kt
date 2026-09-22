package com.lioisme.modev

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.printToLog
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lioisme.modev.R
import com.lioisme.modev.core.model.RecordTypes
import com.lioisme.modev.data.db.CryptoRecordEntity
import com.lioisme.modev.data.db.ModevDatabase
import com.lioisme.modev.data.db.RecordQueryFactory
import com.lioisme.modev.domain.record.RecordFilter
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
            // clearAll + 种子跨两包 ⇒ 不变量 = 全表恰为我种的四行,计数按全局口径。
            val seeded = dao.countByQuery(RecordQueryFactory.count(RecordFilter())).first()
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
        // V20:入口页两记录入口(单应用列表)。V40 T4 健壮性:入口行位于
        // Hero/统计区之下,先滚动至可见再断言(修复 V39 报告记录的预存假失败)。
        rule.onNodeWithText(str(R.string.algorithm_records_title))
            .performScrollTo()
            .assertIsDisplayed()
        rule.onNodeWithText(str(R.string.database_records_title))
            .performScrollTo()
            .assertIsDisplayed()
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

    /**
     * 行级断言:含某文本的记录行(列表渲染两份时按节点计数)。
     * V40 T5.3:RecordRow 改 M3 ListItem 后内容槽合并为单一语义节点,
     * 行内文本不再是后代 → testTag 节点自身 Text 匹配(AGENTS #13 同源口径)。
     */
    private fun rowCount(text: String, substring: Boolean = false) =
        rule.onAllNodes(
            hasTestTag("record-row") and hasText(text, substring = substring),
        ).fetchSemanticsNodes().size

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
        // V43 T4 后记录行自身也承载类别词元(「加密」)且整行可点 ⇒ 按文本 + 可点匹配会
        // 同时命中菜单项与记录行(T3.7 的 kind-filter-* 锚点就是为此而设)。
        rule.onNodeWithContentDescription(str(R.string.cd_filter)).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("kind-filter-CIPHER").performClick()
        rule.waitForIdle()
        waitForVisible("AES")
        assertEquals(1, rowCount("AES"))
        waitForHidden("SHA-256")

        // 复位全部类别:两行回归。
        rule.onNodeWithContentDescription(str(R.string.cd_filter)).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("kind-filter-all").performClick()
        rule.waitForIdle()
        waitForVisible("SHA-256")
        assertEquals(1, rowCount("AES"))

        // 搜索词过滤(展开搜索视图输入 → SQL 侧过滤)。
        rule.onAllNodesWithText(str(R.string.algorithm_records_search)).onFirst().performClick()
        // Dialog 是新窗口:单次 waitForIdle 不保证首帧已组合(高负载机器上随机红),
        // 改有界等待并让超时消息定位到「展开未生效」。
        rule.awaitSearchDialogInput("点折叠搜索条应展开全屏搜索视图")
        // V40 T5.1:折叠条(标准 InputField)+ 展开视图双输入域并存 → 取 Dialog 窗口内者。
        rule.onNode(
            hasSetTextAction() and hasAnyAncestor(isDialog()),
        ).performTextInput("AES")
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

    @Test
    fun inApp_longPressOnClearTypeSlot_doesNotExpandSearch() {
        // V42 T1 实机锚点(组件探针之外的整链路验证):Appbar 内长按清空动作槽
        // (records-clear-type)不得展开全屏搜索(V42 前长按经焦点链路误触展开);
        // 单击仍弹危险确认,取消后列表保持。折叠/展开双份槽取首个(折叠条)。
        rule.waitForIdle()
        openMonitorEntry()
        openList(str(R.string.algorithm_records_title))
        waitForVisible("SHA-256")

        // 长按动作槽(折叠条内)→ tooltip 显示为设计行为,但零展开。
        rule.onAllNodesWithTag("records-clear-type").onFirst()
            .performTouchInput { longClick() }
        rule.waitForIdle()
        Thread.sleep(SLOT_EXPAND_GRACE_MS + 200)
        rule.waitForIdle()
        // 展开态判据:Dialog 窗口内出现第二份搜索输入域(展开后双输入域并存)。
        val expandedInputs = rule.onAllNodes(
            hasSetTextAction() and hasAnyAncestor(isDialog()),
        ).fetchSemanticsNodes().size
        assertEquals("长按清空槽不得展开搜索视图", 0, expandedInputs)

        // 单击仍走危险确认(V42 门禁不得误伤点击)。确认对话框同为**新窗口**,
        // 先有界等待其锚点(超时消息 = 「点击被吞」),再断言文案显示性。
        rule.onAllNodesWithTag("records-clear-type").onFirst().performClick()
        rule.awaitAnchor("records-clear-confirm", "点清空槽应弹出危险确认")
        rule.onNodeWithText(str(R.string.danger_clear_algorithm_msg)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.cancel)).performClick()
        rule.waitForIdle()
        waitForVisible("SHA-256")
        assertEquals(1, rowCount("SHA-256"))
    }

}

/** 主源槽展开宽限(internal 可见,探针等待窗与门禁同源)。 */
private val SLOT_EXPAND_GRACE_MS = com.lioisme.modev.ui.components.SLOT_EXPAND_GRACE_MS