package com.lioisme.modev

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lioisme.modev.domain.server.ConnectionMonitor
import com.lioisme.modev.ui.settings.ServicesScreen
import com.lioisme.modev.ui.settings.ServicesViewModel
import com.lioisme.modev.ui.settings.ServicesViewModelFactory
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 服务页验收(V39 拆分自「服务与诊断」;V40 T2 解耦;V41 T2 统一外部访问;
 * V42 T3 端口可用性校验):
 * - 三分区渲染(通用[统一外部访问] / 通信服务[注入侧] / MCP 服务)+ 令牌管理入口行;
 * - 统一外部访问开关默认停用,切换即改状态并驱动 wire 改绑(MCP 经同一偏好消费);
 * - V41 D-1:单一开关双服务消费,旧双行(services-wire-external/services-mcp-external)
 *   退役;与令牌认证互相独立(V40 D-4 语义保持),切换不得级联认证/派生令牌;
 * - V42 T3:ModevPortSheet 异步可用性校验(PortValidator 特权/冲突/占用三闸 +
 *   自身豁免)硬阻止保存;wire bindError 观测行(与 MCP 行对称)渲染/恢复。
 */
@RunWith(AndroidJUnit4::class)
class ServicesScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun str(id: Int) = ctx.getString(id)

    private lateinit var vm: ServicesViewModel
    private lateinit var settings: com.lioisme.modev.data.repo.SettingsRepository
    private lateinit var server: ConnectionMonitor

    private fun setContent() {
        val container = (ctx.applicationContext as ModevApp).container
        settings = container.settingsRepo
        // wire 真源观测(V41:VM 不再暴露监听地址流,绑地址观测归诊断页;
        // 测试经 ConnectionMonitor 抽象轮询实际 bind 态)。
        server = container.wireServer
        runBlocking {
            settings.setExternalAccess(false)
            settings.setMcpTokenAuth(false)
        }
        vm = ServicesViewModelFactory(container).create(ServicesViewModel::class.java)
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline &&
            (server.boundAddress.value ?: ConnectionMonitor.BIND_LOOPBACK) != ConnectionMonitor.BIND_LOOPBACK
        ) {
            Thread.sleep(100)
        }
        rule.setContent { MaterialTheme { ServicesScreen(vm, onBack = {}, onOpenTokens = {}) } }
        rule.waitForIdle()
    }

    @Test
    fun threeSections_andTokenEntry() {
        setContent()
        rule.onNodeWithText(str(R.string.services_common_section)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.settings_server_section_injection)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.mcp_section)).assertIsDisplayed()
        rule.onNodeWithTag("services-tokens-entry").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(str(R.string.mcp_tokens_entry)).assertIsDisplayed()
    }

    @Test
    fun unifiedExternalAccess_defaultOff_toggleOnRebindsAllInterfaces() {
        setContent()

        // 旧双行退役断言:双键分裂形态不得复活(V41 D-1)。
        rule.onAllNodesWithTag("services-wire-external").assertCountEquals(0)
        rule.onAllNodesWithTag("services-mcp-external").assertCountEquals(0)

        rule.onNodeWithTag("services-external-access").performScrollTo().assertIsOff()
        rule.onNodeWithTag("services-external-access").performClick()
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline && !vm.externalAccess.value) {
            Thread.sleep(100)
            rule.waitForIdle()
        }
        assertEquals(true, vm.externalAccess.value)
        // 副标题 = 开关态的纯函数映射(单源,无派生流调度差)。
        assertEquals("统一开关副标题 = 全接口地址", ConnectionMonitor.BIND_ALL_INTERFACES, vm.externalAddressLabel(true))
        assertEquals("停用态副标题 = 回环地址", ConnectionMonitor.BIND_LOOPBACK, vm.externalAddressLabel(false))
        rule.onNodeWithTag("services-external-access").assertIsOn()

        var rebound = false
        while (System.currentTimeMillis() < deadline && !rebound) {
            rule.waitForIdle()
            rebound = (server.boundAddress.value ?: ConnectionMonitor.BIND_LOOPBACK) ==
                ConnectionMonitor.BIND_ALL_INTERFACES
            if (!rebound) Thread.sleep(100)
        }
        assertEquals(ConnectionMonitor.BIND_ALL_INTERFACES, server.boundAddress.value)
    }

    @Test
    fun mcpTokenAuth_defaultOff_toggleOnDerivesToken() {
        setContent()

        rule.onNodeWithTag("services-mcp-token-auth").performScrollTo().assertIsOff()
        rule.onNodeWithTag("services-mcp-token-auth").performClick()
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline && !vm.mcpTokenAuth.value) {
            Thread.sleep(100)
            rule.waitForIdle()
        }
        assertTrue("启用令牌认证应即时派生令牌", vm.mcpTokenCount.value >= 1)
        rule.onNodeWithTag("services-mcp-token-auth").assertIsOn()
    }

    @Test
    fun unifiedExternalAccess_toggleOn_keepsTokenAuthIndependent() {
        // V40 D-4 解耦红线(V41 统一开关同体适用):开外部访问不级联启用认证、
        // 不自动派生令牌。令牌跨进程持久(V38 契约),设备上起点数量可能 >0 →
        // 以「数量不变」断言。
        setContent()

        val tokensBefore = settings.mcpTokens.value.size
        rule.onNodeWithTag("services-external-access").performScrollTo().assertIsOff()
        rule.onNodeWithTag("services-external-access").performClick()
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline && !vm.externalAccess.value) {
            Thread.sleep(100)
            rule.waitForIdle()
        }
        assertEquals(true, vm.externalAccess.value)
        assertEquals("外部访问不得级联启用令牌认证", false, vm.mcpTokenAuth.value)
        assertEquals("外部访问不得自动派生令牌", tokensBefore, settings.mcpTokens.value.size)
        rule.onNodeWithTag("services-mcp-token-auth").performScrollTo().assertIsOff()
    }

    @After
    fun tearDown() {
        if (this::settings.isInitialized) {
            runBlocking {
                settings.setExternalAccess(false)
                settings.setMcpTokenAuth(false)
                // V42 端口用例卫生:恢复默认端口(写入即触发重绑,退避窗口自愈)。
                settings.setServerPort(DEFAULT_WIRE_PORT)
            }
        }
    }

    // ---- V42 T3 端口可用性校验(sheet 异步流 + wire bindError 观测行) ----

    private fun str(res: Int, arg: Any?): String = ctx.getString(res, arg)

    /** 打开 wire 端口 sheet(点击端口行)并断言锚点就位。 */
    private fun openWirePortSheet() {
        rule.onNodeWithText(str(R.string.settings_port)).performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("port-setting-sheet").assertIsDisplayed()
        rule.onNodeWithTag("port-input").assertIsDisplayed()
    }

    /** 有界轮询:sheet 已关闭(校验通过路径)。 */
    private fun awaitSheetClosed() {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline &&
            rule.onAllNodes(hasTestTag("port-setting-sheet")).fetchSemanticsNodes().isNotEmpty()
        ) {
            Thread.sleep(100)
            rule.waitForIdle()
        }
        rule.onAllNodes(hasTestTag("port-setting-sheet")).assertCountEquals(0)
    }

    /** 有界轮询:行内错误文案出现(校验失败路径,异步 IO 落定)。 */
    private fun awaitErrorText(vararg texts: String) {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            rule.waitForIdle()
            if (texts.any { t -> rule.onAllNodesWithText(t).fetchSemanticsNodes().isNotEmpty() }) return
            Thread.sleep(100)
        }
        throw AssertionError("校验错误文案未在时限内出现: ${texts.joinToString()}")
    }

    /** 输入候选端口并点确认(校验异步,断言由后续 await* 完成)。 */
    private fun submitPort(candidate: String) {
        rule.onNodeWithTag("port-input").performTextReplacement(candidate)
        rule.waitForIdle()
        rule.onNodeWithTag("port-confirm").performClick()
    }

    @Test
    fun portSheet_outOfRange_showsError_andKeepsSheetOpen() {
        setContent()
        openWirePortSheet()
        submitPort("99999")
        awaitErrorText(str(R.string.settings_port_invalid))
        // 硬阻止:sheet 保持打开 + 偏好零写入。
        rule.onNodeWithTag("port-setting-sheet").assertIsDisplayed()
        assertEquals("非法端口不得写入偏好", DEFAULT_WIRE_PORT, settings.serverPort.value)
    }

    @Test
    fun portSheet_privilegedPort_hardBlocked() {
        setContent()
        openWirePortSheet()
        submitPort("80")
        awaitErrorText(str(R.string.settings_port_privileged))
        rule.onNodeWithTag("port-setting-sheet").assertIsDisplayed()
        assertEquals("特权端口不得写入偏好", DEFAULT_WIRE_PORT, settings.serverPort.value)
    }

    @Test
    fun portSheet_conflictWithMcp_hardBlocked() {
        setContent()
        // MCP 端口 1510:wire 录 1510 → 冲突(错误文案携带对端名 MCP)。
        openWirePortSheet()
        submitPort("1510")
        awaitErrorText(str(R.string.settings_port_conflict, "MCP"))
        rule.onNodeWithTag("port-setting-sheet").assertIsDisplayed()
        assertEquals("冲突端口不得写入偏好", DEFAULT_WIRE_PORT, settings.serverPort.value)
    }

    @Test
    fun portSheet_inUsePort_hardBlocked() {
        setContent()
        // 测试进程内占用一个高位端口(探测语义:活跃监听必 EADDRINUSE)。
        val socket = java.net.ServerSocket(0).also { it.reuseAddress = true }
        try {
            openWirePortSheet()
            submitPort(socket.localPort.toString())
            awaitErrorText(str(R.string.settings_port_in_use))
            rule.onNodeWithTag("port-setting-sheet").assertIsDisplayed()
            assertEquals("占用端口不得写入偏好", DEFAULT_WIRE_PORT, settings.serverPort.value)
        } finally {
            socket.close()
        }
    }

    @Test
    fun portSheet_freePort_savesAndDismisses() {
        setContent()
        // 预占即释:取一个当前空闲端口样本。
        val freePort = java.net.ServerSocket(0).use { it.localPort }
        openWirePortSheet()
        submitPort(freePort.toString())
        awaitSheetClosed()
        assertEquals("空闲端口应保存", freePort, settings.serverPort.value)
    }

    @Test
    fun portSheet_selfPort_exemptFromProbe_savesDirectly() {
        setContent()
        // 自身当前端口 = 探测豁免(重存原值 no-op)——即使该端口正被 wire 监听。
        val current = settings.serverPort.value
        openWirePortSheet()
        submitPort(current.toString())
        awaitSheetClosed()
        assertEquals("自身端口重存应放行", current, settings.serverPort.value)
    }

    @Test
    fun wireBindError_rowRendersOnFailure_andClearsOnRecovery() {
        setContent()
        // V45 T2.4 契约后果:端口写路径现在**先过 PortWriteGuard 硬闸**,
        // 「写入一个必败端口」在方法层就被拒(偏好零写入) ⇒ bindError 观测面
        // 不能再经仓库写路径制造。真实世界里 bind 失败的来源是「写后端口被他人抢占」,
        // 即绑定层的最终裁决,故本例改由**服务端 seam** 注入失败(行的数据源本就是
        // ConnectionMonitor.bindError,与谁改端口无关)。
        //
        // 序列:①迁到空闲端口(合法写)→ ②占位 1511 → ③seam 改端口 + restart →
        // bind EADDRINUSE → bindError 置位 → 行渲染;④撤占 + restart → 行清空;
        // ⑤seam 先退回空闲端口(释放 1511)→ ⑥再经**合法写路径**把持久偏好改回 1511。
        val staging = java.net.ServerSocket(0).use { it.localPort }
        runBlocking { settings.setServerPort(staging) }
        // 空闲端口绑定即时完成;固定 settle 等重启落定(boundAddress 无端口观测面)。
        Thread.sleep(2_000)

        val squatter = java.net.ServerSocket().also { it.reuseAddress = true }
        squatter.bind(java.net.InetSocketAddress(ConnectionMonitor.BIND_LOOPBACK, DEFAULT_WIRE_PORT))
        try {
            rebindWireServer(DEFAULT_WIRE_PORT)
            val deadline = System.currentTimeMillis() + 20_000
            var errored = false
            while (System.currentTimeMillis() < deadline) {
                rule.waitForIdle()
                if (rule.onAllNodes(hasTestTag("wire-bind-error-row")).fetchSemanticsNodes().isNotEmpty()) {
                    errored = true
                    break
                }
                Thread.sleep(200)
            }
            assertTrue("端口被占时 wire 绑定错误行应渲染", errored)
            rule.onNodeWithText(str(R.string.wire_bind_error_title)).assertIsDisplayed()
        } finally {
            squatter.close()
        }
        // 恢复:再触发一次重启(1511 已释放)→ 绑定成功 → bindError 清空 → 行消失。
        rebindWireServer(DEFAULT_WIRE_PORT)
        val recoveryDeadline = System.currentTimeMillis() + 20_000
        while (System.currentTimeMillis() < recoveryDeadline) {
            rule.waitForIdle()
            if (rule.onAllNodes(hasTestTag("wire-bind-error-row")).fetchSemanticsNodes().isEmpty()) break
            Thread.sleep(200)
        }
        rule.onAllNodes(hasTestTag("wire-bind-error-row")).assertCountEquals(0)
        // 持久偏好复位:先让服务端退回空闲端口(释放 1511),再走合法写路径改回默认。
        rebindWireServer(staging)
        runBlocking { settings.setServerPort(DEFAULT_WIRE_PORT) }
        assertEquals("恢复后端口保持", DEFAULT_WIRE_PORT, settings.serverPort.value)
    }

    /** 服务端 seam 改端口并重启(仅测试注入绑定失败用;用户面写路径恒带硬闸)。 */
    private fun rebindWireServer(port: Int) {
        val wire = server as com.lioisme.modev.data.server.ConfigServer
        wire.port = port
        kotlinx.coroutines.runBlocking { wire.restart() }
        Thread.sleep(1_500)
    }

    private companion object {
        /** wire 默认端口(WireProtocol.PORT = 1511;用例以「写入未发生」断言比对)。 */
        const val DEFAULT_WIRE_PORT = 1511
    }
}
