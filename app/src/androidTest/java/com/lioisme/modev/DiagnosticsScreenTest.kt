package com.lioisme.modev

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 服务与诊断页验收(V19 T8;V26 外部访问开关 + 端口行参数化;V38 MCP 分区增量):
 * - 四分区渲染(框架与作用域 / 通信服务 / MCP 服务 / 管道统计)+ 框架态行(按 VM 实际态);
 * - 端口行 = 端口号(V30 起收敛;监听地址与作用域后缀经剪贴板报告承载);
 * - 外部访问开关默认停用;切换即改状态并驱动服务端改绑(@After 复位停用);
 * - 「复制诊断信息」剪贴板快照与页面行同源(参数驱动),Snackbar 反馈。
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

    /** 端口行期望值(V30 契约:行值 = 端口号)。 */
    private fun portRowValue(): String = vm.port.value.toString()

    private fun scrollTo(text: String) {
        rule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText(text))
    }

    @Test
    fun threeSections_andServiceRows() {
        setContent()

        // 四分区:框架与作用域 / 通信服务 / MCP 服务 / 管道统计(统计折叠区滚动可达)。
        rule.onNodeWithText(str(R.string.settings_framework_section)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.settings_server_section)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.mcp_section)).assertIsDisplayed()
        scrollTo(str(R.string.settings_stats_section))
        rule.onNodeWithText(str(R.string.settings_stats_section)).assertIsDisplayed()

        // 框架态行 + 通信服务行(端口号 / 在线目标;存在性断言,分区滚动后不要求同屏)。
        assertTrue(
            "框架行缺失",
            rule.onAllNodesWithText(frameworkValue(vm)).fetchSemanticsNodes().isNotEmpty(),
        )
        assertTrue(
            "端口行缺失",
            rule.onAllNodesWithText(portRowValue()).fetchSemanticsNodes().isNotEmpty(),
        )
        assertTrue(
            "在线目标行缺失",
            rule.onAllNodesWithText(str(R.string.settings_online_targets_value, vm.connectedCount.value))
                .fetchSemanticsNodes().isNotEmpty(),
        )
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

    @Test
    fun externalAccessSwitch_defaultOff_toggleOnRebindsAllInterfaces() {
        setContent()

        // 默认停用:开关 Off(V38 起页面有两个「外部访问」语义行,锚定走 testTag)。
        rule.onNodeWithTag("diagnostics-bind-all").performScrollTo().assertIsOff()

        // 开启 → 状态翻转 + 服务端改绑全接口(@After 复位)。
        rule.onNodeWithTag("diagnostics-bind-all").performClick()
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline && !vm.externalAccess.value) {
            Thread.sleep(100)
            rule.waitForIdle()
        }
        assertEquals(true, vm.externalAccess.value)
        rule.onNodeWithTag("diagnostics-bind-all").assertIsOn()

        // 等待服务端完成改绑(VM 地址观测点轮询)。
        var rebound = false
        while (System.currentTimeMillis() < deadline && !rebound) {
            rule.waitForIdle()
            rebound = vm.listeningAddress.value == ConnectionMonitor.BIND_ALL_INTERFACES
            if (!rebound) Thread.sleep(100)
        }
        assertEquals(ConnectionMonitor.BIND_ALL_INTERFACES, vm.listeningAddress.value)
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
