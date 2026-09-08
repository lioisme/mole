package com.lioisme.modev

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lioisme.modev.R
import com.lioisme.modev.core.model.RecordType
import com.lioisme.modev.core.model.RecordTypes
import com.lioisme.modev.data.db.CryptoRecordEntity
import com.lioisme.modev.data.db.ModevDatabase
import com.lioisme.modev.data.repo.LogRepository
import com.lioisme.modev.data.server.ConfigServer
import com.lioisme.modev.ui.settings.DataSettingsScreen
import com.lioisme.modev.ui.settings.DataSettingsViewModel
import com.lioisme.modev.ui.settings.DataSettingsViewModelFactory
import com.lioisme.modev.ui.settings.StorageFormat
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 数据管理页验收(V19 T7;V40 T4 双维度;V41 T5 收敛):
 * - 分类计数(可点 → 全局列表回调);
 * - 「按应用浏览」已退役(V41)——`data-app-*` 行与入口回调不复存在,
 *   应用维度唯一入口 = 主界面应用列表;
 * - 按类型危险清理行退役(清理并入各记录列表),仅保留「清空全部」(确认/取消路径);
 * - 存储占用 = 与生产同源计算值。
 */
@RunWith(AndroidJUnit4::class)
class DataSettingsScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun str(id: Int) = ctx.getString(id)

    private fun str(id: Int, vararg args: Any) = ctx.getString(id, *args)

    private fun entity(recordType: String, pkg: String = "pkg.test") =
        CryptoRecordEntity(
            recordType = recordType,
            packageName = pkg,
            kind = "DIGEST",
            operation = "DIGEST",
            algorithm = "MD5",
            threadName = "main",
        )

    /** 有界轮询等待文本出现(Room Flow 异步首帧/重组)。 */
    private fun waitForVisible(text: String) {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            rule.waitForIdle()
            if (rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()) return
            Thread.sleep(100)
        }
        rule.onAllNodesWithText(text).onFirst().assertIsDisplayed()
    }

    /** 滚动至可见(小屏折叠区)。 */
    private fun scrollTo(text: String) {
        rule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText(text))
    }

    private fun scrollToTag(tag: String) {
        rule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasTestTag(tag))
    }

    /** 计数文案值(zh="2 条" / en="2",资源驱动,与设备区域无关)。 */
    private fun countText(value: Long) = str(R.string.settings_total_records_value, value)

    private fun setContent(robot: Robot) = rule.setContent {
        MaterialTheme {
            DataSettingsScreen(
                viewModel = robot.vm,
                onBack = {},
                onOpenTypeRecords = robot::onOpenType,
            )
        }
    }

    /** 回调捕获替身(导航在单屏组合内不可用,断言入口接线)。 */
    private class Robot {
        lateinit var vm: DataSettingsViewModel
        val openedTypes = mutableListOf<RecordType>()

        fun onOpenType(type: RecordType) {
            openedTypes += type
        }
    }

    /** 类型计数可点回调 + 按应用浏览退役红线(V41 T5)。 */
    @Test
    fun typeCounts_clickableRows_andAppBrowseRetired() {
        val robot = Robot()
        val db = Room.inMemoryDatabaseBuilder(ctx, ModevDatabase::class.java).build()
        val dao = db.cryptoRecordDao()
        runBlocking {
            repeat(2) { dao.insert(entity(RecordTypes.CRYPTO)) }
            repeat(3) { dao.insert(entity(RecordTypes.DATABASE, pkg = "pkg.other")) }
        }
        robot.vm = DataSettingsViewModel(LogRepository(dao, ConfigServer()))
        setContent(robot)

        // 类型计数区:算法 2 / 数据库 3 行渲染 + 可点回调(全局列表入口)。
        waitForVisible(str(R.string.settings_records_algorithm))
        scrollTo(str(R.string.settings_records_algorithm))
        rule.onNodeWithText(str(R.string.settings_records_algorithm)).performClick()
        rule.waitForIdle()
        scrollTo(str(R.string.settings_records_database))
        rule.onNodeWithText(str(R.string.settings_records_database)).performClick()
        rule.waitForIdle()
        assertEquals(listOf(RecordType.CRYPTO, RecordType.DATABASE), robot.openedTypes)

        // V41 T5 红线:按应用浏览区退役——分区文案/应用行全库不复存在。
        scrollTo(str(R.string.settings_records_database))
        rule.onAllNodesWithTag("data-app-pkg.test", useUnmergedTree = true).assertCountEquals(0)
        rule.onAllNodesWithTag("data-app-pkg.other", useUnmergedTree = true).assertCountEquals(0)
        assertTrue(
            "按应用浏览应用行不得复活",
            rule.onAllNodesWithText("pkg.test").fetchSemanticsNodes().isEmpty(),
        )

        db.close()
    }

    /** V40 T4 红线:按类型危险清理行退役,清理并入列表;「清空全部」保留可用。 */
    @Test
    fun dangerRowsRetired_clearAllKept_withConfirmAndCancel() {
        val robot = Robot()
        val db = Room.inMemoryDatabaseBuilder(ctx, ModevDatabase::class.java).build()
        val dao = db.cryptoRecordDao()
        runBlocking {
            repeat(2) { dao.insert(entity(RecordTypes.CRYPTO)) }
        }
        robot.vm = DataSettingsViewModel(LogRepository(dao, ConfigServer()))
        setContent(robot)

        // 10 个按类型危险行(testTag clear-type-*)全库退役。
        rule.onAllNodesWithTag("clear-type-crypto", useUnmergedTree = true)
            .assertCountEquals(0)
        rule.onAllNodesWithTag("clear-type-database", useUnmergedTree = true)
            .assertCountEquals(0)

        // 「清空全部」保留:确认路径 → 库清空(计数归零)。
        scrollTo(str(R.string.settings_clear_all_records))
        rule.onNodeWithTag("clear-all").performClick()
        rule.onNodeWithText(str(R.string.danger_clear_all_msg)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.confirm)).performClick()
        waitForVisible(countText(0))

        // 取消路径:打开 → 取消 → 无清理(种子 1 条仍在)。
        runBlocking { dao.insert(entity(RecordTypes.CRYPTO, pkg = "pkg.keep")) }
        scrollTo(str(R.string.settings_clear_all_records))
        rule.onNodeWithTag("clear-all").performClick()
        rule.onNodeWithText(str(R.string.danger_clear_all_msg)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.cancel)).performClick()
        rule.waitForIdle()
        assertTrue(
            "取消后不得清理数据",
            rule.onAllNodesWithText(countText(1)).fetchSemanticsNodes().isNotEmpty(),
        )

        db.close()
    }

    /** 真实容器壳:存储占用值与生产同源(V23 契约保留)。 */
    @Test
    fun storageValue_sameSourceAsProduction() {
        val container = (ctx.applicationContext as ModevApp).container
        val robot = Robot()
        robot.vm = DataSettingsViewModelFactory(container).create(DataSettingsViewModel::class.java)
        // 预建 DB 文件防「Room 首查才建文件」竞态取 0 B。
        val dbPath = ctx.getDatabasePath(ModevDatabase.NAME)
        dbPath.parentFile?.mkdirs()
        ctx.openOrCreateDatabase(ModevDatabase.NAME, Context.MODE_PRIVATE, null).close()
        fun sizeOf(name: String): Long {
            val f = java.io.File(dbPath.parentFile, name)
            return if (f.exists()) f.length() else 0L
        }
        val expectedSize = StorageFormat.formatBytes(
            sizeOf(ModevDatabase.NAME) +
                sizeOf(ModevDatabase.NAME + "-wal") +
                sizeOf(ModevDatabase.NAME + "-shm"),
        )

        setContent(robot)

        waitForVisible(expectedSize)
        scrollTo(expectedSize)
        rule.onNodeWithText(expectedSize).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.settings_storage_db)).assertIsDisplayed()
    }
}
