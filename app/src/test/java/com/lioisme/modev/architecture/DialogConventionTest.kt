package com.lioisme.modev.architecture

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V39 R7 对话框/底部弹层统一门禁(源码扫描,镜像 [InterventionGateConventionTest]):
 *
 *  R1:ui 子树内 `AlertDialog(` 仅允许出现在 `ui/components/ModevDialogs.kt`
 *     (统一确认对话框唯一出口,禁逐屏手写复制模板);
 *  R2:`ModevDialogs.kt` 自身禁止 `icon =` 参数(V39 验收:对话框不带图标);
 *  R3:ui 子树内 `ModalBottomSheet(` 直构造仅允许 `ui/components/ModevSheetScaffold.kt`
 *     (hide 动画收口 / surfaceContainerHigh / imePadding 契约由脚手架单点保证)。
 *
 * 豁免:`ui/detail/markdown/FullscreenTextDialog.kt` 使用 `Dialog(`(全屏只读查看器,
 * AGENTS 强约束 #3 固化形态),不在本门禁扫描面(非 AlertDialog/ModalBottomSheet)。
 */
class DialogConventionTest {

    private val uiRoot = "app/src/main/java/com/lioisme/modev/ui"

    private fun locateProjectRoot(): File {
        val userDir = System.getProperty("user.dir") ?: error("user.dir unavailable")
        val candidates = listOf(
            File(userDir),
            File(userDir, ".."),
        )
        return candidates.firstOrNull {
            File(it, uiRoot).isDirectory
        } ?: error("project root not located from $userDir")
    }

    private fun uiSources(): List<File> =
        File(locateProjectRoot(), uiRoot).walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()

    @Test
    fun alertDialogOnlyInSharedDialogComponent() {
        val allowed = "ModevDialogs.kt"
        val violations = uiSources()
            .filter { it.name != allowed }
            .filter { file -> Regex("""\bAlertDialog\(""").containsMatchIn(file.readText()) }
            .map { it.name }
        assertTrue(
            "AlertDialog must only be constructed in $allowed (use ModevConfirmDialog):\n$violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun sharedConfirmDialogCarriesNoIcon() {
        val source = File(locateProjectRoot(), "$uiRoot/components/ModevDialogs.kt").readText()
        assertTrue(
            "ModevConfirmDialog must not render an icon (V39 R7: dialogs carry no icons)",
            !Regex("""\bicon\s*=""").containsMatchIn(source),
        )
    }

    @Test
    fun modalBottomSheetOnlyInSheetScaffold() {
        val allowed = "ModevSheetScaffold.kt"
        val violations = uiSources()
            .filter { it.name != allowed }
            .filter { file -> Regex("""\bModalBottomSheet\(""").containsMatchIn(file.readText()) }
            .map { it.name }
        assertTrue(
            "ModalBottomSheet must only be constructed in $allowed (use ModevSheet/ModevInputSheet/ModevChoiceSheet):\n$violations",
            violations.isEmpty(),
        )
    }
}
