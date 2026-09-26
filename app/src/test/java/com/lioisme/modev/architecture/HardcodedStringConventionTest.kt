package com.lioisme.modev.architecture

import java.io.File
import java.util.regex.Pattern
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 定向测试(硬编码文案,2026-08-18 V15):
 * ui 包 Kotlin 源码中禁止出现中文字符串字面量 —— 用户可见文本必须走
 * 资源(i18n),否则「切换语言后部分文本不翻译」。注释(含 KDoc)/导入/
 * 注解成员引用均被剔除;仅单行/三引号字符串字面量的内容参与判定。
 * 新增 UI 文案若绕过 resources,本测试即失败,须先补双语资源再引用。
 */
class HardcodedStringConventionTest {

    private val cjk = Pattern.compile("[\u4e00-\u9fff]")
    private val blockComment = Pattern.compile("/\\*[\\s\\S]*?\\*/")
    private val tripleQuoted = Pattern.compile("\"\"\"[\\s\\S]*?\"\"\"")
    private val lineComment = Pattern.compile("//.*$")
    private val stringLiteral = Pattern.compile("\"(?:[^\"\\\\]|\\\\.)*\"")

    @Test
    fun noHardcodedChineseInUiSourceStringLiterals() {
        val root = locateProjectRoot()
        val uiDir = File(root, "app/src/main/java/com/lioisme/modev/ui")
        assertTrue("ui source dir not found: $uiDir", uiDir.isDirectory)

        val violations = uiDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file -> hardcodedCjk(file).map { "${file.relativeTo(root)}:$it" } }
            .toList()

        assertTrue(
            "hardcoded Chinese string literals in ui package (must use resources):\n" +
                violations.joinToString("\n"),
            violations.isEmpty(),
        )
    }

    /**
     * 逐行判定(行号即定位):先剥块注释与三引号串(跨行,基于全文),
     * 再按行剥行注释并扫描单行字面量;含 CJK 者计为违规。
     */
    private fun hardcodedCjk(file: File): List<String> {
        val source = file.readText()
        val stripped = tripleQuoted.matcher(blockComment.matcher(source).replaceAll(" ")).replaceAll(" ")
        val out = mutableListOf<String>()
        for ((index, rawLine) in stripped.lineSequence().withIndex()) {
            val line = lineComment.matcher(rawLine).replaceAll("")
            val matcher = stringLiteral.matcher(line)
            while (matcher.find()) {
                val literal = matcher.group()
                if (cjk.matcher(literal).find()) {
                    out.add("L${index + 1} literal=$literal")
                }
            }
        }
        return out
    }

    /** 定位项目根(测试工作目录:项目根 / app 模块两种可能)。 */
    private fun locateProjectRoot(): File {
        val candidates = listOf(
            File(System.getProperty("user.dir")),
            File(System.getProperty("user.dir"), ".."),
        )
        return candidates.firstOrNull { File(it, "app/src/main/java/com/lioisme/modev/ui").isDirectory }
            ?: error("project root not located from ${System.getProperty("user.dir")}")
    }
}
