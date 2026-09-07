package com.lioisme.modev

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
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
 * 服务页验收(V39 拆分自「服务与诊断」;V40 T2 解耦修订):
 * - 两分区渲染(通信服务[注入侧] / MCP 服务)+ 令牌管理入口行;
 * - wire 外部访问开关默认停用,切换即改状态并驱动服务端改绑(@After 复位);
 * - MCP 令牌认证开关默认停用;V40 D-4:外部访问与令牌认证互相独立,
 *   开外部访问不得级联启用认证(仓库级联已退役)。
 */
@RunWith(AndroidJUnit4::class)
class ServicesScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun str(id: Int) = ctx.getString(id)

    private lateinit var vm: ServicesViewModel
    private lateinit var settings: com.lioisme.modev.data.repo.SettingsRepository

    private fun setContent() {
        val container = (ctx.applicationContext as ModevApp).container
        settings = container.settings
        runBlocking {
            settings.setServerExternalAccess(false)
            settings.setMcpExternalAccess(false)
            settings.setMcpTokenAuth(false)
        }
        vm = ServicesViewModelFactory(container).create(ServicesViewModel::class.java)
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline &&
            vm.wireListeningAddress.value != ConnectionMonitor.BIND_LOOPBACK
        ) {
            Thread.sleep(100)
        }
        rule.setContent { MaterialTheme { ServicesScreen(vm, onBack = {}, onOpenTokens = {}) } }
        rule.waitForIdle()
    }

    @Test
    fun twoSections_andTokenEntry() {
        setContent()
        rule.onNodeWithText(str(R.string.settings_server_section_injection)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.mcp_section)).assertIsDisplayed()
        rule.onNodeWithTag("services-tokens-entry").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(str(R.string.mcp_tokens_entry)).assertIsDisplayed()
    }

    @Test
    fun wireExternalAccess_defaultOff_toggleOnRebindsAllInterfaces() {
        setContent()

        rule.onNodeWithTag("services-wire-external").performScrollTo().assertIsOff()
        rule.onNodeWithTag("services-wire-external").performClick()
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline && !vm.wireExternalAccess.value) {
            Thread.sleep(100)
            rule.waitForIdle()
        }
        assertEquals(true, vm.wireExternalAccess.value)
        rule.onNodeWithTag("services-wire-external").assertIsOn()

        var rebound = false
        while (System.currentTimeMillis() < deadline && !rebound) {
            rule.waitForIdle()
            rebound = vm.wireListeningAddress.value == ConnectionMonitor.BIND_ALL_INTERFACES
            if (!rebound) Thread.sleep(100)
        }
        assertEquals(ConnectionMonitor.BIND_ALL_INTERFACES, vm.wireListeningAddress.value)
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
    fun mcpExternalAccess_toggleOn_keepsTokenAuthIndependent() {
        // V40 D-4:开外部访问不级联启用认证、不自动派生令牌(解耦红线)。
        // 令牌跨进程持久(V38 契约),设备上起点数量可能 >0 → 以「数量不变」断言。
        setContent()

        val tokensBefore = settings.mcpTokens.value.size
        rule.onNodeWithTag("services-mcp-external").performScrollTo().assertIsOff()
        rule.onNodeWithTag("services-mcp-external").performClick()
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline && !vm.mcpExternalAccess.value) {
            Thread.sleep(100)
            rule.waitForIdle()
        }
        assertEquals(true, vm.mcpExternalAccess.value)
        assertEquals("外部访问不得级联启用令牌认证", false, vm.mcpTokenAuth.value)
        assertEquals("外部访问不得自动派生令牌", tokensBefore, settings.mcpTokens.value.size)
        rule.onNodeWithTag("services-mcp-token-auth").performScrollTo().assertIsOff()
    }

    @After
    fun tearDown() {
        if (this::settings.isInitialized) {
            runBlocking {
                settings.setServerExternalAccess(false)
                settings.setMcpExternalAccess(false)
                settings.setMcpTokenAuth(false)
            }
        }
    }
}
