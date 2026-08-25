package com.lioisme.modev

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 服务与诊断页验收(V19 T8;V26 外部访问开关 + 端口行参数化):
 * - 三分区渲染(框架与作用域 / 通信服务 / 管道统计)+ 框架态行(按 VM 实际态);
 * - 端口行 = 监听地址:端口 + 作用域后缀(与 VM listeningAddress 同源,不再断言字面量);
 * - 外部访问开关默认停用;切换即改状态并同步作用域后缀(@After 复位停用);
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
        rule.waitForIdle()
        vm = DiagnosticsViewModelFactory(container).create(DiagnosticsViewModel::class.java)
        rule.setContent { MaterialTheme { DiagnosticsScreen(vm, onBack = {}) } }
        rule.waitForIdle()
    }

    /** 端口行期望值(与页面渲染同源:地址:端口 + 作用域后缀)。 */
    private fun portRowValue(address: String, external: Boolean): String =
        str(R.string.settings_port_value, address, vm.port) + " " +
            str(if (external) R.string.settings_port_scope_all else R.string.settings_port_scope_local)

    private fun scrollTo(text: String) {
        rule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText(text))
    }

    @Test
    fun threeSections_andServiceRows() {
        setContent()

        // 三分区:框架与作用域 / 通信服务 / 管道统计(统计折叠区滚动可达)。
        rule.onNodeWithText(str(R.string.settings_framework_section)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.settings_server_section)).assertIsDisplayed()
        scrollTo(str(R.string.settings_stats_section))
        rule.onNodeWithText(str(R.string.settings_stats_section)).assertIsDisplayed()

        // 框架态行 + 通信服务行(参数化端口值 / 在线目标)。
        rule.onNodeWithText(frameworkValue(vm)).assertIsDisplayed()
        val expectedPort = portRowValue(ConnectionMonitor.BIND_LOOPBACK, external = false)
        scrollTo(expectedPort)
        rule.onNodeWithText(expectedPort).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.settings_online_targets_value, vm.connectedCount.value)).assertIsDisplayed()
    }

    @Test
    fun copyDiagnostics_writesClipboard_parameterDriven() {
        setContent()

        scrollTo(str(R.string.diagnostics_copy))
        rule.onNodeWithText(str(R.string.diagnostics_copy)).performClick()

        // 剪贴板快照:与页面行同源(参数驱动的端口行/统计标签/框架行)。
        val text = readClipboard()
        val expectedPort =
            str(
                R.string.settings_port_value,
                ConnectionMonitor.BIND_LOOPBACK,
                vm.port,
            ) + " " + str(R.string.settings_port_scope_local)
        assertTrue("端口行缺失:\n$text", text.contains(expectedPort))
        assertTrue("统计标签缺失:\n$text", text.contains(str(R.string.stats_received)))
        assertTrue("框架行缺失:\n$text", text.contains(frameworkValue(vm)))

        // Snackbar 反馈。
        waitForVisible(str(R.string.diagnostics_copied))
    }

    @Test
    fun externalAccessSwitch_defaultOff_toggleOnUpdatesScopeSuffix() {
        setContent()

        val switchTitle = str(R.string.settings_external_access)
        scrollTo(switchTitle)
        rule.onNodeWithText(switchTitle).assertIsDisplayed()

        // 默认停用:作用域后缀 = 仅本机(瞬态组合窗口重试兜底)。
        val localValue = portRowValue(vm.listeningAddress.value, external = false)
        var shownLocal = false
        val localDeadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < localDeadline && !shownLocal) {
            try {
                scrollTo(localValue)
                rule.onNodeWithText(localValue).assertIsDisplayed()
                shownLocal = true
            } catch (_: AssertionError) {
                Thread.sleep(100)
            } catch (_: IllegalStateException) {
                Thread.sleep(100)
            }
        }
        assertTrue(shownLocal)

        // 开启 → 状态翻转,作用域后缀变为「所有接口」(@After 复位)。
        rule.onNodeWithTag("diagnostics-bind-all").performClick()
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline && !vm.externalAccess.value) {
            Thread.sleep(100)
            rule.waitForIdle()
        }
        assertEquals(true, vm.externalAccess.value)

        // 等待服务端完成改绑(VM 地址观测点轮询),再验证页面行同源刷新。
        var rebound = false
        while (System.currentTimeMillis() < deadline && !rebound) {
            rule.waitForIdle()
            rebound = vm.listeningAddress.value == ConnectionMonitor.BIND_ALL_INTERFACES
            if (!rebound) Thread.sleep(100)
        }
        assertEquals(ConnectionMonitor.BIND_ALL_INTERFACES, vm.listeningAddress.value)

        // 页面行按当前地址动态构建期望(避免重启窗口期固化陈旧值)。
        var shownAll = false
        while (System.currentTimeMillis() < deadline && !shownAll) {
            try {
                val expectedNow = portRowValue(vm.listeningAddress.value, external = true)
                scrollTo(expectedNow)
                rule.onNodeWithText(expectedNow).assertIsDisplayed()
                shownAll = true
            } catch (_: AssertionError) {
                Thread.sleep(100)
            } catch (_: IllegalStateException) {
                // 组合窗口重建瞬态,下一轮重试
                Thread.sleep(100)
            }
        }
        assertTrue("开启外部访问后端口行应显示所有接口后缀", shownAll)
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
