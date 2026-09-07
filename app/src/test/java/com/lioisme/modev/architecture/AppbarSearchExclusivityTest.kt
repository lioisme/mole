package com.lioisme.modev.architecture

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V39 R5 Appbar 搜索排他门禁(源码扫描,镜像 [DialogConventionTest];V40 T5.1
 * 扫描标记随 CollapsedSearchInput 退役更新为标准 `SearchInputField(`):
 * 凡 Appbar 承载 SearchBar(`SearchInputField(` 宿主)的页面,
 * TopAppBar `actions =` 槽必须为空——页面动作一律收进搜索条
 * (actionSlot / filterSlot / leadingSlot),与 AppsScreen 样板对齐。
 *
 * 无搜索的页面(AppMonitorScreen 等)不受本门禁约束,Appbar actions 合法。
 */
class AppbarSearchExclusivityTest {

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

    @Test
    fun searchHostScreensHaveEmptyAppbarActions() {
        val violations = File(locateProjectRoot(), uiRoot).walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { file ->
                val source = file.readText()
                source.contains("SearchInputField(") &&
                    Regex("""\bactions\s*=\s*\{""").containsMatchIn(source)
            }
            .map { it.name }
            .toList()
        assertTrue(
            "screens hosting a SearchBar in the Appbar must keep TopAppBar actions empty " +
                "(move actions into the search bar slots):\n$violations",
            violations.isEmpty(),
        )
    }
}
