package com.lioisme.modev

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
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
 * 按类型全局记录列表 e2e(V41 T7,R8 复用锁定):
 * 路径 = 根页设置 → 数据管理 `data-type-crypto` 计数行 → `records/type/crypto` 全局列表。
 * 锁定契约:
 * - 复用一致性(V41 V8.2 闭合):全局列表与每应用列表走同一渲染管线——
 *   `record-row` 行、Appbar 内 SearchBar(返回/筛选各 1,无设置按钮)、
 *   actions 恒空(#24 排他)、搜索聚焦即展开(MD3 标准,V40 T5.1)且两入口行为一致;
 * - 全局形态:跨应用聚合(own + victim 包行均现)、行内应用名上下文;
 * - 清空流程:`records-clear-type` 搜索条动作槽 → `records-clear-confirm` 居中确认
 *   → 该类型全部应用清空(VM 层 RecordsViewModelGlobalTest 的插桩对账)。
 * 前置:清空全库(确定性)+ 开启「显示 Xposed 模块」,@After 复位。
 */
@RunWith(AndroidJUnit4::class)
class GlobalTypeRecordsTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val ownPackage = "com.lioisme.modev"
    private val victimPackage = "com.example.victim"

    private fun str(id: Int) = ctx.getString(id)

    private fun record(pkg: String, kind: String, algorithm: String, operation: String, ts: Long) =
        CryptoRecordEntity(
            packageName = pkg,
            kind = kind,
            operation = operation,
            algorithm = algorithm,
            threadName = "global-type-test",
            timestamp = ts,
        )

    @Before
    fun seed() {
        val dao = ModevDatabase.get(ctx).cryptoRecordDao()
        runBlocking {
            dao.clearAll()
            val now = System.currentTimeMillis()
            dao.insertAll(
                listOf(
                    record(ownPackage, "MESSAGE_DIGEST", "SHA-256", "DIGEST", now - 60_000),
                    record(victimPackage, "CIPHER", "DES", "ENCRYPT", now - 30_000),
                ),
            )
            // 本例先 clearAll() 且种子跨两包(全局列表聚合语境)⇒ 不变量 = 「全表恰为我种的这些行」,
            // 计数必须按全局口径(仅 clearPackage 的用例才按包scoped)。
            val seeded = dao.countByQuery(RecordQueryFactory.count(RecordFilter())).first()
            assertEquals("种子写入须在读回中可见", 2L, seeded)
        }
        AppListSettings.setShowModules(true)
        rule.waitForIdle()
    }

    @After
    fun restore() {
        AppListSettings.setShowModules(false)
    }

    /** 根页 → 设置页 → 数据管理页。 */
    private fun openDataSettings() {
        rule.waitForIdle()
        rule.onNodeWithContentDescription(str(R.string.cd_settings)).performClick()
        rule.waitForIdle()
        rule.onNodeWithText(str(R.string.data_settings_title))
            .performScrollTo()
            .performClick()
        rule.waitForIdle()
    }

    /** 数据管理页 → crypto 类型全局列表(等待计数行渲染,有界轮询)。 */
    private fun openCryptoGlobalList() {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            rule.waitForIdle()
            val nodes =
                rule.onAllNodes(hasTestTag("data-type-crypto")).fetchSemanticsNodes()
            if (nodes.isNotEmpty()) break
            Thread.sleep(100)
        }
        rule.onAllNodesWithTag("data-type-crypto").onFirst().performClick()
        rule.waitForIdle()
        // 全局列表落地:搜索占位符出现(类型语境化,非单应用占位)。
        val placeholderDeadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < placeholderDeadline) {
            rule.waitForIdle()
            if (rule.onAllNodesWithText(str(R.string.algorithm_records_search))
                    .fetchSemanticsNodes().isNotEmpty()
            ) {
                return
            }
            Thread.sleep(100)
        }
        rule.onNodeWithText(str(R.string.algorithm_records_search)).assertIsDisplayed()
    }

    /** 行级断言:含某文本的记录行数(V40 T5.3 合并节点口径)。 */
    private fun rowCount(text: String, substring: Boolean = false) =
        rule.onAllNodes(
            hasTestTag("record-row") and hasText(text, substring = substring),
        ).fetchSemanticsNodes().size

    /** 有界轮询等待行出现。 */
    private fun waitForVisible(text: String, substring: Boolean = false) {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            rule.waitForIdle()
            if (rule.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty()) return
            Thread.sleep(100)
        }
        rule.onAllNodes(hasText(text, substring = substring)).onFirst().assertIsDisplayed()
    }

    @Test
    fun globalList_reusesPipeline_crossPackageAggregation() {
        openDataSettings()
        openCryptoGlobalList()

        // Appbar 内仅 SearchBar:返回 1 + 筛选 1,无设置按钮(#24 actions 恒空同源断言)。
        rule.onAllNodesWithContentDescription(str(R.string.cd_back)).assertCountEquals(1)
        rule.onAllNodesWithContentDescription(str(R.string.cd_filter)).assertCountEquals(1)
        rule.onAllNodesWithContentDescription(str(R.string.cd_settings)).assertCountEquals(0)

        // 跨应用聚合:own(SHA-256)+ victim(DES)两行均现。
        waitForVisible("SHA-256")
        waitForVisible("DES")
        assertEquals(1, rowCount("SHA-256"))
        assertEquals(1, rowCount("DES"))

        // 全局形态行内应用名上下文(showPackage=true):own 包行携带目录应用名标签。
        // 注:未安装包不入目录 → contextLabel 为 null(现网正确回落行为),
        // 故以已安装的自身包行断言。V40 T5.3 合并节点口径:槽文本在节点自身。
        val appLabel = ctx.getString(R.string.app_name)
        assertTrue(
            "全局列表行内应携带应用名上下文",
            rule.onAllNodes(
                hasTestTag("record-row") and hasText(appLabel, substring = true),
            ).fetchSemanticsNodes().isNotEmpty(),
        )

        // 搜索聚焦即展开 = MD3 标准(V40 T5.1 契约;与单应用列表同一 SearchInputField 单源)。
        // V40 T5.1:折叠条(标准 InputField)+ 展开视图双输入域并存 → 取 Dialog 窗口内者
        // (与 MonitorListScreensTest 同款选择器)。
        rule.onAllNodesWithText(str(R.string.algorithm_records_search)).onFirst()
            .performClick()
        rule.waitForIdle()
        rule.onNode(
            hasSetTextAction() and hasAnyAncestor(isDialog()),
        ).performTextInput("SHA")
        rule.waitForIdle()
        waitForVisible("SHA-256")
    }

    @Test
    fun globalList_clearTypeFlow_clearsAllPackagesWithConfirm() {
        openDataSettings()
        openCryptoGlobalList()
        waitForVisible("SHA-256")

        // 清空入口在搜索条动作槽(折叠态同 tag),确认弹窗为居中 ModevConfirmDialog。
        rule.onAllNodesWithTag("records-clear-type").onFirst().performClick()
        rule.waitForIdle()
        rule.onNodeWithText(str(R.string.danger_clear_algorithm_msg)).assertIsDisplayed()
        rule.onNodeWithTag("records-clear-confirm").performClick()
        rule.waitForIdle()

        // 该类型全部应用清空(全局语境),回到空列表。
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            rule.waitForIdle()
            if (rule.onAllNodes(hasTestTag("record-row")).fetchSemanticsNodes().isEmpty()) return
            Thread.sleep(100)
        }
        rule.onAllNodes(hasTestTag("record-row")).assertCountEquals(0)
    }
}
