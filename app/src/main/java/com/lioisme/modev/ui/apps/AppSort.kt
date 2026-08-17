package com.lioisme.modev.ui.apps

import java.text.Collator
import java.util.Comparator
import java.util.Locale

/**
 * 应用名称排序(2026-08-17 V7,社区规范):
 * Locale Collator 比较应用名(中文按拼音),PRIMARY 强度忽略大小写/声调差异,
 * 名称同 key 回退包名保证全序稳定;比较器按调用时 Locale 现建,
 * 语言切换后无需重建缓存。终态排序唯一在 AppsViewModel(单一事实源)。
 */
object AppSort {

    fun nameComparator(locale: Locale = Locale.getDefault()): Comparator<AppsViewModel.AppRow> {
        val collator = Collator.getInstance(locale).apply { strength = Collator.PRIMARY }
        return Comparator { a, b ->
            collator.compare(a.label, b.label)
                .takeIf { it != 0 } ?: a.packageName.compareTo(b.packageName)
        }
    }
}