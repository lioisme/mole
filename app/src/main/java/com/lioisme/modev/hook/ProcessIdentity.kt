package com.lioisme.modev.hook

import java.io.File

/**
 * 注入进程身份(V44 T2.6,审计 P13):目标进程名的**唯一读取点**。
 *
 * 背景:旧形态 9 个 Hooker 基类 + WebViewHooker 各自 `by lazy { File("/proc/self/cmdline")… }`
 * ——冷启动期约 50 个 Hooker 实例各自做一次文件读 + 字节解码(且 lazy 只在首次访问
 * 时触发,分散在装配与首条记录两条路径上)。注入侧恒位于目标进程内,
 * `/proc/self/cmdline` 就是目标进程名,进程内恒定不变 → 单例缓存一次即可。
 *
 * 失败降级 "unknown"(与旧行为一致,绝不因身份读取失败影响记录产出)。
 */
object ProcessIdentity {

    /** 目标进程名(形如 `pkg` 或 `pkg:sub`)。 */
    val processName: String by lazy {
        runCatching { File("/proc/self/cmdline").readBytes() }
            .getOrNull()
            ?.toString(Charsets.UTF_8)
            ?.substringBefore('\u0000')
            ?.takeIf { it.isNotBlank() }
            ?: "unknown"
    }
}
