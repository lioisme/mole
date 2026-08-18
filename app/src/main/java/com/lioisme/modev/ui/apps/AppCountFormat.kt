package com.lioisme.modev.ui.apps

/**
 * 记录数紧凑格式化(S3 修复,2026-08-17):纯函数,与资源格式器解耦——
 * UI 传入本地化格式器(万/千),测试传入等价 lambda 即可断言。
 * 语义:≥10000 走万级格式器,≥1000 走千级格式器,以下原样;
 * 缩放(zh 万=÷10000 / 千=÷1000,en 恒 K=÷1000)由 UI 按语言在格式器内完成。
 */
object AppCountFormat {

    fun compact(
        count: Long,
        formatWan: (Long) -> String,
        formatQian: (Long) -> String,
    ): String = when {
        count >= 10_000 -> formatWan(count)
        count >= 1_000 -> formatQian(count)
        else -> count.toString()
    }
}