package com.lioisme.modev.ui.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 双击返回退出状态机(V39 R8,JVM 直测):注入时钟,窗口语义精确判定。
 */
class DoubleBackExitTest {

    @Test
    fun `first press prompts, second within window exits`() {
        var now = 1_000L
        val exit = DoubleBackExit(windowMs = 2_000L, now = { now })

        assertFalse("首次返回应提示", exit.press())
        now += 1_999
        assertTrue("窗口内二次返回应退出", exit.press())
    }

    @Test
    fun `press after window expiry prompts again`() {
        var now = 0L
        val exit = DoubleBackExit(windowMs = 2_000L, now = { now })

        assertFalse(exit.press())
        now += 2_000
        assertFalse("窗口已过(恰达边界)重新提示", exit.press())
        now += 100
        assertTrue("提示后窗口内再按退出", exit.press())
    }

    @Test
    fun `reset clears the window`() {
        var now = 0L
        val exit = DoubleBackExit(windowMs = 2_000L, now = { now })

        assertFalse(exit.press())
        exit.reset()
        now += 10
        assertFalse("复位后再按 = 首次提示", exit.press())
    }
}
