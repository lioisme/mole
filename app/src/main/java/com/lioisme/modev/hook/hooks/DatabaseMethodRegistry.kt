package com.lioisme.modev.hook.hooks

import java.lang.reflect.Method

/**
 * 数据库门面方法注册表(ADR-8,T6):
 * 按方法名枚举注册(参数任意),一次方法扫描覆盖全部重载(含继承的公开实现,
 * 如 Robolectric 下 SQLiteCursor.moveToFirst 继承自 AbstractCursor),
 * 规避 [com.lioisme.modev.hook.MethodFinder] 精确签名查找对多重重载的不可枚举问题。
 * 注册失败静默跳过(engine 既有语义),任一重载缺失不影响其余。
 */
class DatabaseMethodRegistry(clazz: Class<*>) {

    /** 公开方法全集(含继承;重载去重后最派生声明仅出现一次)。 */
    private val byName: Map<String, List<Method>> =
        runCatching { clazz.methods }
            .getOrElse { emptyArray() }
            .groupBy { it.name }

    /** 按方法名取全部重载(无匹配返回空列表,调用方静默跳过)。 */
    fun named(name: String): List<Method> = byName[name].orEmpty()

    /** 任一名称命中即返回全部命中重载(用于「同语义多入口」)。 */
    fun namedAny(vararg names: String): List<Method> =
        names.flatMap { byName[it].orEmpty() }.distinctBy { it.name + it.parameterTypes.contentToString() }

    companion object {

        /** 解析系统门面类并构造注册表;失败返回 null。
         *  优先 boot classloader(注入进程直接可见),Robolectric/个别 ROM 兜底当前加载器。 */
        fun forName(className: String): DatabaseMethodRegistry? =
            runCatching {
                val clazz = try {
                    Class.forName(className, false, null)
                } catch (e: ClassNotFoundException) {
                    Class.forName(className)
                }
                DatabaseMethodRegistry(clazz)
            }.getOrNull()
    }
}
