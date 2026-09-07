package com.lioisme.modev.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 令牌页掩码函数契约(V40 T6):前 4 + 6 圆点 + 后 4 摘要形态;
 * 短值(≤8)全掩不泄露结构;空串零输出。
 */
class TokenMaskValueTest {

    @Test
    fun longValue_masksMiddleKeepsHeadAndTail() {
        val masked = maskValue("0123456789abcdef0123456789abcdef")
        assertEquals("0123" + "••••••" + "cdef", masked)
    }

    @Test
    fun shortValue_fullyMasked() {
        assertEquals("••••••", maskValue("abcdef"))
        assertEquals("••", maskValue("ab"))
        assertEquals("", maskValue(""))
    }

    @Test
    fun maskedValue_neverContainsFullSecret() {
        val secret = "0123456789abcdef0123456789abcdef"
        val masked = maskValue(secret)
        assertTrue("掩码不得包含全值中段", !masked.contains("5678"))
        assertTrue("掩码不得等于全值", masked != secret)
    }
}
