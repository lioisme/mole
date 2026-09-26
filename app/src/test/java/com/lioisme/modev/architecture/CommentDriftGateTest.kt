package com.lioisme.modev.architecture

import java.io.File
import java.util.regex.Pattern
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 注释漂移门禁:注释只允许点名**当前存在的东西**。
 *
 * 为什么值得钉住:本仓两类真事故都藏在注释里,而所有单测当时照常绿 ——
 *  1. 退役一个资源键或枚举项时,只有注释还在引用它(读者按注释去找,找不到,
 *     或更糟:照着注释把它「补回来」);
 *  2. 删键时漏 grep androidTest 的引用面,`R.string.kind_webview` 这类引用
 *     一路活到编译才炸。代码侧的引用编译器会管,**注释侧的引用没有任何人会管**。
 *
 * 四条判据:
 *  - 注释里点名的 `R.string.*` / `R.plurals.*` 必须在资源里存在;
 *  - 注释里点名的 `Xxx.kt` 文件名必须在工作树里存在;
 *  - 不允许 TODO / FIXME / HACK(待办属于议题,不属于源码;占位符形态不计);
 *  - 版本簿记(`V37` 一类内部任务标记)总量只降不升 —— 见 [VERSION_MARKER_CEILING]。
 *
 * 明确豁免:含「退役 / 已删除 / 禁止复活 / 勿再 / 曾」等字的行,其点名对象**本就是历史**,
 * 那是这类注释的正当语义(告诉后人别把它补回来),不计为漂移。
 *
 * 纯 JVM 文本扫描,形态沿用 `HardcodedStringConventionTest`。
 */
class CommentDriftGateTest {

    private val lineComment = Pattern.compile("//[^\\n]*")
    private val stringKeyRef = Pattern.compile("""R\.(string|plurals)\.([a-z0-9_]+)""")
    private val fileNameRef = Pattern.compile("(?<![*\\w])([A-Za-z][A-Za-z0-9_]*)\\.kt")
    private val versionMarker = Pattern.compile("\\bV\\d{1,3}\\b")
    private val todoMarker = Pattern.compile("\\b(TODO|FIXME|HACK)\\b")
    private val historicalHint = Pattern.compile("退役|已删除|已整体删除|禁止复活|勿再|曾经|曾为|原为|历史")

    @Test
    fun everyResourceKeyNamedInComments_stillExists() {
        val root = locateProjectRoot()
        val declared = declaredResourceNames(File(root, "app/src/main/res/values/strings.xml"))
        assertTrue("未能从 strings.xml 解析出任何资源名(解析式失效,门禁会恒绿在空集上)", declared.isNotEmpty())

        val violations = commentLines(root).flatMap { (file, line, text) ->
            val m = stringKeyRef.matcher(text)
            val hits = mutableListOf<String>()
            while (m.find()) {
                val name = m.group(2)!!
                if (!declared.contains(name) && !historicalHint.matcher(text).find()) {
                    hits += "R.${m.group(1)!!}.$name"
                }
            }
            hits.map { "$file:${line + 1} 引用了不存在的资源 $it" }
        }.toList()

        assertTrue("注释点名了已不存在的资源键:\n" + violations.joinToString("\n"), violations.isEmpty())
    }

    @Test
    fun everyFileNameNamedInComments_stillExists() {
        val root = locateProjectRoot()
        val present = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .map { it.name }
            .toSet()
        assertTrue("工作树里没有 kt 文件(遍历失效)", present.isNotEmpty())

        val missing = mutableSetOf<String>()
        for ((_, _, text) in commentLines(root)) {
            if (historicalHint.matcher(text).find()) continue
            val m = fileNameRef.matcher(text)
            while (m.find()) {
                val name = m.group(1)!! + ".kt"
                if (!present.contains(name)) missing += name
            }
        }
        assertTrue("注释点名了不存在的源文件:$missing", missing.isEmpty())
    }

    @Test
    fun noTodoMarkersInSource() {
        val root = locateProjectRoot()
        val hits = allSources(root).flatMap { file ->
            file.readLines().mapIndexedNotNull { i, l -> if (todoMarker.matcher(l).find()) "${file.relativeTo(root)}:${i + 1}" else null }
        }.toList()
        assertTrue("源码里出现待办标记(请开议题,不要埋在代码里):\n" + hits.joinToString("\n"), hits.isEmpty())
    }

    /**
     * 版本标记棘轮:注释里的 `V37` / `V45 T3.2` / `V49 WP-D1` 一类内部任务编号,
     * 对圈外读者不可解析,且每接管/每退役一批就过期一次。**只允许下降**。
     * 清理一批后把上限改小;任何使总数上升的改动都判红。
     */
    @Test
    fun versionMarkersInComments_neverIncrease() {
        // 实测基线(清理后)。只许改小:每剥掉一批就把它降到新值。
        val ceiling = 1742
        val root = locateProjectRoot()
        val total = commentLines(root).sumOf { (_, _, text) ->
            val m = versionMarker.matcher(text)
            var n = 0
            while (m.find()) n++
            n
        }
        assertTrue(
            "注释里的版本簿记从上限 $ceiling 涨到了 $total:新代码不要再往注释里写任务编号," +
                "要留痕就写进 docs/ 或提交信息。若确属清理后的新基线,请把 ceiling 改小(不是改大)。",
            total <= ceiling,
        )
    }

    // ------------------------------------------------------------------ 取材

    /** (相对文件, 行号 0 基, 注释文本):整行注释,或代码行的行尾注释部分。 */
    private fun commentLines(root: File): List<Triple<String, Int, String>> {
        val out = mutableListOf<Triple<String, Int, String>>()
        for (file in allSources(root)) {
            val rel = file.relativeTo(root).path.replace('\\', '/')
            for ((i, raw) in file.readLines().withIndex()) {
                val trimmed = raw.trim()
                val whole = trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")
                if (whole) {
                    out += Triple(rel, i, raw)
                } else {
                    val t = lineComment.matcher(raw)
                    while (t.find()) out += Triple(rel, i, t.group())
                }
            }
        }
        return out
    }

    private fun allSources(root: File): List<File> =
        listOf(
            File(root, "app/src/main/java"),
            File(root, "app/src/test/java"),
            File(root, "app/src/androidTest/java"),
            File(root, "testtarget/src/main/java"),
        ).filter { it.isDirectory }.flatMap { dir ->
            dir.walkTopDown().filter { it.isFile && it.extension == "kt" && it.name != "CommentDriftGateTest.kt" }.toList()
        }

    private fun declaredResourceNames(strings: File): Set<String> {
        if (!strings.isFile) return emptySet()
        val m = Pattern.compile("<(string|plurals) name=\"([a-zA-Z0-9_]+)\"")
        val text = strings.readText()
        val matcher = m.matcher(text)
        val out = mutableSetOf<String>()
        while (matcher.find()) out += matcher.group(2)!!
        return out
    }

    private fun locateProjectRoot(): File {
        val userDir = System.getProperty("user.dir") ?: error("user.dir unavailable")
        return listOf(File(userDir), File(userDir, ".."))
            .firstOrNull { File(it, "app/src/main/res/values/strings.xml").isFile }
            ?.getAbsoluteFile()
            ?: error("未定位到项目根(user.dir=$userDir 下没有 app/src/main/res/values/strings.xml)")
    }
}
