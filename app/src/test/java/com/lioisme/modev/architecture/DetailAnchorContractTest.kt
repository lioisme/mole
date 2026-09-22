package com.lioisme.modev.architecture

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 可驱动锚点契约(V45 T3.7,纯 JVM 源码扫描):详情页 / 代码组 / 筛选菜单 / 监控页入口行
 * 的 testTag 集合是**插桩矩阵的唯一驱动面**(TASK-V45-ACCEPTANCE §5)。
 *
 * 为什么要门禁:这些锚点缺失时插桩不会编译失败,只会退化成「按文本反查节点」——
 * 文本随四语资源与格式化变,反查在 zh/en 设备上结果不同,是设备轮大面积假失败的常见成因。
 * 本门禁把「锚点在位」变成秒级 JVM 断言。
 */
class DetailAnchorContractTest {

    private val sourceRoot = File(System.getProperty("user.dir"), "src/main/java/com/lioisme/modev")

    private fun source(relative: String): String {
        val file = File(sourceRoot, relative)
        assertTrue("源文件缺失: ${file.path}", file.isFile)
        return file.readText()
    }

    private fun assertTags(file: String, vararg tags: String) {
        val text = source(file)
        val missing = tags.filterNot { it in text }
        assertTrue("$file 缺锚点: $missing", missing.isEmpty())
    }

    @Test
    fun detail_top_bar_and_retry_are_tagged() {
        assertTags(
            "ui/detail/RecordDetailScreen.kt",
            "detail-prev",
            "detail-next",
            "detail-more",
            "detail-export",
            "detail-retry",
        )
    }

    /** 代码组:标签行 + 逐标签页 + 逐块复制/全屏(多块页面必须可按块寻址)。 */
    @Test
    fun code_group_anchors_are_tagged_per_block_and_per_tab() {
        assertTags(
            "ui/detail/markdown/RecordMarkdownContent.kt",
            "detail-code-tabs",
            "detail-code-tab-",
            "-copy\"",
            "-fullscreen\"",
            "md-code-block",
        )
    }

    /** 目录行与筛选菜单(AGENTS #21 D2.1:页内导航必须原生可点击 + 带 tag)。 */
    @Test
    fun toc_and_filter_menu_are_tagged() {
        assertTags("ui/detail/markdown/RecordTocContent.kt", "\"toc-")
        assertTags(
            "ui/components/KindFilterMenu.kt",
            "records-filter",
            "kind-filter-all",
            "kind-filter-",
        )
    }

    /**
     * 监控页记录入口行同构(V45 T3.7 收口项:算法/数据库两行曾漏 tag):
     * 规则 = **每个 `EntryRow(` 调用必须自带 `monitor-entry-` tag**,而不是「至少有几个」。
     */
    @Test
    fun every_monitor_entry_row_carries_its_own_tag() {
        val text = source("ui/apps/AppMonitorScreen.kt")
        // 排除 `private fun EntryRow(` 声明本身(定长负向后查)
        val rows = Regex("""(?<!fun )EntryRow\(""").findAll(text).count()
        val tagged = Regex("""testTag\("monitor-entry-""").findAll(text).count()
        assertEquals("每个入口行都必须有独立 tag(算法/数据库两行曾缺)", rows, tagged)
        assertTrue("入口行数量异常: $rows", rows >= 10)
    }

    /** 监听/功能两页的行内开关 tag(AGENTS #16 契约:testTag 是插桩唯一寻址面)。 */
    @Test
    fun per_app_setting_rows_are_tagged() {
        assertTags("ui/apps/PerAppListenSettingsScreen.kt", "listen-row-master-", "listen-reset")
        assertTags("ui/apps/PerAppFeatureSettingsScreen.kt", "feature-reset")
    }
}
