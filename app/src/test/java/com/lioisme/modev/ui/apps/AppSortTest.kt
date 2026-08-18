package com.lioisme.modev.ui.apps

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AppSort 排序契约(2026-08-17 V7 社区规范;V11 S2 投影收窄):
 * - 中文按拼音(Collator zh,PRIMARY 强度:忽略大小写/声调差异);
 * - 名称完全相等(同 base 字母)回退包名比较,保证全序稳定;
 * - 比较器按调用时 Locale 现建,语言切换无需重建缓存;
 * - 置顶 = 稳定分区(O(n) 无比较器,组内保持名称序)。
 */
class AppSortTest {

    private fun row(label: String, packageName: String = "com.test.$label", scoped: Boolean = false) =
        TestRow(label = label, packageName = packageName, scoped = scoped)

    private data class TestRow(val label: String, val packageName: String, val scoped: Boolean)

    private fun byName(locale: Locale) =
        AppSort.nameComparator<TestRow>(locale, label = { it.label }, key = { it.packageName })

    @Test
    fun `chinese labels sort by pinyin with zh locale`() {
        val input = listOf(row("滴滴出行"), row("阿里巴巴"), row("百度网盘"))
        val sorted = input.sortedWith(byName(Locale.SIMPLIFIED_CHINESE))
        assertEquals(listOf("阿里巴巴", "百度网盘", "滴滴出行"), sorted.map { it.label })
    }

    @Test
    fun `english labels sort case-insensitively`() {
        // PRIMARY 强度忽略大小写:Google 与 google 视为同 key → 回退包名定序
        val sorted =
            listOf(row("google", "com.b.google"), row("Google", "com.a.google"))
                .sortedWith(byName(Locale.US))
        assertEquals(listOf("com.a.google", "com.b.google"), sorted.map { it.packageName })
    }

    @Test
    fun `tie falls back to packageName for total order`() {
        val sorted =
            listOf(row("Photos", "com.z"), row("Photos", "com.a"))
                .sortedWith(byName(Locale.US))
        assertEquals(listOf("com.a", "com.z"), sorted.map { it.packageName })
    }

    @Test
    fun `default locale comparator works with ascii labels`() {
        val sorted =
            listOf(row("Zebra"), row("Alpha"), row("Mango"))
                .sortedWith(byName(Locale.getDefault()))
        assertEquals(listOf("Alpha", "Mango", "Zebra"), sorted.map { it.label })
    }

    @Test
    fun `name sort is stable for identical keys`() {
        val input = listOf(row("Same"), row("Same"))
        val sorted = input.sortedWith(byName(Locale.US))
        assertTrue(sorted[0] === input[0])
        assertTrue(sorted[1] === input[1])
    }

    @Test
    fun `pin scoped partitions stably without comparator`() {
        val rows = listOf(row("Alpha"), row("Beta", scoped = true), row("Gamma"), row("Delta", scoped = true))
        val pinned = AppSort.pinScoped(rows) { it.scoped }
        assertEquals(listOf("Beta", "Delta", "Alpha", "Gamma"), pinned.map { it.label })
        // 组内顺序 = 原序(稳定,无比较器参与)
        assertEquals(listOf("Beta", "Delta"), pinned.take(2).map { it.label })
        assertEquals(listOf("Alpha", "Gamma"), pinned.drop(2).map { it.label })
    }

    @Test
    fun `pin scoped with no scoped rows is identity`() {
        val rows = listOf(row("A"), row("B"))
        assertEquals(rows, AppSort.pinScoped(rows) { it.scoped })
    }
}