package com.lioisme.modev.ui.apps

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AppSort 排序契约(2026-08-17 V7,社区规范):
 * - 中文按拼音(Collator zh,PRIMARY 强度:忽略大小写/声调差异);
 * - 名称完全相等(同 base 字母)回退包名比较,保证全序稳定;
 * - 比较器按调用时 Locale 现建,语言切换无需重建缓存。
 */
class AppSortTest {

    private fun row(label: String, packageName: String = "com.test.$label") =
        AppsViewModel.AppRow(
            packageName = packageName,
            label = label,
            system = false,
            enabled = true,
            scoped = false,
            connected = false,
            recordCount = 0L,
        )

    @Test
    fun `chinese labels sort by pinyin with zh locale`() {
        val input = listOf(row("滴滴出行"), row("阿里巴巴"), row("百度网盘"))
        val sorted = input.sortedWith(AppSort.nameComparator(Locale.SIMPLIFIED_CHINESE))
        assertEquals(listOf("阿里巴巴", "百度网盘", "滴滴出行"), sorted.map { it.label })
    }

    @Test
    fun `english labels sort case-insensitively`() {
        // PRIMARY 强度忽略大小写:Google 与 google 视为同 key → 回退包名定序
        val sorted =
            listOf(row("google", "com.b.google"), row("Google", "com.a.google"))
                .sortedWith(AppSort.nameComparator(Locale.US))
        assertEquals(listOf("com.a.google", "com.b.google"), sorted.map { it.packageName })
    }

    @Test
    fun `tie falls back to packageName for total order`() {
        val sorted =
            listOf(row("Photos", "com.z"), row("Photos", "com.a"))
                .sortedWith(AppSort.nameComparator(Locale.US))
        assertEquals(listOf("com.a", "com.z"), sorted.map { it.packageName })
    }

    @Test
    fun `default locale comparator works with ascii labels`() {
        val sorted =
            listOf(row("Zebra"), row("Alpha"), row("Mango"))
                .sortedWith(AppSort.nameComparator())
        assertEquals(listOf("Alpha", "Mango", "Zebra"), sorted.map { it.label })
    }

    @Test
    fun `name sort is stable for identical keys`() {
        val input = listOf(row("Same"), row("Same"))
        val sorted = input.sortedWith(AppSort.nameComparator(Locale.US))
        assertTrue(sorted[0] === input[0])
        assertTrue(sorted[1] === input[1])
    }
}