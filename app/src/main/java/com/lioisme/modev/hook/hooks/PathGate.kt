package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.HookSwitch

/**
 * 行为监听路径降噪闸(V27):主体路径命中任一前缀即跳过该记录。
 *
 * 内置清单 = 系统框架/设备节点路径(全 App 高频低值,不可配置关闭)+
 * 模块自身基础设施(spool/seq 文件);用户清单([HookSwitch.ignorePathPrefixes])
 * 与内置清单叠加生效(denylist 整条跳过语义,对齐 [StackTraceFilter.isDenied])。
 */
object PathGate {

    /** 内置忽略路径前缀:系统区域(读/列目录噪声主源)。 */
    private val BUILTIN_PREFIXES =
        listOf(
            "/proc/",
            "/sys/",
            "/dev/",
            "/system/",
            "/apex/",
            "/vendor/",
            "/product/",
            "/system_ext/",
            "/odm/",
        )

    /** 内置忽略子串:模块自身 WAL/序号文件(自写自录消除)。 */
    private val BUILTIN_CONTAINS = listOf("/modev_spool")

    private val BUILTIN_BASE_NAMES = listOf("modev_seq_")

    /** 判定:builtin 或用户任一前缀/子串/基名命中 → 忽略。path 为空不忽略。 */
    fun isIgnored(path: String?, userPrefixes: List<String>): Boolean {
        if (path.isNullOrEmpty()) return false
        for (prefix in BUILTIN_PREFIXES) {
            if (path.startsWith(prefix)) return true
        }
        for (needle in BUILTIN_CONTAINS) {
            if (path.contains(needle)) return true
        }
        val baseName = path.substringAfterLast('/')
        for (stem in BUILTIN_BASE_NAMES) {
            if (baseName.startsWith(stem)) return true
        }
        for (prefix in userPrefixes) {
            if (prefix.isNotEmpty() && path.startsWith(prefix)) return true
        }
        return false
    }
}
