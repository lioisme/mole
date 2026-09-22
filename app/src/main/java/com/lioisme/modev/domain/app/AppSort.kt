package com.lioisme.modev.domain.app

import java.text.Collator
import java.util.Comparator
import java.util.Locale

/**
 * 应用列表排序核心(V44 Q3 上移 domain:UI AppsViewModel 与 MCP apps_list 双端
 * 同一排序事实源——旧形态排序仅存 UI VM,MCP 返回扫描原始序,同「应用列表查询」
 * 两侧结果序漂移)。
 *
 * 纯函数、零 Android 依赖(domain 解耦契约):
 * - [nameComparator]:Locale Collator 比较应用名(中文按拼音),PRIMARY 强度忽略
 *   大小写/声调差异,名称同 key 回退包名保证全序稳定;比较器按调用时 Locale 现建,
 *   语言切换后无需重建;
 * - [pinScoped]:已作用域置顶稳定分区——置顶组前置,组内保持既有名称序,
 *   O(n) 无比较器,不改变全序稳定性;
 * - 排序阶段仅依赖 label/packageName,与计数、连接态解耦(V11 S2)。
 */
object AppSort {

    /**
     * 名称比较器:label 投影取应用名,key 投影取包名(回退全序键)。
     * 与元素类型解耦:列表流水线可在轻量排序行上排序,UI 行不受限。
     */
    fun <T> nameComparator(
        locale: Locale = Locale.getDefault(),
        label: (T) -> String,
        key: (T) -> String,
    ): Comparator<T> {
        val collator = Collator.getInstance(locale).apply { strength = Collator.PRIMARY }
        return Comparator { a, b ->
            collator.compare(label(a), label(b))
                .takeIf { it != 0 } ?: key(a).compareTo(key(b))
        }
    }

    /**
     * 已作用域置顶(S2):稳定分区——置顶组前置,组内保持既有名称序。
     * O(n) 无比较器,不改变全序稳定性。
     */
    fun <T> pinScoped(rows: List<T>, scoped: (T) -> Boolean): List<T> {
        val (pinned, rest) = rows.partition { scoped(it) }
        return pinned + rest
    }
}
