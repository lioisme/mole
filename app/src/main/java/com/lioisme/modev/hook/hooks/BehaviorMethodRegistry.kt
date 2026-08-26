package com.lioisme.modev.hook.hooks

import java.lang.reflect.Method

/**
 * 行为监听方法注册表(V27,镜像 [DatabaseMethodRegistry]):
 * 按方法名枚举注册(参数任意),一次扫描覆盖全部公开重载;boot classloader 优先,
 * 失败回退当前加载器;类缺失(API < 26 的 java.nio.file.Files 等)返回 null →
 * 调用方静默零安装。
 */
class BehaviorMethodRegistry private constructor(
    clazz: Class<*>,
) {

    /** 公开方法全集(含继承;重载按「名称+签名」去重)。 */
    private val byName: Map<String, List<Method>> =
        runCatching { clazz.methods }
            .getOrElse { emptyArray() }
            .groupBy { it.name }

    /** 按方法名取全部重载(无匹配返回空列表)。 */
    fun named(name: String): List<Method> = byName[name].orEmpty()

    /** 任一名称命中即返回全部命中重载(无匹配返回空列表,调用方静默跳过)。 */
    fun namedAny(vararg names: String): List<Method> =
        names.flatMap { byName[it].orEmpty() }
            .distinctBy { it.name + it.parameterTypes.contentToString() }

    companion object {

        /** 解析系统类并构造注册表;失败/类缺失返回 null。 */
        fun forName(className: String): BehaviorMethodRegistry? =
            runCatching {
                val clazz = try {
                    Class.forName(className, false, null)
                } catch (e: ClassNotFoundException) {
                    Class.forName(className)
                }
                BehaviorMethodRegistry(clazz)
            }.getOrNull()
    }
}
