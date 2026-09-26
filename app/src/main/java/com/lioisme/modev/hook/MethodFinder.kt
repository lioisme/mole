package com.lioisme.modev.hook

import java.lang.reflect.Constructor
import java.lang.reflect.Method

/**
 * 反射定位系统加密门面类方法/构造器,结果缓存。
 * 目标类均由 boot classloader 加载,直接 [Class.forName] 即可。
 *
 * 线程契约:仅安装线程串行访问;缓存仍用 ConcurrentHashMap——
 * 契约被破坏时退化为弱一致读而非 HashMap 结构性损坏(坏表/死循环)。
 */
object MethodFinder {

    private val classCache = java.util.concurrent.ConcurrentHashMap<String, Class<*>>()
    private val methodCache = java.util.concurrent.ConcurrentHashMap<String, Method>()
    private val ctorCache = java.util.concurrent.ConcurrentHashMap<String, Constructor<*>>()
    /** 缺席类负缓存(并发集;不可用 null 值表达,见 [clazz])。 */
    private val absentClasses = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /**
     * 按类名 + 方法名 + 参数类型精确定位方法;不存在时返回 null(调用方静默跳过)。
     * 参数类型允许可空(如 [Int::class.javaPrimitiveType] 的平台可空标注),null 自动忽略。
     */
    fun find(className: String, name: String, vararg paramTypes: Class<*>?): Method? {
        val types = paramTypes.filterNotNull().toTypedArray()
        val key = "$className#$name#${types.joinToString(",") { it.name }}"
        methodCache[key]?.let { return it }
        val clazz = clazz(className) ?: return null
        val method = runCatching { clazz.getDeclaredMethod(name, *types) }.getOrNull()
        if (method != null) {
            methodCache[key] = method
        }
        return method
    }

    /**
     * 按类名 + 参数类型精确定位构造器;不存在时返回 null。
     */
    fun findCtor(className: String, vararg paramTypes: Class<*>?): Constructor<*>? {
        val types = paramTypes.filterNotNull().toTypedArray()
        val key = "$className#<init>#${types.joinToString(",") { it.name }}"
        ctorCache[key]?.let { return it }
        val clazz = clazz(className) ?: return null
        val ctor = runCatching { clazz.getDeclaredConstructor(*types) }.getOrNull()
        if (ctor != null) {
            ctorCache[key] = ctor
        }
        return ctor
    }

    private fun clazz(className: String): Class<*>? {
        classCache[className]?.let { return it }
        if (className in absentClasses) return null
        val clazz = runCatching { Class.forName(className, false, null) }.getOrNull()
        // 缺席类必须走独立负缓存:ConcurrentHashMap 不接受 null 值,旧形态在此直接抛
        // NPE(而非「静默跳过」),把一个可选类缺失放大成整探针装配失败(调用方
        // Hooker.install 无逐类兜底,异常上抛到 HookInstaller 的 per-probe catch)。
        if (clazz == null) {
            absentClasses += className
        } else {
            classCache[className] = clazz
        }
        return clazz
    }
}
