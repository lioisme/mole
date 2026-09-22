package com.lioisme.modev

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
 * 详情页 V28 纯 markdown 化验收(取代 V24 Hero/锚点行契约):
 * - 身份区(markdown H2):算法名双处出现(顶栏 + identity 文档),原生 Hero 卡退役;
 * - 目录(TOC 导航行):按内容动态生成(数据流/密钥材料/调用栈),
 *   点击「密钥材料」平滑滚动后目标分区可见;
 * - 两列元数据 GFM 表:上下文标签存在(恒有),provider 缺失行不渲染。
 */
@RunWith(AndroidJUnit4::class)
class PureMarkdownIdentityTocTest {

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
            val seeded = dao.countByQuery(RecordQueryFactory.count(RecordFilter(packages = setOf(ownPackage)))).first()
            assertEquals("种子写入须在读回中可见", 1L, seeded)
        }
        AppListSettings.setShowModules(true)
        rule.waitForIdle()
    }

    @After
    fun restore() {
        AppListSettings.setShowModules(false)
    }

    /** 等待详情页出现(V28 哨兵 = 算法名双处:顶栏 + 身份区 markdown H2)。 */
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
        rule.awaitAnchor("monitor-entry-algorithm", "点应用行应进监控页")
        // 监控页是整屏 verticalScroll Column:Hero/统计区之下的入口行常落在视口外,
        // 直接 performClick 的坐标在窗外被静默丢弃(DEF-V45-009 根因,V40 T4 同坑)。
        rule.clickScrolledIntoView(hasText(str(R.string.algorithm_records_title)))
        rule.awaitAnchor("records-filter", "点「算法记录」入口行应进算法记录列表")
        rule.onAllNodes(hasScrollAction()).onFirst()
            .performScrollToNode(hasText(cipherAlgorithm))
        rule.onNodeWithText(cipherAlgorithm).performClick()

        // 详情页哨兵 = detail-more 锚点已到位;算法名唯一承载处 = 身份区 H2
        // (V29 起顶栏标题为静态「记录详情」,V28「双处」口径作废)。轮询等 markdown 装配。
        rule.awaitAnchor("detail-more", "点记录行应进详情页")
        val detailDeadline = System.currentTimeMillis() + 10_000
        var opened = false
        while (System.currentTimeMillis() < detailDeadline && !opened) {
            rule.waitForIdle()
            opened = rule.onAllNodesWithText(cipherAlgorithm).fetchSemanticsNodes().isNotEmpty()
            if (!opened) Thread.sleep(100)
        }
        assertTrue("身份区应渲染算法名", opened)
    }

    @Test
    fun identity_andToc_render_withMetaTable() {
        openCipherDetail()

        // 身份区:类别徽章文案(资源驱动)+ 算法名双处(顶栏 + identity)。
        assertTrue(
            "身份区应含类别徽章文案",
            rule.onAllNodesWithText(str(R.string.kind_cipher)).fetchSemanticsNodes().isNotEmpty(),
        )
        rule.onAllNodesWithText(cipherAlgorithm).onFirst().assertIsDisplayed()

        // 目录导航行:数据流 / 密钥材料 / 调用栈 三条目按内容生成(TOC 与节标题同文案,取 onFirst)。
        listOf(R.string.section_zone_data_flow, R.string.section_zone_key_material, R.string.section_stack)
            .forEach { res ->
                assertTrue(
                    "目录应含:" + str(res),
                    rule.onAllNodesWithText(str(res)).fetchSemanticsNodes().isNotEmpty(),
                )
            }

        // 元数据 GFM 表:上下文恒有;provider 未种 → 不渲染。
        assertTrue(
            "元数据表应含上下文标签",
            rule.onAllNodesWithText(str(R.string.meta_context)).fetchSemanticsNodes().isNotEmpty(),
        )
        assertEquals(
            0,
            rule.onAllNodesWithText(str(R.string.meta_provider)).fetchSemanticsNodes().size,
        )
    }

    @Test
    fun tocEntry_click_scrollsToKeyMaterialSection() {
        openCipherDetail()

        // 点击「密钥材料」TOC 行 → 平滑滚动完成后分区可见(轮询等待动画结束;
        // TOC 行与分区 H2 同文案,onFirst 取先组合的目录行)。
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
        assertTrue("点击目录后密钥材料分区应可见", visible)

        // 密钥 hex 内容在组合中(滚动动画完成后密钥区进入组合窗口,轮询等待)。
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
}
