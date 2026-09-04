package com.lioisme.modev.hook

import com.lioisme.modev.core.model.AppListenOverride
import com.lioisme.modev.core.model.HookSwitch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V36 D-1 回归:本地偏差层与 TCP 全局推送的合并语义
 * (Vector 2.2 磁盘兜底配置不得被宿主推送覆盖)。
 */
class ConfigChannelDeviationTest {

    private fun channel() = ConfigChannel(
        packageName = "com.example.app",
        processName = "com.example.app",
        onConfig = {},
    )

    @Test
    fun `tcp push alone sets config directly`() {
        val c = channel()
        c.updateConfig(HookSwitch.DEFAULT.copy(base64 = true))
        assertTrue(c.currentConfig.base64)
    }

    @Test
    fun `deviation survives later global push`() {
        val c = channel()
        c.localDeviation = AppListenOverride(kinds = mapOf("BASE64" to true))
        assertTrue("deviation applied immediately", c.currentConfig.base64)
        // 模拟宿主 hello 后推送纯默认(不含 BASE64)
        c.updateConfig(HookSwitch.DEFAULT)
        assertTrue("deviation must survive global push", c.currentConfig.base64)
    }

    @Test
    fun `deviation off-override forces disable over global on`() {
        val c = channel()
        c.updateConfig(HookSwitch.DEFAULT)
        assertTrue(c.currentConfig.cipher)
        c.localDeviation = AppListenOverride(kinds = mapOf("CIPHER" to false))
        assertFalse(c.currentConfig.cipher)
        c.updateConfig(HookSwitch.DEFAULT)
        assertFalse(c.currentConfig.cipher)
    }

    @Test
    fun `clearing deviation restores global layer`() {
        val c = channel()
        c.localDeviation = AppListenOverride(kinds = mapOf("BASE64" to true))
        c.localDeviation = null
        assertEquals(HookSwitch.DEFAULT.base64, c.currentConfig.base64)
    }
}
