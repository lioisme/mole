package com.lioisme.modev

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lioisme.modev.domain.scope.ScopeState
import com.lioisme.modev.ui.settings.DiagnosticsScreen
import com.lioisme.modev.ui.settings.DiagnosticsViewModel
import com.lioisme.modev.ui.settings.DiagnosticsViewModelFactory
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 服务与诊断页验收(V19 T8):
 * - 三分区渲染(框架与作用域 / 通信服务 / 管道统计)+ 框架态行(按 VM 实际态);
 * - 「复制诊断信息」:剪贴板快照与页面行同源(端口/统计标签/框架行),Snackbar 反馈。
 */
@RunWith(AndroidJUnit4::class)
class DiagnosticsScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun str(id: Int) = ctx.getString(id)

    private fun str(id: Int, vararg args: Any) = ctx.getString(id, *args)

    private fun scrollTo(text: String) {
        rule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText(text))
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

    /** 框架行期望文本(与页面渲染同源,按 VM 实际态)。 */
    private fun frameworkValue(vm: DiagnosticsViewModel): String = when (val f = vm.frameworkState.value) {
        is ScopeState.Connecting -> str(R.string.settings_connecting)
        is ScopeState.Ready -> "${f.frameworkName} ${f.frameworkVersion}"
        is ScopeState.Unavailable -> str(R.string.settings_not_connected)
    }

    @Test
    fun threeSections_andServiceRows() {
        val container = (ctx.applicationContext as ModevApp).container
        val vm = DiagnosticsViewModelFactory(container).create(DiagnosticsViewModel::class.java)

        rule.setContent { MaterialTheme { DiagnosticsScreen(vm, onBack = {}) } }

        // 三分区:框架与作用域 / 通信服务 / 管道统计(统计折叠区滚动可达)。
        rule.onNodeWithText(str(R.string.settings_framework_section)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.settings_server_section)).assertIsDisplayed()
        scrollTo(str(R.string.settings_stats_section))
        rule.onNodeWithText(str(R.string.settings_stats_section)).assertIsDisplayed()

        // 框架态行 + 通信服务行(端口值 / 在线目标)。
        rule.onNodeWithText(frameworkValue(vm)).assertIsDisplayed()
        scrollTo(str(R.string.settings_port_value, vm.port))
        rule.onNodeWithText(str(R.string.settings_port_value, vm.port)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.settings_online_targets_value, vm.connectedCount.value)).assertIsDisplayed()
    }

    @Test
    fun copyDiagnostics_writesClipboard() {
        val container = (ctx.applicationContext as ModevApp).container
        val vm = DiagnosticsViewModelFactory(container).create(DiagnosticsViewModel::class.java)

        rule.setContent { MaterialTheme { DiagnosticsScreen(vm, onBack = {}) } }

        scrollTo(str(R.string.diagnostics_copy))
        rule.onNodeWithText(str(R.string.diagnostics_copy)).performClick()

        // 剪贴板快照:与页面行同源(端口/统计标签/框架行)。
        val text = readClipboard()
        assertTrue("端口行缺失:\n$text", text.contains("127.0.0.1:${vm.port}"))
        assertTrue("统计标签缺失:\n$text", text.contains(str(R.string.stats_received)))
        assertTrue("框架行缺失:\n$text", text.contains(frameworkValue(vm)))

        // Snackbar 反馈。
        waitForVisible(str(R.string.diagnostics_copied))
    }
}