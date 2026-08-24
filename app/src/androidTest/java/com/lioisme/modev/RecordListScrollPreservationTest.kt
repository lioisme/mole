package com.lioisme.modev

import android.view.KeyEvent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
 * 记录列表滚动位置保持验收(V20 R3):
 * 种子 = 120 条被测包算法记录(时间倒序,跨 2 页),流程:
 * 算法列表 → 滚动至第 2 页深位(ALG-090)→ 进详情 → 返回:
 * - 返回后列表不重置回顶部(旧冷流重收集回归),目标行仍可见;
 * - 列表滚动状态由 VM 级 LazyListState 承载(跨详情往返原位恢复)。
 * 前置:清空被测包记录 + 开启「显示 Xposed 模块」,@After 复位。
 */
@RunWith(AndroidJUnit4::class)
class RecordListScrollPreservationTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val ownPackage = "com.lioisme.modev"

    /** 目标行(120 条中第 90 新,位于第 2 页;唯一算法名锚点)。 */
    private val targetAlgorithm = "ALG-090"

    @Before
    fun seed() {
        val dao = ModevDatabase.get(ctx).cryptoRecordDao()
        runBlocking {
            dao.clearPackage(ownPackage)
            val now = System.currentTimeMillis()
            dao.insertAll(
                (0 until 120).map { i ->
                    CryptoRecordEntity(
                        packageName = ownPackage,
                        kind = "MESSAGE_DIGEST",
                        operation = "DIGEST",
                        algorithm = "ALG-%03d".format(i),
                        threadName = "scroll-test",
                        timestamp = now - i * 1_000L,
                    )
                },
            )
            val seeded = dao
                .observeCountByFilter(
                    recordTypes = listOf(""),
                    packages = listOf(ownPackage),
                    kinds = listOf(""),
                    typeCount = 0,
                    packageCount = 1,
                    kindCount = 0,
                    query = null,
                )
                .first()
            assertEquals("种子写入须在读回中可见", 120L, seeded)
        }
        AppListSettings.setShowModules(true)
        rule.waitForIdle()
    }

    @After
    fun restore() {
        AppListSettings.setShowModules(false)
    }

    private fun pressBack() {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        rule.waitForIdle()
    }

    /** 等待被测包行出现 → 入口页 → 算法记录列表。 */
    private fun openAlgorithmList() {
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
        rule.onNodeWithText(ctx.getString(R.string.algorithm_records_title)).performClick()
        rule.waitForIdle()
    }

    private fun waitForNode(text: String): Boolean {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            rule.waitForIdle()
            if (rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()) return true
            Thread.sleep(100)
        }
        return false
    }

    @Test
    fun scrollPositionSurvivesDetailRoundTrip() {
        rule.waitForIdle()
        openAlgorithmList()

        // 滚动至第 2 页深位(时间倒序第 90 新,分页滚动中自动翻页加载)。
        rule.onAllNodes(hasScrollAction()).onFirst()
            .performScrollToNode(hasText(targetAlgorithm))
        rule.waitForIdle()
        assertTrue("目标行须已加载并显示", waitForNode(targetAlgorithm))
        rule.onNodeWithText(targetAlgorithm).assertIsDisplayed()

        // 进详情页(顶栏更多按钮 = 详情页标志)。
        rule.onNodeWithText(targetAlgorithm).performClick()
        rule.waitForIdle()
        val detailDeadline = System.currentTimeMillis() + 10_000
        var detailShown = false
        while (System.currentTimeMillis() < detailDeadline) {
            rule.waitForIdle()
            if (rule.onAllNodesWithContentDescription(ctx.getString(R.string.record_more)).fetchSemanticsNodes().isNotEmpty()) {
                detailShown = true
                break
            }
            Thread.sleep(100)
        }
        assertTrue("详情页应打开", detailShown)

        // 返回列表:位置保持(不重置回顶部)。
        pressBack()
        assertTrue("返回后列表应保持滚动位置(目标行仍可见)", waitForNode(targetAlgorithm))
        rule.onNodeWithText(targetAlgorithm).assertIsDisplayed()

        // 顶行不可见(确证非重置回顶部)。
        assertTrue("列表未回顶部:ALG-000 不可见", !waitForTopStillVisible())
    }

    /** 有界(短)轮询判断顶行 ALG-000 是否可见(返回后应立即保持深位)。 */
    private fun waitForTopStillVisible(): Boolean {
        val deadline = System.currentTimeMillis() + 1_500
        while (System.currentTimeMillis() < deadline) {
            rule.waitForIdle()
            if (rule.onAllNodesWithText("ALG-000").fetchSemanticsNodes().isNotEmpty()) return true
            Thread.sleep(100)
        }
        return false
    }
}