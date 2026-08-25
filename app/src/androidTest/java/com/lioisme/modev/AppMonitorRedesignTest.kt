package com.lioisme.modev

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
 * 应用详情页重构验收(V23 R5/T5.2):
 * - Hero 卡:图标/名称/状态胶囊/作用域 Switch 行(testTag app-scope-switch);
 * - 统计概览卡(app-stats-card):总数大字 + 类型分布图例(资源驱动文案);
 * - 功能入口卡:算法记录 / 数据库记录 / 监听设置(monitor-entry-listen-settings),
 *   「监听设置」摘要 = 使用默认配置(未定制时);
 * - 旧「更多菜单」退役:清空记录入口归位操作卡(detail_clear_records 行)。
 * 断言一律资源驱动;行内文本经 hasAnyDescendant 限定避免合并节点误命中。
 */
@RunWith(AndroidJUnit4::class)
class AppMonitorRedesignTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val ownPackage = "com.lioisme.modev"

    @Before
    fun seed() {
        // 自身是 Xposed 模块默认隐藏:开启显隐使被测包行可见(@After 复位)。
        AppListSettings.setShowModules(true)
        val dao = ModevDatabase.get(ctx).cryptoRecordDao()
        runBlocking {
            dao.clearAll()
            val now = System.currentTimeMillis()
            dao.insertAll(
                listOf(
                    record(now - 60_000, kind = "MESSAGE_DIGEST", operation = "DIGEST"),
                    dbRecord(now - 10_000),
                ),
            )
            assertEquals(2L, dao.count(ownPackage))
        }
        rule.waitForIdle()
    }

    @After
    fun restore() {
        AppListSettings.setShowModules(false)
    }

    private fun record(ts: Long, kind: String, operation: String) =
        CryptoRecordEntity(
            packageName = ownPackage,
            kind = kind,
            operation = operation,
            algorithm = "AES",
            threadName = "monitor-redesign-test",
            timestamp = ts,
        )

    private fun dbRecord(ts: Long) =
        CryptoRecordEntity(
            recordType = RecordTypes.DATABASE,
            packageName = ownPackage,
            kind = "DML",
            operation = "INSERT",
            algorithm = "t-redesign.db",
            sql = "INSERT INTO t VALUES (?)",
            threadName = "monitor-redesign-test",
            timestamp = ts,
        )

    private fun openMonitorEntry() {
        // 行可能在 LazyColumn 组合窗口外(列表长/被置顶分区推下),先滚动定位
        // (与 MonitorListScreensTest 同款轮询滚动,裸 onNodeWithText 找不到屏外行)。
        val deadline = System.currentTimeMillis() + 10_000
        var opened = false
        while (System.currentTimeMillis() < deadline && !opened) {
            rule.waitForIdle()
            try {
                val scrollables = rule.onAllNodes(hasScrollAction()).fetchSemanticsNodes()
                if (scrollables.isNotEmpty()) {
                    rule.onAllNodes(hasScrollAction()).onFirst()
                        .performScrollToNode(hasText(ownPackage))
                }
                rule.onNodeWithText(ownPackage).performClick()
                opened = true
            } catch (_: AssertionError) {
                Thread.sleep(100)
            }
        }
        assertTrue("应进入应用详情页", opened)
        rule.waitForIdle()
    }

    @Test
    fun heroCard_scopeSwitch_andEntryRows_rendered() {
        openMonitorEntry()

        // Hero:作用域开关行与统计卡存在。
        rule.onNodeWithTag("app-scope-row").assertIsDisplayed()
        rule.onNodeWithTag("app-stats-card").assertIsDisplayed()

        // 三入口卡:监听设置入口存在且摘要为「使用默认配置」(未定制)。
        val entry = rule.onNodeWithTag("monitor-entry-listen-settings")
        entry.performScrollTo().assertIsDisplayed()
        assertTrue(
            "未定制时应显示「使用默认配置」",
            rule.onAllNodesWithText(str(R.string.listen_summary_default))
                .fetchSemanticsNodes().isNotEmpty(),
        )
    }

    @Test
    fun statsCard_legend_showsTypeCounts() {
        openMonitorEntry()

        rule.onNodeWithTag("app-stats-card").assertIsDisplayed()
        // 分布图例(资源驱动):算法 / 数据库标签出现于统计卡内。
        rule.onNode(
            hasTestTag("app-stats-card") and hasAnyDescendant(hasText(str(R.string.filter_type_crypto))),
        ).assertIsDisplayed()
    }

    @Test
    fun clearRecords_livesInActionsSection_withDangerConfirm() {
        openMonitorEntry()

        // 操作卡内的清空行(屏外行先滚动再点击;危险确认对话框可取消,非破坏路径)。
        rule.onNodeWithText(str(R.string.detail_clear_records))
            .performScrollTo()
            .performClick()
        rule.waitForIdle()
        rule.onNodeWithText(str(R.string.danger_title)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.cancel)).performClick()
        rule.waitForIdle()
        runBlocking {
            assertEquals("取消后记录不得被清除", 2L, ModevDatabase.get(ctx).cryptoRecordDao().count(ownPackage))
        }
    }

    private fun str(id: Int) = ctx.getString(id)
}
