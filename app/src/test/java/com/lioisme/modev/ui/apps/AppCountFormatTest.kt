package com.lioisme.modev.ui.apps

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 记录数紧凑格式化契约(2026-08-18 V15):
 * zh ≥1 万 → 「1.2万」式紧凑(1 位小数);en ≥1 千 → 「1.2K」;
 * 阈值以下千分位整数(「3,000」,不再有「3千+」式自造单位);0 → 「0」。
 * Robolectric 提供真实 android.icu 实现(纯 JVM 下为桩,静态方法返回 null)。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppCountFormatTest {

    private val zh = Locale.SIMPLIFIED_CHINESE
    private val en = Locale.US

    @Test
    fun zh_compact_aboveTenThousand() {
        assertEquals("1.2万", AppCountFormat.compact(12_345, zh))
        assertEquals("10万", AppCountFormat.compact(100_000, zh))
    }

    @Test
    fun zh_grouped_belowTenThousand() {
        assertEquals("3,000", AppCountFormat.compact(3_000, zh))
        assertEquals("999", AppCountFormat.compact(999, zh))
        assertEquals("0", AppCountFormat.compact(0, zh))
    }

    @Test
    fun en_compact_aboveThousand() {
        assertEquals("1.2K", AppCountFormat.compact(1_200, en))
        assertEquals("1.5M", AppCountFormat.compact(1_500_000, en))
    }

    @Test
    fun en_grouped_belowThousand() {
        assertEquals("999", AppCountFormat.compact(999, en))
        // 恰在阈值(1,000)上走紧凑档
        assertEquals("1K", AppCountFormat.compact(1_000, en))
    }

    @Test
    fun thresholds_follow_locale() {
        assertEquals(10_000L, AppCountFormat.compactThreshold(zh))
        assertEquals(1_000L, AppCountFormat.compactThreshold(en))
        // V42 T5:ja/ko 归入 CJK 万进位组(10^4,与 ICU 原生万/만 进位一致)
        assertEquals(10_000L, AppCountFormat.compactThreshold(Locale.JAPANESE))
        assertEquals(10_000L, AppCountFormat.compactThreshold(Locale.KOREAN))
        // 边界:恰在阈值上走紧凑,之下走千分位
        assertEquals("1万", AppCountFormat.compact(10_000, zh))
        assertEquals("9,999", AppCountFormat.compact(9_999, zh))
    }

    // ---- V42 T5:ja/ko 紧凑格式(ICU 原生万/만 进位) ----

    @Test
    fun ja_compact_aboveTenThousand() {
        val ja = Locale.JAPANESE
        assertEquals("1.2万", AppCountFormat.compact(12_345, ja))
        assertEquals("1万", AppCountFormat.compact(10_000, ja))
        // 阈值之下走千分位(与 zh 同 10^4 档,不再提前进位为「9.9千」)
        assertEquals("9,999", AppCountFormat.compact(9_999, ja))
    }

    @Test
    fun ko_compact_aboveTenThousand() {
        val ko = Locale.KOREAN
        assertEquals("1.2만", AppCountFormat.compact(12_345, ko))
        assertEquals("1만", AppCountFormat.compact(10_000, ko))
        assertEquals("9,999", AppCountFormat.compact(9_999, ko))
    }

    @Test
    fun grouped_addsLocaleGrouping() {
        assertEquals("1,234,567", AppCountFormat.grouped(1_234_567, en))
        assertEquals("123", AppCountFormat.grouped(123, en))
        assertEquals("0", AppCountFormat.grouped(0, en))
    }
}