package com.lioisme.modev.hook.hooks

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 会话节流契约:同键窗口内去重、窗口过期放行、LRU 容量淘汰、空键直通。
 */
class SessionThrottleTest {

    @Test
    fun `same key within window is skipped`() {
        val throttle = SessionThrottle(windowMs = 1000)
        assertFalse(throttle.shouldSkip("FILE_READ|OPEN_READ|/a.txt", nowMs = 0))
        assertTrue(throttle.shouldSkip("FILE_READ|OPEN_READ|/a.txt", nowMs = 500))
        assertTrue(throttle.shouldSkip("FILE_READ|OPEN_READ|/a.txt", nowMs = 999))
    }

    @Test
    fun `expired window admits event again`() {
        val throttle = SessionThrottle(windowMs = 1000)
        assertFalse(throttle.shouldSkip("k", nowMs = 0))
        assertTrue(throttle.shouldSkip("k", nowMs = 500))
        assertFalse(throttle.shouldSkip("k", nowMs = 1001))
    }

    @Test
    fun `distinct keys are independent`() {
        val throttle = SessionThrottle(windowMs = 1000)
        assertFalse(throttle.shouldSkip("a", nowMs = 0))
        assertFalse(throttle.shouldSkip("b", nowMs = 0))
    }

    @Test
    fun `capacity evicts least recently used keys`() {
        val throttle = SessionThrottle(capacity = 2, windowMs = 10_000)
        assertFalse(throttle.shouldSkip("a", nowMs = 0))
        assertFalse(throttle.shouldSkip("b", nowMs = 0))
        // 命中 a → a 变最新;b 被挤出
        assertTrue(throttle.shouldSkip("a", nowMs = 1))
        assertFalse(throttle.shouldSkip("c", nowMs = 2))
        // b 已被 LRU 淘汰 → 放行
        assertFalse(throttle.shouldSkip("b", nowMs = 3))
        // c 仍在窗口内 → 拦截
        assertTrue(throttle.shouldSkip("c", nowMs = 4))
    }

    @Test
    fun `empty key passes through`() {
        val throttle = SessionThrottle()
        assertFalse(throttle.shouldSkip("", nowMs = 0))
        assertFalse(throttle.shouldSkip("", nowMs = 1))
    }
}
