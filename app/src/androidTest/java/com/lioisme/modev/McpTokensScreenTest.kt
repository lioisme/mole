package com.lioisme.modev

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lioisme.modev.core.model.McpTokenEntry
import com.lioisme.modev.domain.server.McpServiceConfig
import com.lioisme.modev.ui.settings.McpTokensScreen
import com.lioisme.modev.ui.settings.McpTokensViewModel
import com.lioisme.modev.ui.settings.McpTokensViewModelFactory
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 令牌页菜单化验收(V46 R3/D2/D3/D4):
 *  - 行内零动作按钮(复制/显隐按钮整体退役),动作唯一入口 = 「更多」按钮;
 *  - 菜单三项锚点齐备(查看 / 重命名 / 删除);
 *  - 查看 = 只读值对话框,明文只在此上屏,复制后关窗;
 *  - 重命名经底部 sheet 原地生效(值与顺序零变化 = 不重启不踢会话);
 *  - 删除走居中危险确认,确认即移出条目。
 *
 * 宿主形态 = 直挂 [McpTokensScreen](与 `ServicesScreenTest` 同),装配一律经
 * `ModevApp.container`(禁为测试开旁路);类型持 `domain` 契约 `McpServiceConfig`
 * (AGENTS #18 G2 同口径)。锚点等待走同包 [awaitAnchor] —— Dialog/Sheet 是新窗口,
 * 单次 waitForIdle 不保证首帧已组合。
 */
@RunWith(AndroidJUnit4::class)
class McpTokensScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun str(id: Int) = ctx.getString(id)

    private val container get() = (ctx.applicationContext as ModevApp).container
    private val mcp: McpServiceConfig get() = container.mcpConfig

    private lateinit var seeded: McpTokenEntry

    @Before
    fun seedAndHost() {
        // 每例自建一枚 label 为空的令牌:headline 走 token_unnamed 回落形态,
        // 重命名用例因此无需清文本(本仓测试库无 performTextClearance 先例)。
        seeded = runBlocking { mcp.addMcpToken("") }
        val vm = McpTokensViewModelFactory(container).create(McpTokensViewModel::class.java)
        rule.setContent {
            MaterialTheme { McpTokensScreen(viewModel = vm, onBack = {}) }
        }
        rule.waitForIdle()
    }

    @After
    fun tearDown() {
        // 令牌值是宿主本地秘密,测后清场;认证复位默认停用,不污染后续用例。
        runBlocking {
            mcp.mcpTokens.value.forEach { mcp.revokeMcpToken(it.id) }
            mcp.setMcpTokenAuth(false)
        }
    }

    private fun openMenu() {
        // ⋮ 按钮可能落在视口外(列表可滚):视口外 performClick 被静默丢弃(V45 实锤),
        // 故先滚进视口再点。
        rule.clickScrolledIntoView(hasTestTag("token-more-${seeded.id}"))
        rule.waitForIdle()
    }

    @Test
    fun rowHasNoInlineActions_onlyMoreButton_andValueStaysMasked() {
        assertTrue(
            "更多按钮必须存在",
            rule.onAllNodesWithTag("token-more-${seeded.id}").fetchSemanticsNodes().isNotEmpty(),
        )
        rule.onAllNodesWithTag("token-copy-${seeded.id}").assertCountEquals(0)
        rule.onAllNodesWithTag("token-revoke-${seeded.id}").assertCountEquals(0)
        // D3:明文恒不上行(只进查看对话框);断言掩码点阵存在而非重算掩码公式。
        rule.onAllNodesWithText(seeded.value).assertCountEquals(0)
        assertTrue(
            "行内应为掩码形态",
            rule.onAllNodesWithText("••••••", substring = true).fetchSemanticsNodes().isNotEmpty(),
        )
    }

    @Test
    fun moreMenu_hasViewRenameDelete() {
        openMenu()
        rule.onNodeWithTag("token-menu-view").assertIsDisplayed()
        rule.onNodeWithTag("token-menu-rename").assertIsDisplayed()
        rule.onNodeWithTag("token-menu-delete").assertIsDisplayed()
    }

    @Test
    fun view_opensDialogWithPlaintext_andCopyClosesIt() {
        openMenu()
        rule.onNodeWithTag("token-menu-view").performClick()
        rule.awaitAnchor("token-view-value", "查看应打开只读值对话框")
        rule.onNodeWithText(str(R.string.token_value_title)).assertIsDisplayed()
        assertTrue(
            "对话框内必须是完整明文",
            rule.onAllNodesWithText(seeded.value).fetchSemanticsNodes().isNotEmpty(),
        )
        rule.onNodeWithTag("token-view-copy").performClick()
        rule.waitForIdle()
        // 复制即关窗:明文不常驻(D3)
        rule.onAllNodesWithTag("token-view-value").assertCountEquals(0)
        val stored = mcp.mcpTokens.value.first { it.id == seeded.id }
        assertEquals("复制不得改动条目", seeded.value, stored.value)
    }

    @Test
    fun rename_updatesLabelInPlace_withoutTouchingValue() {
        openMenu()
        rule.onNodeWithTag("token-menu-rename").performClick()
        rule.awaitAnchor("token-rename-input", "重命名应打开输入 sheet")
        rule.onNodeWithTag("token-rename-input").performTextInput("renamed-by-test")
        rule.onNodeWithTag("token-rename-confirm").performClick()
        rule.waitForIdle()
        val stored = mcp.mcpTokens.value.first { it.id == seeded.id }
        assertEquals("renamed-by-test", stored.label)
        assertEquals("值不变 = 校验集内容不变(不重启不踢会话)", seeded.value, stored.value)
        assertTrue(
            "新名应回写到行 headline",
            rule.onAllNodesWithText("renamed-by-test").fetchSemanticsNodes().isNotEmpty(),
        )
    }

    @Test
    fun delete_confirmsAndRemovesEntry() {
        openMenu()
        rule.onNodeWithTag("token-menu-delete").performClick()
        rule.awaitAnchor("token-delete-confirm", "删除应弹居中危险确认")
        rule.onNodeWithTag("token-delete-confirm").performClick()
        rule.waitForIdle()
        assertTrue(
            "条目应已移除",
            mcp.mcpTokens.value.none { it.id == seeded.id },
        )
        rule.onAllNodesWithTag("token-more-${seeded.id}").assertCountEquals(0)
    }
}
