package com.lioisme.modev.ui.apps

import org.junit.Assert.assertEquals
import org.junit.Test

/** 记录数紧凑格式化(纯函数,en/zh 资源语义对齐,回归 S3)。 */
class AppCountFormatTest {

    // 缩放语义与 UI 一致:zh 万=÷10000、千=÷1000;en 恒 K=÷1000。
    private val zhWan = { n: Long -> "${n / 10_000}万+" }
    private val zhQian = { n: Long -> "${n / 1_000}千+" }
    private val enK = { n: Long -> "${n / 1_000}K+" }

    @Test
    fun `wan level uses wan formatter`() {
        assertEquals("2万+", AppCountFormat.compact(20_000, zhWan, zhQian))
        assertEquals("20K+", AppCountFormat.compact(20_000, enK, enK))
    }

    @Test
    fun `qian level uses qian formatter`() {
        assertEquals("2千+", AppCountFormat.compact(2_000, zhWan, zhQian))
        assertEquals("2K+", AppCountFormat.compact(2_000, enK, enK))
    }

    @Test
    fun `boundary rounding is floor`() {
        assertEquals("1万+", AppCountFormat.compact(10_000, zhWan, zhQian))
        assertEquals("9K+", AppCountFormat.compact(9_999, enK, enK))
        assertEquals("1K+", AppCountFormat.compact(1_000, enK, enK))
    }

    @Test
    fun `below thousand renders verbatim`() {
        assertEquals("999", AppCountFormat.compact(999, zhWan, zhQian))
        assertEquals("0", AppCountFormat.compact(0, zhWan, zhQian))
        assertEquals("1", AppCountFormat.compact(1, enK, enK))
    }
}