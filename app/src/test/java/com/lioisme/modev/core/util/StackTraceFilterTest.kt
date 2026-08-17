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

    @Test
    fun capture_deny_prefixes_returns_empty_when_hit() {
        val frames = arrayOf(
            element("com.google.gson.internal.ConstructorConstructor", "newDefaultConstructor"),
            element("com.example.app.Main", "onCreate"),
        )
        assertTrue(StackTraceFilter.isDenied(frames, listOf("com.google.gson")))
        // 全量 denylist 命中 → 捕获结果为空(emit 侧据此跳过整条记录);
        // capture 基于真实线程栈,测试线程栈含 org.junit 帧 → 命中 org.junit 前缀为空
        assertEquals(0, StackTraceFilter.capture(denyPrefixes = listOf("org.junit")).size)
        assertTrue(StackTraceFilter.capture(denyPrefixes = listOf("com.google.gson")).isNotEmpty())
    }

    @Test
    fun capture_deny_prefixes_unaffected_when_no_match() {
        val frames = arrayOf(
            element("com.example.app.Main", "onCreate"),
        )
        assertTrue(!StackTraceFilter.isDenied(frames, listOf("com.google.gson")))
    }

    @Test
    fun is_denied_short_circuits_on_empty_list() {
        assertTrue(!StackTraceFilter.isDenied(arrayOf(element("a.b", "c")), emptyList()))
    }
}