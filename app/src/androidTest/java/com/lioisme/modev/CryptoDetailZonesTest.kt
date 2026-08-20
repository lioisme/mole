package com.lioisme.modev

import android.view.KeyEvent
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
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
 * 算法详情页数据关系分区验收(V20 R4):
 * - AES 加密记录(方向=加密,key + iv + input + output):
 *   数据流分区(分区头 + 输入(明文) → 流向箭头 → 输出(密文))+
 *   密钥材料分区(分区头)+ 元数据算法行;
 * - 密钥生成记录(仅 output):无数据流分区(无输入),输出角色 = 输出(密钥);
 * - 分区头/角色标签文案经 ctx.getString 与资源同源(设备 locale 自适应)。
 * 前置:清空被测包记录 + 开启「显示 Xposed 模块」,@After 复位。
 */
@RunWith(AndroidJUnit4::class)
class CryptoDetailZonesTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val ownPackage = "com.lioisme.modev"

    private fun str(id: Int) = ctx.getString(id)

    private val cipherAlgorithm = "AES/CBC/PKCS5Padding"
    private val generateAlgorithm = "AES"

    @Before
    fun seed() {
        val dao = ModevDatabase.get(ctx).cryptoRecordDao()
        runBlocking {
            dao.clearPackage(ownPackage)
            val now = System.currentTimeMillis()
            dao.insertAll(
                listOf(
                    CryptoRecordEntity(
                        packageName = ownPackage,
                        kind = "CIPHER",
                        operation = "ENCRYPT",
                        algorithm = cipherAlgorithm,
                        direction = 1,
                        keyHex = "0102030405060708090a0b0c0d0e0f10",
                        keyLength = 16,
                        ivHex = "000102030405060708090a0b0c0d0e0f",
                        ivLength = 16,
                        inputHex = "68656c6c6f",
                        inputText = "hello",
                        inputLength = 5,
                        outputHex = "1b7d0e5f6c9a2b48",
                        outputLength = 8,
                        threadName = "zones-test",
                        timestamp = now - 10_000,
                    ),
                    CryptoRecordEntity(
                        packageName = ownPackage,
                        kind = "KEY_GENERATION",
                        operation = "GENERATE",
                        algorithm = generateAlgorithm,
                        outputHex = "2f4d88a0c9b1e6f3a2d5b7c1e9f0a4d8",
                        outputLength = 16,
                        threadName = "zones-test",
                        timestamp = now - 5_000,
                    ),
                ),
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
            assertEquals("种子写入须在读回中可见", 2L, seeded)
        }
        AppListSettings.setOnlyScoped(false, ctx)
        AppListSettings.setShowModules(true, ctx)
        rule.waitForIdle()
    }

    @After
    fun restore() {
        AppListSettings.setShowModules(false, ctx)
        AppListSettings.setOnlyScoped(false, ctx)
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
        rule.onNodeWithText(str(R.string.algorithm_records_title)).performClick()
        rule.waitForIdle()
    }

    /** 等待详情页出现(顶栏更多按钮 = 详情页标志)。 */
    private fun waitForDetail() {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            rule.waitForIdle()
            if (rule.onAllNodesWithContentDescription(str(R.string.record_more)).fetchSemanticsNodes().isNotEmpty()) return
            Thread.sleep(100)
        }
        assertTrue("详情页应打开", false)
    }

    @Test
    fun cipherDetail_showsDataFlowZone_withRolesAndArrow_andKeyMaterialZone() {
        rule.waitForIdle()
        openAlgorithmList()

        rule.onAllNodes(hasScrollAction()).onFirst()
            .performScrollToNode(hasText(cipherAlgorithm))
        rule.onNodeWithText(cipherAlgorithm).performClick()
        rule.waitForIdle()
        waitForDetail()

        // 元数据算法行(算法 = 顶栏标题 + 元数据行双处,断言标签存在即可)。
        rule.onNodeWithText(str(R.string.meta_algorithm)).assertIsDisplayed()

        // 数据流分区:分区头 + 输入(明文) + 流向箭头 + 输出(密文)。
        rule.onAllNodes(hasScrollAction()).onFirst()
            .performScrollToNode(hasText(str(R.string.section_zone_data_flow)))
        rule.onNodeWithText(str(R.string.section_zone_data_flow)).assertIsDisplayed()
        rule.onAllNodes(hasScrollAction()).onFirst()
            .performScrollToNode(hasText(str(R.string.role_input_plaintext)))
        rule.onNodeWithText(str(R.string.role_input_plaintext)).assertIsDisplayed()
        rule.onAllNodes(hasScrollAction()).onFirst()
            .performScrollToNode(hasText(str(R.string.role_output_ciphertext)))
        rule.onNodeWithText(str(R.string.role_output_ciphertext)).assertIsDisplayed()
        rule.onAllNodesWithContentDescription(str(R.string.cd_flow)).assertCountEquals(1)

        // 密钥材料分区:分区头 + 密钥节标题。
        rule.onAllNodes(hasScrollAction()).onFirst()
            .performScrollToNode(hasText(str(R.string.section_zone_key_material)))
        rule.onNodeWithText(str(R.string.section_zone_key_material)).assertIsDisplayed()
        rule.onAllNodes(hasScrollAction()).onFirst()
            .performScrollToNode(hasText(str(R.string.section_key)))
        rule.onNodeWithText(str(R.string.section_key)).assertIsDisplayed()

        // 输入/输出字节区块仍在(三态分段契约未破坏;多区块并存,取首节点)。
        rule.onAllNodes(hasScrollAction()).onFirst()
            .performScrollToNode(hasText(str(R.string.section_repr_hex)))
        rule.onAllNodesWithText(str(R.string.section_repr_hex)).onFirst().assertIsDisplayed()
    }

    @Test
    fun generateDetail_hasNoDataFlowZone_butKeyOutputRole() {
        rule.waitForIdle()
        openAlgorithmList()

        // 仅 output 的密钥生成记录:算法名精确匹配 AES(密钥生成行)。
        rule.onAllNodes(hasScrollAction()).onFirst()
            .performScrollToNode(hasText(generateAlgorithm))
        rule.onNodeWithText(generateAlgorithm).performClick()
        rule.waitForIdle()
        waitForDetail()

        // 无数据流分区(无输入),但输出区块带「输出(密钥)」角色标签。
        rule.onNodeWithText(str(R.string.section_zone_data_flow)).assertDoesNotExist()
        rule.onNodeWithText(str(R.string.role_output_key)).assertIsDisplayed()
        // 无密钥材料分区(该记录无 key/iv 等)。
        rule.onNodeWithText(str(R.string.section_zone_key_material)).assertDoesNotExist()
        // 记录行仍有锚点(详情页顶栏标题 = 算法名)。
        rule.onAllNodesWithText(generateAlgorithm).assertCountEquals(2)
    }
}