package com.lioisme.modev.ui.common

import java.util.Calendar
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 时间线分组纯函数契约:
 * secondKey 跨秒边界;dayKey 跨日/跨月;formatSecond 12/24h;formatDay en/zh;
 * 组头决策:同秒 null / 分页边界 null / 跨日带日期前缀。
 * 时间戳经本地时区 Calendar 构造(格式化走默认时区,确定性不锁时区)。
 */
class TimelineGroupTest {

    private fun ts(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        second: Int,
    ): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, hour, minute, second)
        }.timeInMillis

    @Test
    fun `secondKey differs across second boundary and embeds date`() {
        val a = ts(2026, 8, 19, 14, 32, 5)
        assertEquals("20260819143205", secondKey(a))
        assertNotEquals(secondKey(a), secondKey(a + 1000))
        assertNotEquals(secondKey(a), secondKey(a - 1000))
    }

    @Test
    fun `dayKey differs across day and month boundaries`() {
        assertNotEquals(
            dayKey(ts(2026, 8, 19, 23, 59, 59)),
            dayKey(ts(2026, 8, 20, 0, 0, 0)),
        )
        assertNotEquals(
            dayKey(ts(2026, 7, 31, 23, 59, 59)),
            dayKey(ts(2026, 8, 1, 0, 0, 0)),
        )
        assertEquals("20260819", dayKey(ts(2026, 8, 19, 14, 32, 5)))
    }

    @Test
    fun `formatSecond respects 24h and 12h`() {
        val t = ts(2026, 8, 19, 14, 32, 5)
        assertEquals("14:32:05", formatSecond(t, Locale.US, is24h = true))
        assertEquals("2:32:05 PM", formatSecond(t, Locale.US, is24h = false))
    }

    @Test
    fun `formatDay respects locale`() {
        val t = ts(2026, 8, 19, 0, 0, 0)
        assertEquals("Aug 19", formatDay(t, Locale.US))
        assertEquals("8月19日", formatDay(t, Locale.SIMPLIFIED_CHINESE))
        // V42 T5:ja/ko 母语惯形(默认 MMM 在 ja/ko 输出「8月 19」截断形态,禁回退)
        assertEquals("8月19日", formatDay(t, Locale.JAPANESE))
        assertEquals("8월 19일", formatDay(t, Locale.KOREAN))
    }

    @Test
    fun `timelineLabel always carries day prefix`() {
        assertEquals(
            "Aug 19 14:32:05",
            timelineLabel(ts(2026, 8, 19, 14, 32, 5), Locale.US, is24h = true),
        )
    }

    @Test
    fun `groupLabelOrNull null for same second and paging boundary`() {
        val t = ts(2026, 8, 19, 14, 32, 5)
        assertNull(groupLabelOrNull(t, t, Locale.US, true))
        assertNull(groupLabelOrNull(t, null, Locale.US, true))
        // 同秒同组:±0.5s 内不跨秒
        assertNull(groupLabelOrNull(t + 500, t, Locale.US, true))
    }

    @Test
    fun `groupLabelOrNull same day shows second only`() {
        val a = ts(2026, 8, 19, 14, 32, 5)
        val b = ts(2026, 8, 19, 14, 32, 6)
        assertEquals("14:32:06", groupLabelOrNull(b, a, Locale.US, true))
    }

    @Test
    fun `groupLabelOrNull cross day carries date prefix`() {
        val a = ts(2026, 8, 19, 23, 59, 59)
        val b = ts(2026, 8, 20, 0, 0, 1)
        assertEquals("8月20日 00:00:01", groupLabelOrNull(b, a, Locale.SIMPLIFIED_CHINESE, true))
    }
}
