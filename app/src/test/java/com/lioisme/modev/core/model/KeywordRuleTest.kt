package com.lioisme.modev.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** V37 T2.1 关键词匹配规则纯函数门禁。 */
class KeywordRuleTest {

    @Test
    fun `contains matches case-insensitively`() {
        val rule = KeywordRule("root", KeywordMatchMode.CONTAINS)
        assertTrue(KeywordRule.matches(rule, "Device is ROOTED"))
        assertFalse(KeywordRule.matches(rule, "all good"))
    }

    @Test
    fun `equals requires full match ignoring case`() {
        val rule = KeywordRule("warning", KeywordMatchMode.EQUALS)
        assertTrue(KeywordRule.matches(rule, "Warning"))
        assertFalse(KeywordRule.matches(rule, "Warning: low battery"))
    }

    @Test
    fun `regex matches with ignore case`() {
        val rule = KeywordRule("det(e|a)ct", KeywordMatchMode.REGEX)
        assertTrue(KeywordRule.matches(rule, "DETECTING..."))
        assertTrue(KeywordRule.matches(rule, "detecting..."))
        assertFalse(KeywordRule.matches(rule, "nothing"))
    }

    @Test
    fun `invalid regex degrades to no match instead of throwing`() {
        val rule = KeywordRule("([unclosed", KeywordMatchMode.REGEX)
        assertFalse(KeywordRule.matches(rule, "anything"))
    }

    @Test
    fun `null or empty inputs never match`() {
        val rule = KeywordRule("x", KeywordMatchMode.CONTAINS)
        assertFalse(KeywordRule.matches(rule, null))
        assertFalse(KeywordRule.matches(rule, ""))
        assertFalse(KeywordRule.matches(KeywordRule("", KeywordMatchMode.CONTAINS), "text"))
    }
}
