package com.lioisme.modev

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lioisme.modev.core.model.RecordTypes
import com.lioisme.modev.data.db.CryptoRecordEntity
import com.lioisme.modev.data.db.ModevDatabase
import com.lioisme.modev.ui.MainActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 全局统一记录页验收(V23 R1/G1):
 * - 入口 = 应用列表顶栏「查看全部记录」,直达全局页(非设置/单应用列表);
 * - 全类型聚合:算法 + 数据库、跨包记录同列时间线;
 * - 类型 SegmentedButton 单选:算法/数据库互斥切换;
 * - 应用多选(ModalBottomSheet):勾选即时过滤,chip 计数更新,清除恢复;
 * - 返回语义:搜索条返回键回应用列表。
 * 行断言以 hasTestTag("record-row") 限定(sheet 打开时双份渲染防多命中)。
 * 前置:清空全库保证断言确定性。
 */
@RunWith(AndroidJUnit4::class)
class GlobalRecordsScreenTest {

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
            dao.clearAll()
            val now = System.currentTimeMillis()
            dao.insertAll(
                listOf(
                    record(ownPackage, "MESSAGE_DIGEST", "SHA-256", "DIGEST", now - 60_000),
                    record(victimPackage, "CIPHER", "DES", "ENCRYPT", now - 30_000),
                    dbRecord(victimPackage, "DML", "t23.db", "INSERT", "INSERT INTO t VALUES (?)", now - 10_000),
                ),
            )
            val seeded = dao
                .observeCountByFilter(
                    recordTypes = listOf(EMPTY_IN_SENTINEL),
                    packages = listOf(EMPTY_IN_SENTINEL),
                    kinds = listOf(EMPTY_IN_SENTINEL),
                    typeCount = 0,
                    packageCount = 0,
                    kindCount = 0,
                    query = null,
                )
                .first()
            assertEquals("种子写入须在读回中可见", 3L, seeded)
        }
        rule.waitForIdle()
    }

    private fun record(pkg: String, kind: String, algorithm: String, operation: String, ts: Long) =
        CryptoRecordEntity(
            packageName = pkg,
            kind = kind,
            operation = operation,
            algorithm = algorithm,
            threadName = "global-records-test",
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
        threadName = "global-records-test",
        timestamp = ts,
    )

    /** 从根页进入全局记录页(顶栏入口)。 */
    private fun openGlobalRecords() {
        rule.waitForIdle()
        rule.onNodeWithContentDescription(str(R.string.cd_records_all)).performClick()
        rule.waitForIdle()
        rule.onNodeWithText(str(R.string.records_all_search)).assertIsDisplayed()
    }

    /** 有界轮询等待行出现(substring 供库名等非整串文本)。 */
    private fun waitForVisible(text: String, substring: Boolean = false) {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            rule.waitForIdle()
            if (rule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()) return
            Thread.sleep(100)
        }
        rule.onNodeWithText(text, substring = substring).assertIsDisplayed()
    }

    /** 行级断言:含某文本的记录行计数。 */
    private fun rowCount(text: String, substring: Boolean = false) =
        rule.onAllNodes(
            hasTestTag("record-row") and hasAnyDescendant(hasText(text, substring = substring)),
        ).fetchSemanticsNodes().size

    @Test
    fun entryFromTopBar_aggregatesAllTypesAndPackages() {
        openGlobalRecords()

        // 三条种子(两包两类)全部呈现:聚合无类型/应用预过滤。
        waitForVisible("SHA-256")
        waitForVisible("DES")
        waitForVisible("t23.db", substring = true)
        assertEquals(1, rowCount("SHA-256"))
        assertEquals(1, rowCount("DES"))
        assertEquals(1, rowCount("t23.db", substring = true))
    }

    @Test
    fun typeSegmentedButtons_narrowToSingleType() {
        openGlobalRecords()

        // 选「算法」:仅 crypto 行。
        rule.onNodeWithText(str(R.string.filter_type_crypto)).performClick()
        rule.waitForIdle()
        waitForVisible("SHA-256")
        waitForHiddenCompat("t23.db")
        assertEquals(1, rowCount("SHA-256"))
        assertEquals(0, rowCount("t23.db", substring = true))

        // 选「数据库」:仅 db 行。
        rule.onNodeWithText(str(R.string.filter_type_database)).performClick()
        rule.waitForIdle()
        waitForVisible("t23.db", substring = true)
        assertEquals(0, rowCount("SHA-256"))
        assertEquals(1, rowCount("t23.db", substring = true))
    }

    @Test
    fun appPickerSheet_filtersBySelection() {
        openGlobalRecords()

        // 打开应用选择器(点击「应用」chip;cd 恒定,选中后 label 变化不影响定位)。
        rule.onNodeWithContentDescription(str(R.string.cd_filter_apps)).performClick()
        rule.waitForIdle()
        rule.onNodeWithText(str(R.string.app_picker_title)).assertIsDisplayed()

        // 勾选 victim 包。
        rule.onNodeWithText(victimPackage).performClick()
        rule.waitForIdle()

        // 完成关闭 sheet:列表仅剩 victim 两行。
        rule.onNodeWithText(str(R.string.app_picker_done)).performClick()
        rule.waitForIdle()
        waitForVisible("DES")
        waitForVisible("t23.db", substring = true)
        assertEquals(0, rowCount("SHA-256"))

        // 再次打开并清除选择,恢复全量。
        rule.onNodeWithContentDescription(str(R.string.cd_filter_apps)).performClick()
        rule.waitForIdle()
        rule.onNodeWithText(str(R.string.app_picker_clear)).performClick()
        rule.onNodeWithText(str(R.string.app_picker_done)).performClick()
        rule.waitForIdle()
        waitForVisible("SHA-256")
        assertEquals(1, rowCount("SHA-256"))
    }

    @Test
    fun backReturnsToAppsRoot() {
        openGlobalRecords()
        rule.onNodeWithContentDescription(str(R.string.cd_back)).performClick()
        rule.waitForIdle()
        assertTrue(
            "应回到应用列表根页",
            rule.onAllNodesWithText(str(R.string.apps_search)).fetchSemanticsNodes().isNotEmpty(),
        )
    }

    private fun waitForHiddenCompat(text: String) {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            rule.waitForIdle()
            if (rowCount(text) == 0) return
            Thread.sleep(100)
        }
        assertEquals(0, rowCount(text))
    }

    companion object {
        /** Room IN 哨兵(与 LogRepository.toBindList 同源)。 */
        private const val EMPTY_IN_SENTINEL = ""
    }
}
