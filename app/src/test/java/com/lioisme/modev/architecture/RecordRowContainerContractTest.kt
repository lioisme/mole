package com.lioisme.modev.architecture

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 记录行容器契约门禁(V46 R1,源码扫描,与 [DialogConventionTest] 同族):
 *
 *  C1 禁再使用 ListItem `trailingContent =` 槽 —— 尾栏存在即永久预留正文宽度,
 *     就是「底部参数行右侧明明还有位置却被提前截断」的根因形态;
 *  C2 容器必须显式圆角 + 显式容器色,且 `ListItem` 转透明
 *     (否则圆角被内部 Surface 方角覆盖、波纹被不透明容器吞掉);
 *  C3 行容器半径一律取自 `MaterialTheme.shapes.*`,禁魔法数
 *     (Material3 1.4.0 的 `ListItem` 无 `shape` 形参,javap 实锤 —— 圆角只能在容器层做)。
 *
 * 与 `RecordRowLayoutTest` 的几何/结构断言互补:那边测所见,这边测禁复活。
 */
class RecordRowContainerContractTest {

    private val relative = "src/main/java/com/lioisme/modev/ui/components/RecordRow.kt"

    private val source: String by lazy {
        val userDir = System.getProperty("user.dir") ?: error("user.dir unavailable")
        val file = listOf(
            File(userDir, relative),
            File(userDir, "app/$relative"),
            File(userDir, "../app/$relative"),
        ).firstOrNull { it.isFile }
            ?: error("RecordRow.kt 未定位(user.dir=$userDir)")
        file.readText()
    }

    /**
     * 去注释但保留行结构(整块替换成空格而非删除):否则 KDoc 里描述的「退役形态」
     * 会被当实现命中(V45 门禁自我实锤过的假红/假绿双向坑)。
     */
    private val code: String by lazy {
        val out = StringBuilder(source)
        Regex("/\\*[\\s\\S]*?\\*/").findAll(source).forEach { m ->
            for (i in m.range) if (out[i] != '\n' && out[i] != '\r') out[i] = ' '
        }
        Regex("//[^\n]*").findAll(out.toString()).forEach { m ->
            for (i in m.range) if (out[i] != '\n' && out[i] != '\r') out[i] = ' '
        }
        out.toString()
    }

    @Test
    fun `C1 record row never uses the ListItem trailing slot`() {
        assertTrue(
            "RecordRow 禁止 trailingContent 槽(V46 R1:尾栏预留宽度 = 正文截断根因)",
            !Regex("""trailingContent\s*=""").containsMatchIn(code),
        )
    }

    @Test
    fun `C2 container is rounded with explicit background and transparent ListItem`() {
        assertTrue(
            "行容器必须从 shapes token 取圆角",
            Regex("""\.clip\(MaterialTheme\.shapes\.\w+\)""").containsMatchIn(code),
        )
        assertTrue(
            "行容器必须显式 background",
            Regex("""\.background\(MaterialTheme\.colorScheme\.surfaceContainerLow\)""").containsMatchIn(code),
        )
        assertTrue(
            "ListItem 必须转透明(容器色已由 Box 承载)",
            Regex("""ListItemDefaults\.colors\(containerColor = Color\.Transparent\)""").containsMatchIn(code),
        )
    }

    @Test
    fun `C3 identity line carries the trailing column`() {
        assertTrue(
            "身份行必须存在且带 record-row-identity 锚点(时间/徽章的唯一新居处)",
            Regex("""testTag\("record-row-identity"\)""").containsMatchIn(code),
        )
        assertTrue(
            "身份行必须同时承载时间与徽章(退役后唯一的右栏渲染处)",
            Regex("""IdentityLine\([\s\S]*?timestamp[\s\S]*?badges""").containsMatchIn(code),
        )
    }
}
