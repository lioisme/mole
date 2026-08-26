package com.lioisme.modev.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 中缀省略纯函数契约(V27 M1-T1):路径/命令「保头掐腰」单一事实源。
 */
class MiddleEllipsisTest {

    @Test
    fun shortTextReturnedAsIs() {
        assertEquals("config.json", MiddleEllipsis.ellipsize("config.json", 32))
    }

    @Test
    fun exactBudgetReturnedAsIs() {
        assertEquals("abcdefgh", MiddleEllipsis.ellipsize("abcdefgh", 8))
    }

    @Test
    fun longTextKeepsHeadAndTailBalanced() {
        val result = MiddleEllipsis.ellipsize("abcdefghijklmnopqrstuvwxyz", 10)
        // keep = 9 → head 5 + tail 4,结果长度恒等于预算
        assertEquals(10, result.length)
        assertEquals("abcde…wxyz", result)
    }

    @Test
    fun budgetThreeKeepsOneCharEachSide() {
        assertEquals("a…z", MiddleEllipsis.ellipsize("abcdefghijklmnopqrstuvwxyz", 3))
    }

    @Test
    fun budgetTwoYieldsEllipsisOnly() {
        assertEquals(MiddleEllipsis.ELLIPSIS, MiddleEllipsis.ellipsize("abcdef", 2))
        assertEquals(MiddleEllipsis.ELLIPSIS, MiddleEllipsis.ellipsize("abcdef", 1))
    }

    @Test
    fun nonPositiveBudgetYieldsEmpty() {
        assertEquals("", MiddleEllipsis.ellipsize("abcdef", 0))
        assertEquals("", MiddleEllipsis.ellipsize("abcdef", -3))
    }

    @Test
    fun emptyTextStaysEmpty() {
        assertEquals("", MiddleEllipsis.ellipsize("", 16))
    }
}
