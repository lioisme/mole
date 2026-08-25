package com.lioisme.modev

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
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
 * 详情页 V24 重设计验收(G3):
 * - Hero 身份卡(detail-hero):类别徽章(kind 标签)+ 算法名双处出现(顶栏+hero);
 * - 锚点芯片行(detail-anchors):按内容动态生成(数据流/密钥材料/调用栈),
 *   点击「密钥材料」平滑滚动后目标分区可见;
 * - 两列元数据网格:上下文标签存在(恒有),provider 缺失行不渲染;
 * - 引擎红线不回归:三态分段(Hex 段)与流向箭头(cd_flow 唯一)仍在。
 */
@RunWith(AndroidJUnit4::class)
class DetailHeroAnchorsTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val ownPackage = "com.lioisme.modev"

    private fun str(id: Int) = ctx.getString(id)

    private val cipherAlgorithm = "AES/CBC/PKCS5Padding"

    @Before
    fun seed() {
        val dao = ModevDatabase.get(ctx).cryptoRecordDao()
        runBlocking {
            dao.clearPackage(ownPackage)
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
                        stackJson = "[\"com.victim.app.CryptoBox.encrypt(CryptoBox.kt:42)\"]",
                        threadName = "hero-test",
                        timestamp = System.currentTimeMillis() - 10_000,
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
            assertEquals("种子写入须在读回中可见", 1L, seeded)
        }
        AppListSettings.setShowModules(true)
        rule.waitForIdle()
    }

    @After
    fun restore() {
        AppListSettings.setShowModules(false)
    }

    private fun openCipherDetail() {
        val deadline = System.currentTimeMillis() + 10_000
        var rowFound = false
        while (System.currentTimeMillis() < deadline && !rowFound) {
            rule.waitForIdle()
            val scrollables = rule.onAllNodes(hasScrollAction()).fetchSemanticsNodes()
            if (scrollables.isNotEmpty()) {
                try {
                    rule.onAllNodes(hasScrollAction()).onFirst()
                        .performScrollToNode(hasText(ownPackage))
                    rowFound = true
                } catch (_: AssertionError) {
                    Thread.sleep(100)
                }
            } else {
                Thread.sleep(100)
            }
        }
        assertTrue("被测包行应出现", rowFound)
        rule.onNodeWithText(ownPackage).performClick()
        rule.waitForIdle()
        rule.onNodeWithText(str(R.string.algorithm_records_title)).performClick()
        rule.waitForIdle()
        rule.onAllNodes(hasScrollAction()).onFirst()
            .performScrollToNode(hasText(cipherAlgorithm))
        rule.onNodeWithText(cipherAlgorithm).performClick()
        rule.waitForIdle()
        // 等待详情页出现(detail-hero = V24 详情页标志)。
        val detailDeadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < detailDeadline) {
            rule.waitForIdle()
            if (rule.onAllNodesWithTag("detail-hero").fetchSemanticsNodes().isNotEmpty()) return
            Thread.sleep(100)
        }
        assertTrue("详情页应打开", false)
    }

    @Test
    fun heroCard_andAnchorRow_render_withKindBadgeAndMetaGrid() {
        openCipherDetail()

        // Hero:类别徽章文本(资源驱动)+ 算法名双处(顶栏 + hero)。
        rule.onNodeWithTag("detail-hero").assertIsDisplayed()
        assertTrue(
            "Hero 应含类别徽章文案",
            rule.onAllNodesWithText(str(R.string.kind_cipher)).fetchSemanticsNodes().isNotEmpty(),
        )
        rule.onAllNodesWithText(cipherAlgorithm).onFirst().assertIsDisplayed()

        // 锚点行:数据流 / 密钥材料 / 调用栈 三锚点按内容生成。
        val anchorsTag = onNodeWithTagSafe("detail-anchors")
        anchorsTag.assertIsDisplayed()
        listOf(R.string.section_zone_data_flow, R.string.section_zone_key_material, R.string.section_stack)
            .forEach { res ->
                assertTrue(
                    "锚点应含:" + str(res),
                    rule.onAllNodesWithText(str(res)).fetchSemanticsNodes().isNotEmpty(),
                )
            }

        // 元数据网格:上下文恒有;provider 未种 → 不渲染。
        assertTrue(
            "元数据网格应含上下文标签",
            rule.onAllNodesWithText(str(R.string.meta_context)).fetchSemanticsNodes().isNotEmpty(),
        )
        assertEquals(
            0,
            rule.onAllNodesWithText(str(R.string.meta_provider)).fetchSemanticsNodes().size,
        )
    }

    @Test
    fun anchorChip_click_scrollsToKeyMaterialZone() {
        openCipherDetail()

        // 点击「密钥材料」锚点 → 平滑滚动完成后分区头可见(轮询等待动画结束;
        // 芯片与分区头同文案,取 onFirst 多节点安全断言)。
        rule.onAllNodesWithText(str(R.string.section_zone_key_material)).onFirst().performClick()
        val deadline = System.currentTimeMillis() + 10_000
        var visible = false
        while (System.currentTimeMillis() < deadline && !visible) {
            rule.waitForIdle()
            try {
                rule.onAllNodesWithText(str(R.string.section_zone_key_material)).onFirst().assertIsDisplayed()
                visible = true
            } catch (_: AssertionError) {
                Thread.sleep(100)
            }
        }
        assertTrue("点击锚点后密钥材料分区应可见", visible)

        // 引擎红线(V26 markdown 化):密钥节为单表示(hex)代码卡 —— 无标签页,
        // 密钥 hex 内容在组合中(锚点滚动动画完成后密钥区进入组合窗口,轮询等待)。
        val hexDeadline = System.currentTimeMillis() + 10_000
        var hexFound = false
        while (System.currentTimeMillis() < hexDeadline && !hexFound) {
            rule.waitForIdle()
            hexFound = rule.onAllNodesWithText("0102030405060708090a0b0c0d0e0f10").fetchSemanticsNodes().isNotEmpty()
            if (!hexFound) Thread.sleep(100)
        }
        assertTrue(
            "密钥 hex 内容应在组合中",
            hexFound,
        )
    }

    /** 屏外节点容错定位:先滚到锚点行再取节点。 */
    private fun onNodeWithTagSafe(tag: String) =
        rule.onAllNodes(hasScrollAction()).onFirst()
            .performScrollToNode(hasTestTag(tag))
            .let { rule.onNodeWithTag(tag) }
}
