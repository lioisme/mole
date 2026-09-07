package com.lioisme.modev

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lioisme.modev.domain.server.ConnectionMonitor
import com.lioisme.modev.domain.scope.ScopeState
import com.lioisme.modev.ui.settings.DiagnosticsScreen
import com.lioisme.modev.ui.settings.DiagnosticsViewModel
import com.lioisme.modev.ui.settings.DiagnosticsViewModelFactory
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 诊断页验收(V19 T8;V26 详情弹层;V39 服务/诊断拆分:服务配置断言迁 ServicesScreenTest):
 * - 三分区渲染(框架与作用域 / 通信服务[在线目标] / 管道统计)+ 框架态行(按 VM 实际态);
 * - 「复制诊断信息」剪贴板快照与页面行同源(参数驱动),Snackbar 反馈;
 * - V39:快照新增 MCP 两行(服务态 + 令牌认证态)。
 */
@RunWith(AndroidJUnit4::class)
class DiagnosticsScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun str(id: Int) = ctx.getString(id)

    private fun str(id: Int, vararg args: Any) = ctx.getString(id, *args)

    private lateinit var vm: DiagnosticsViewModel
    private lateinit var settings: com.lioisme.modev.data.repo.SettingsRepository

    private fun setContent() {
        val container = (ctx.applicationContext as ModevApp).container
        settings = container.settings
        runBlocking { settings.setServerExternalAccess(false) }
        vm = DiagnosticsViewModelFactory(container).create(DiagnosticsViewModel::class.java)
        // 等回环改绑落定(上一用例可能遗留 0.0.0.0 重启窗口),杜绝跨用例地址污染。
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline &&
            vm.listeningAddress.value != ConnectionMonitor.BIND_LOOPBACK
        ) {
            Thread.sleep(100)
        }
        rule.setContent { MaterialTheme { DiagnosticsScreen(vm, onBack = {}) } }
        rule.waitForIdle()
    }

    private fun scrollTo(text: String) {
        rule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText(text))
    }

    @Test
    fun diagnosticsSections_andRuntimeRows() {
        setContent()

        // V39 拆分:诊断页 = 框架与作用域 / 通信服务(在线目标)/ 管道统计;
        // 服务配置(外部访问/端口/MCP 开关/令牌)不再渲染。
        rule.onNodeWithText(str(R.string.settings_framework_section)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.settings_server_section)).assertIsDisplayed()
        assertTrue(
            "MCP 分区已迁服务页,诊断页不得渲染",
            rule.onAllNodesWithText(str(R.string.mcp_section)).fetchSemanticsNodes().isEmpty(),
        )
        assertTrue(
            "外部访问开关已迁服务页,诊断页不得渲染",
            rule.onAllNodesWithTag("services-wire-external").fetchSemanticsNodes().isEmpty(),
        )
        scrollTo(str(R.string.settings_stats_section))
        rule.onNodeWithText(str(R.string.settings_stats_section)).assertIsDisplayed()

        // 框架态行 + 在线目标行(存在性断言,分区滚动后不要求同屏)。
        assertTrue(
            "框架行缺失",
            rule.onAllNodesWithText(frameworkValue(vm)).fetchSemanticsNodes().isNotEmpty(),
        )
        assertTrue(
            "在线目标行缺失",
            rule.onAllNodesWithText(str(R.string.settings_online_targets_value, vm.connectedCount.value))
                .fetchSemanticsNodes().isNotEmpty(),
        )
    }

    /**
     * V40 T1:管道统计恒展开——零交互即可见全部 7 行统计,
     * 折叠机制(StatsToggleRow/AnimatedVisibility)已退役,不存在展开态前置。
     */
    @Test
    fun pipelineStats_alwaysExpanded_noInteraction() {
        setContent()

        val statLabels =
            listOf(
                R.string.stats_received,
                R.string.stats_dropped,
                R.string.stats_write_failures,
                R.string.stats_trimmed,
                R.string.stats_rejected_hellos,
                R.string.stats_dropped_records,
                R.string.stats_rejected_records,
            )
        scrollTo(str(R.string.stats_received))
        rule.onNodeWithText(str(R.string.settings_stats_section)).assertIsDisplayed()
        statLabels.forEach { label ->
            assertTrue(
                "统计行缺失(恒展开契约):${ctx.getString(label)}",
                rule.onAllNodesWithText(str(label)).fetchSemanticsNodes().isNotEmpty(),
            )
        }
    }

    /**
     * 剪贴板功能探测(雷电 x64 镜像实测静默丢弃 primaryClip,hasClip 恒 false):
     * 哨兵写读不通的环境降级为「Snackbar 证处理器执行」,内容同源断言由
     * DiagnosticsReport JVM 单测 + MCP diagnostics 工具集成矩阵双端覆盖。
     */
    private fun clipboardFunctional(): Boolean {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        return runCatching {
            cm.setPrimaryClip(android.content.ClipData.newPlainText("probe", "modev-clip-probe"))
            cm.primaryClip?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)?.coerceToText(ctx)?.toString() == "modev-clip-probe"
        }.getOrDefault(false)
    }

    /** 清空剪贴板(哨兵残留会污染「读新值」轮询;清空失败环境同样降级跳过)。 */
    private fun clearClipboard() {
        runCatching {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("", ""))
        }
    }

    @Test
    fun copyDiagnostics_writesClipboard_parameterDriven() {
        setContent()

        if (!clipboardFunctional()) {
            scrollTo(str(R.string.diagnostics_copy))
            rule.onNodeWithText(str(R.string.diagnostics_copy)).performClick()
            waitForVisible(str(R.string.diagnostics_copied))
            return
        }
        // 功能探测通过 → 清哨兵,点复制,轮询等待「非探测残留」的新内容落板。
        clearClipboard()

        scrollTo(str(R.string.diagnostics_copy))
        rule.onNodeWithText(str(R.string.diagnostics_copy)).performClick()

        // Snackbar 先证点击处理已执行(剪贴板写入与反馈同一处理链)。
        waitForVisible(str(R.string.diagnostics_copied))

        // 剪贴板快照:与页面行同源(参数驱动的端口行/统计标签/框架行)。
        // 有界轮询等待新内容覆盖(雷电镜像写入间歇性丢弃,AGENTS 已记录)。
        var text = ""
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            text = readClipboard()
            val expectedPort =
                str(
                    R.string.settings_port_value,
                    vm.listeningAddress.value,
                    vm.port.value,
                ) + " " + str(R.string.settings_port_scope_local)
            if (text.contains(expectedPort)) break
            Thread.sleep(200)
        }
        val expectedPort =
            str(
                R.string.settings_port_value,
                vm.listeningAddress.value,
                vm.port.value,
            ) + " " + str(R.string.settings_port_scope_local)
        assertTrue("端口行缺失:\n$text", text.contains(expectedPort))
        assertTrue("统计标签缺失:\n$text", text.contains(str(R.string.stats_received)))
        assertTrue("框架行缺失:\n$text", text.contains(frameworkValue(vm)))
    }

    /** 复位外部访问偏好(跨用例污染防线;服务端回绑回环)。 */
    @After
    fun tearDown() {
        if (this::settings.isInitialized) {
            runBlocking { settings.setServerExternalAccess(false) }
        }
    }

    private fun frameworkValue(vm: DiagnosticsViewModel): String = when (val f = vm.frameworkState.value) {
        is ScopeState.Connecting -> str(R.string.settings_connecting)
        is ScopeState.Ready -> "${f.frameworkName} ${f.frameworkVersion}"
        is ScopeState.Unavailable -> str(R.string.settings_not_connected)
    }

    /** 有界轮询等待文本出现(Snackbar 超时窗口内)。 */
    private fun waitForVisible(text: String) {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            rule.waitForIdle()
            if (rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()) return
            Thread.sleep(100)
        }
        rule.onNodeWithText(text).assertIsDisplayed()
    }

    /** 读系统剪贴板(Compose ClipboardManager 同源;有界轮询等写入)。 */
    private fun readClipboard(): String {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            val clip = cm.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).coerceToText(ctx)?.toString()
                if (!text.isNullOrEmpty()) return text
            }
            Thread.sleep(100)
        }
        return cm.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString() ?: ""
    }
}
