package com.lioisme.modev.architecture

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 分层依赖门禁(V45 T2.2/T2.3,需求 2「底层数据架构与方法层严格隔离」+ 需求 3
 * 「UI 与 MCP 同为一等公民、共同基于方法层」的类型级落地):
 *
 *  G1 兼容 typealias 层不得复活 —— 模型/契约上移 domain 后,`data.*` 里留
 *     `typealias X = domain...` 等于给「消费方点名实现包」留后门(曾正是 ui 持有
 *     data.mapping 依赖边的唯一理由)。
 *  G2 容器只暴露契约 —— `AppContainer` 的 public `val` 类型必须来自 `domain.*`
 *     (实现类型只允许 `internal`/`private`,即仅装配期可见);否则「这一屏用什么」
 *     又退化成全容器服务定位器(ISO-5 根因)。
 *  G3 消费方 import 面零实现 —— ui/mcp 子树出现 `com.lioisme.modev.data.` 即红
 *     (与 PackageBoundaryTest R1/R6 同口径,此处按 import 行二次把守,
 *     防「注释剥离后文本级漏检」的镜像形态)。
 *
 * 扫描 = 主源集(测试源允许使用实现类型做替身,不在门禁面内)。
 */
class LayerDependencyGateTest {

    private val sourceRoot: File =
        File(System.getProperty("user.dir"), "src/main/java/com/lioisme/modev")

    private fun sources(): List<Pair<String, String>> {
        assertTrue("源码目录不存在: $sourceRoot", sourceRoot.isDirectory)
        return sourceRoot.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".kt") }
            .map { it.readText() to it.relativeTo(sourceRoot).path }
            .toList()
    }

    @Test
    fun `no compat typealias layer points back into domain`() {
        val offenders = mutableListOf<String>()
        for ((text, path) in sources()) {
            for (line in text.lines()) {
                val trimmed = line.trim()
                if (!trimmed.startsWith("typealias ")) continue
                // 仅禁「指向 domain 的别名」;domain 自身与数据层内部别名不受影响。
                if (trimmed.contains("com.lioisme.modev.domain.")) offenders += "$path :: $trimmed"
            }
        }
        assertEquals(
            "兼容 typealias 层已随 V45 T2.3 删除,禁止复活(消费方一律 import domain 真实类型):\n" +
                offenders.joinToString("\n"),
            emptyList<String>(),
            offenders,
        )
        // 五件 Compat 文件不得回归
        for (gone in listOf(
            "mapping/RowModelCompat.kt",
            "mapping/RecordDetailModelCompat.kt",
            "repo/PipelineStatsCompat.kt",
            "repo/RecordFilter.kt",
            "repo/RecordService.kt",
        )) {
            assertTrue(
                "兼容层文件已删除,不得回归: data/$gone",
                !File(sourceRoot, "data/$gone").exists(),
            )
        }
    }

    @Test
    fun `container exposes only domain contracts`() {
        val text = File(sourceRoot, "ModevApp.kt").readText()
        val domainImports = text.lines()
            .filter { it.startsWith("import com.lioisme.modev.domain.") }
            .map { it.removePrefix("import ") }
            .toSet()
        val violations = mutableListOf<String>()
        var scanned = 0
        var insideContainer = false
        for (line in text.lines()) {
            if (line.startsWith("class AppContainer")) insideContainer = true
            if (!insideContainer) continue
            // public 属性 = 无 private/internal 修饰(顶层 `class ModevApp` 段先于容器,已跳过)
            val match = Regex("""^    val (\w+): ([\w.]+)""").find(line) ?: continue
            val name = match.groupValues[1]
            val declared = match.groupValues[2]
            scanned++
            val simpleType = declared.substringBefore('<').substringBefore('?')
            val isDomainContract = domainImports.any { it.endsWith(".$simpleType") }
            if (!isDomainContract) violations += "AppContainer.$name : $declared"
        }
        assertEquals(
            "容器 public 属性必须是 domain 契约(实现类型只允许 internal/private,仅装配可见):\n" +
                violations.joinToString("\n"),
            emptyList<String>(),
            violations,
        )
        // 扫描自校验:命中数必须 ≥ 契约出口数,否则规则已失效(改名/改形态静默空转)
        assertTrue(
            "容器契约出口扫描失效信号:检查到的 public 属性过少",
            scanned >= 10,
        )
    }

    @Test
    fun `consumers do not import data layer types`() {
        val offenders = mutableListOf<String>()
        for ((text, path) in sources()) {
            val sub = path.substringBefore(File.separatorChar)
            if (sub != "ui" && sub != "mcp") continue
            for (line in text.lines()) {
                if (line.startsWith("import com.lioisme.modev.data.")) offenders += "$path :: $line"
            }
        }
        assertEquals(
            "UI/MCP 直接 import 数据层类型(应与 MCP 同经 domain 方法层):\n" +
                offenders.joinToString("\n"),
            emptyList<String>(),
            offenders,
        )
    }
}
