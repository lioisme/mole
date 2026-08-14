package com.lioisme.modev.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StackTraceFilterTest {

    private fun element(className: String, method: String): StackTraceElement =
        StackTraceElement(className, method, "$className.java", 1)

    @Test
    fun filter_removes_ignored_prefixes() {
        val frames = arrayOf(
            element("java.lang.Thread", "getStackTrace"),
            element("com.lioisme.modev.hook.BaseHooker", "emit"),
            element("io.github.libxposed.api.XposedInterface", "proceed"),
            element("javax.crypto.Cipher", "doFinal"),
            element("com.example.app.CryptoHelper", "sign"),
            element("com.example.app.MainActivity", "onCreate"),
        )
        val result = StackTraceFilter.filter(frames)
        assertEquals(2, result.size)
        assertTrue(result[0].startsWith("com.example.app.CryptoHelper"))
        assertTrue(result[1].startsWith("com.example.app.MainActivity"))
    }

    @Test
    fun filter_respects_max_depth() {
        val frames = (1..10).map { element("com.example.app.Frame$it", "run") }.toTypedArray()
        val result = StackTraceFilter.filter(frames, maxDepth = 3)
        assertEquals(3, result.size)
        assertTrue(result.last().startsWith("com.example.app.Frame3"))
    }

    @Test
    fun filter_custom_prefixes() {
        val frames = arrayOf(
            element("com.example.lib.Internals", "x"),
            element("com.example.app.Main", "y"),
        )
        val result = StackTraceFilter.filter(frames, ignoredPrefixes = listOf("com.example.lib"))
        assertEquals(1, result.size)
        assertTrue(result[0].startsWith("com.example.app.Main"))
    }

    @Test
    fun filter_empty() {
        assertEquals(0, StackTraceFilter.filter(arrayOf()).size)
    }
}