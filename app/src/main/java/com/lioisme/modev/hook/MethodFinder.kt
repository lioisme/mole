package com.lioisme.modev.hook

import java.lang.reflect.Constructor
import java.lang.reflect.Method

/**
 * 反射定位系统加密门面类方法/构造器,结果缓存。
 * 目标类均由 boot classloader 加载,直接 [Class.forName] 即可。
 */
object MethodFinder {

    private val classCache = HashMap<String, Class<*>?>()
    private val methodCache = HashMap<String, Method>()
    private val ctorCache = HashMap<String, Constructor<*>>()

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
        val clazz = runCatching { Class.forName(className, false, null) }.getOrNull()
        classCache[className] = clazz
        return clazz
    }
}