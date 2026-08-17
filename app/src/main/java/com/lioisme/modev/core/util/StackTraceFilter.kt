package com.lioisme.modev.core.util

/**
 * 调用堆栈采集与过滤。
 *
 * 过滤规则(按前缀剔除):
 *  - 模块自身帧(com.lioisme.modev)
 *  - Xposed 框架帧(io.github.libxposed / de.robv.android.xposed,兼容双框架)
 *  - 反射与 JVM 内部帧(java.lang.reflect / jdk.internal / sun.reflect / dalvik.system)
 *  - 被 Hook 的 JDK 门面类自身(避免记录自身递归帧)
 */
object StackTraceFilter {

    private val DEFAULT_IGNORED_PREFIXES = listOf(
        "com.lioisme.modev",
        "io.github.libxposed",
        "de.robv.android.xposed",
        "java.lang.reflect",
        "jdk.internal",
        "sun.reflect",
        "dalvik.system",
        "com.android.internal.os",
        "android.os",
        "java.lang.Thread",
        "javax.crypto.Cipher",
        "java.security.MessageDigest",
        "javax.crypto.Mac",
        "java.security.Signature",
        "javax.crypto.spec",
        "javax.crypto.KeyGenerator",
        "java.security.KeyPairGenerator",
        "java.security.KeyFactory",
        "javax.crypto.SecretKeyFactory",
        "android.util.Base64",
    )

    /**
     * 从调用栈中提取应用帧,返回"调用方在上"的顺序(与 [Thread.getStackTrace] 一致,从最内层开始)。
     *
     * @param denyPrefixes 调用栈 denylist:任一帧命中即整条跳过(调用方按需启用,默认空)
     */
    fun capture(
        maxDepth: Int = 24,
        ignoredPrefixes: List<String> = DEFAULT_IGNORED_PREFIXES,
        denyPrefixes: List<String> = emptyList(),
    ): List<String> {
        val elements = Thread.currentThread().stackTrace
        if (denyPrefixes.isNotEmpty() && isDenied(elements, denyPrefixes)) return emptyList()
        return filter(elements, maxDepth, ignoredPrefixes)
    }

    /** 调用栈中是否存在命中 denylist 前缀的帧(框架自触发噪声治理)。 */
    fun isDenied(elements: Array<StackTraceElement>, denyPrefixes: List<String>): Boolean {
        if (denyPrefixes.isEmpty()) return false
        for (element in elements) {
            val className = element.className
            for (prefix in denyPrefixes) {
                if (className.startsWith(prefix)) return true
            }
        }
        return false
    }

    /**
     * 从异常对象提取调用栈,过滤后返回帧描述列表。
     */
    fun filter(
        elements: Array<StackTraceElement>,
        maxDepth: Int = 24,
        ignoredPrefixes: List<String> = DEFAULT_IGNORED_PREFIXES,
    ): List<String> {
        val result = ArrayList<String>(minOf(maxDepth, elements.size))
        for (element in elements) {
            val className = element.className
            if (className.startsWith("java.lang.Thread")) continue
            var ignored = false
            for (prefix in ignoredPrefixes) {
                if (className.startsWith(prefix)) {
                    ignored = true
                    break
                }
            }
            if (ignored) continue
            result.add(element.toString())
            if (result.size >= maxDepth) break
        }
        return result
    }
}