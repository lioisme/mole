package com.lioisme.modev

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.printToString
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 数据管理页验收(V19 T7 扩展):
 * - 分类计数(算法/数据库)与全库总数渲染(内存库端到端,种子数据确定性);
 * - 按类型清理:危险 AlertDialog 确认 → 仅清目标类型;
 * - 存储占用:数据库文件大小 = 与生产同源的计算值;
 * - 危险对话框取消路径(真实容器,非破坏)。
 */
@RunWith(AndroidJUnit4::class)
class DataSettingsScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun str(id: Int) = ctx.getString(id)

    private fun str(id: Int, vararg args: Any) = ctx.getString(id, *args)

    private fun entity(recordType: String) =
        CryptoRecordEntity(
            recordType = recordType,
            packageName = "pkg.test",
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
        val tree = rule.onRoot(useUnmergedTree = true).printToString()
        throw AssertionError("未等到文本 '$text'。树:\n$tree")
    }

    /** 滚动至可见(小屏折叠区)。 */
    private fun scrollTo(text: String) {
        try {
            rule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText(text))
        } catch (e: AssertionError) {
            val tree = rule.onRoot(useUnmergedTree = true).printToString()
            throw AssertionError("滚动目标 '$text' 未找到。树:\n$tree", e)
        }
    }

    /** 计数文案值(zh="2 条" / en="2",资源驱动,与设备区域无关)。 */
    private fun countText(value: Long) = str(R.string.settings_total_records_value, value)

    /** 内存库端到端:计数渲染 + 按类型清理确认(破坏性验证,不触碰真库)。 */
    @Test
    fun typeCounts_andClearByTypeConfirm() {
        val db = Room.inMemoryDatabaseBuilder(ctx, ModevDatabase::class.java).build()
        val dao = db.cryptoRecordDao()
        runBlocking {
            repeat(2) { dao.insert(entity(RecordTypes.CRYPTO)) }
            repeat(3) { dao.insert(entity(RecordTypes.DATABASE)) }
        }
        val vm = DataSettingsViewModel(LogRepository(dao, ConfigServer()))

        rule.setContent { MaterialTheme { DataSettingsScreen(vm, onBack = {}) } }

        // 分类计数:算法 2 / 数据库 3(值进 ListItem 合并节点文本,行标签即锚点)。
        rule.onNodeWithText(str(R.string.settings_records_algorithm)).assertIsDisplayed()
        waitForVisible(countText(2))
        rule.onAllNodesWithText(countText(3)).assertCountEquals(1)

        // 按类型清理:算法记录 → 危险确认 → 仅算法清空(0/3;清后总数 3 与数据库行同值)。
        rule.onNodeWithText(str(R.string.settings_clear_algorithm_records)).performClick()
        rule.onNodeWithText(str(R.string.danger_clear_algorithm_msg)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.confirm)).performClick()
        waitForVisible(countText(0))
        rule.onAllNodesWithText(str(R.string.danger_clear_algorithm_msg)).assertCountEquals(0)
        rule.onAllNodesWithText(countText(3)).assertCountEquals(2)

        db.close()
    }

    /** 真实容器壳:标签齐全 + 存储占用值与生产同源 + 危险对话框取消路径(非破坏)。 */
    @Test
    fun storageValue_andDangerCancelPath() {
        val container = (ctx.applicationContext as ModevApp).container
        val vm = DataSettingsViewModelFactory(container).create(DataSettingsViewModel::class.java)
        // 预建 DB 文件:Room 首查才建文件,remember 快照会取到 0 B;
        // 先落盘(4096 B 头页,只读查询不增长)再断言,生产渲染值与测试值同源。
        val dbPath = ctx.getDatabasePath(ModevDatabase.NAME)
        dbPath.parentFile?.mkdirs()
        ctx.openOrCreateDatabase(ModevDatabase.NAME, Context.MODE_PRIVATE, null).close()
        val expectedSize = StorageFormat.formatBytes(dbPath.length().toLong())

        rule.setContent { MaterialTheme { DataSettingsScreen(vm, onBack = {}) } }

        rule.onNodeWithText(str(R.string.settings_records_algorithm)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.settings_records_database)).assertIsDisplayed()
        // 存储占用 = 生产同源计算(DB 文件大小格式化);小屏折叠区先滚动。
        scrollTo(expectedSize)
        rule.onNodeWithText(expectedSize).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.settings_storage_db)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.settings_total_records)).assertIsDisplayed()

        // 危险对话框:打开 → 取消 → 关闭(不清理任何数据)。
        scrollTo(str(R.string.settings_clear_all_records))
        rule.onNodeWithText(str(R.string.settings_clear_all_records)).performClick()
        rule.onNodeWithText(str(R.string.danger_clear_all_msg)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.cancel)).performClick()
        rule.waitForIdle()
        rule.onAllNodesWithText(str(R.string.danger_clear_all_msg)).assertCountEquals(0)
    }
}