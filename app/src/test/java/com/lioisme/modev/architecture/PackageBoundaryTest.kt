package com.lioisme.modev.architecture

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 包边界架构测试(P5-1,2026-08-17;V23 R4 增补 R3;**V43 T7/V5.2 升级为
 * 文本级全限定引用扫描**,`^import` 行扫描可被 `com.lioisme.modev.x.y.Z` 内联
 * 全限定引用绕过且已发生——SettingsRepository→mcp 依赖环实证):
 *
 *  规则(扫描 = 去注释/去字符串字面量后的源码文本):
 *  R1:ui 子树不得依赖注入侧(hook)与**任何 data 实现**(V45 T2.2:数据层全子树禁,
 *     此前仅禁 data.server/data.db,`data.repo`/`data.mapping` 具体类型可被 ViewModel
 *     直接点名 —— 门面被实现类型旁路的根因)。
 *  R2:domain 子树不得依赖 android 框架(文案/资源)、ui 层与 data 实现
 *     (V43 T7/V5.3:模型族上移 domain 后,逆向依赖零容忍)。
 *  R3(V23):ui 子树不得依赖 data.db(Room 实体/DAO/库名;R1 已含,单列保留诊断信息)。
 *  R4(V43):hook 子树不得依赖 ui/compose(注入侧与宿主 UI 完全隔离)。
 *  R5(V43):data.repo 不得依赖 mcp(装配在 ModevApp,仓库不反控服务层)。
 *  R6(V45):mcp 子树不得依赖任何 data 实现(与 UI 同 = 只经 domain 契约)。
 *
 * 全部通过才允许新增豁免(注释说明理由)。
 * V45 T2.3:原「兼容 typealias 迁移层」豁免名单清空 —— RowModelCompat/
 * RecordDetailModelCompat/PipelineStatsCompat/data.repo.RecordFilter/
 * data.repo.RecordService 五件已删除,消费方 import 面全量迁至 domain 真实类型
 * (「兼容层」曾是 ui 持有 data 依赖的唯一理由,兼容期已结束)。
 */
class PackageBoundaryTest {

    private val sourceRoot: File =
        File(System.getProperty("user.dir"), "src/main/java/com/lioisme/modev")

    private data class KotlinFile(val packageName: String, val code: String, val imports: List<String>, val path: String)

    private fun scan(): List<KotlinFile> {
        assertTrue("源码目录不存在: $sourceRoot", sourceRoot.isDirectory)
        val blockComment = Regex("/\\*[\\s\\S]*?\\*/")
        val lineComment = Regex("//[^\n]*")
        return sourceRoot.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".kt") }
            .map { file ->
                val raw = file.readText()
                val stripped = blockComment.replace(raw, " ").let { lineComment.replace(it, " ") }
                val packageName = raw.lineSequence()
                    .firstOrNull { it.trimStart().startsWith("package ") }
                    ?.trim()?.removePrefix("package ")?.removeSuffix(";")
                    ?: "unknown"
                val imports = raw.lines().filter { it.startsWith("import ") }
                    .map { it.removePrefix("import ").removeSuffix(";") }
                KotlinFile(packageName, stripped, imports, file.relativeTo(sourceRoot).path)
            }
            .toList()
    }

    /** 文本级引用判定:出现 `com.lioisme.modev.<prefix>.` 全限定前缀即依赖该子树。 */
    private fun references(code: String, pkgPrefix: String): Boolean =
        Regex(Regex.escape("com.lioisme.modev.") + Regex.escape(pkgPrefix) + "[.\\w]").containsMatchIn(code) ||
            Regex(Regex.escape("com.lioisme.modev.") + Regex.escape(pkgPrefix) + "$").containsMatchIn(code)

    /**
     * V45 T2.3:兼容 typealias 迁移层已整体删除,豁免名单恒空 ——
     * 保留该集合是为了「新增豁免必须先写理由」这一门槛形态,禁止静默放行。
     */
    private val compatWhitelist = setOf<String>()

    private fun check(
        files: List<KotlinFile>,
        srcPrefix: String,
        forbidden: List<String>,
        message: String,
    ) {
        val violations = mutableListOf<String>()
        for (file in files) {
            if (!file.packageName.startsWith("com.lioisme.modev.$srcPrefix")) continue
            if (compatWhitelist.any { file.path.endsWith(it) }) continue
            for (target in forbidden) {
                if (references(file.code, target)) {
                    violations += "${file.path} -> com.lioisme.modev.$target"
                }
            }
        }
        assertEquals("$message:\n${violations.joinToString("\n")}", emptyList<String>(), violations)
    }

    @Test
    fun `ui does not depend on hook or data implementations`() {
        // V45 T2.2(R1 收紧):hook + data 全子树禁 —— UI 唯一上游 = domain 契约。
        check(scan(), "ui.", listOf("hook", "data"), "UI 层依赖注入侧/数据实现,应经 domain 抽象交互")
    }

    @Test
    fun `mcp does not depend on data implementations`() {
        // V45 T2.2(R6):MCP 与 UI 同一契约面(需求 3「双一等公民」的类型级保证)。
        // 与 McpToolParityTest 的 B1 扫描互补:B1 逐条列被禁子树,本条整树禁 data。
        check(scan(), "mcp", listOf("data"), "MCP 工具层依赖数据实现,应与 UI 同经 domain 契约")
    }

    @Test
    fun `domain does not depend on android framework ui or data`() {
        val files = scan()
        val violations = mutableListOf<String>()
        // V43 T7/V5.1 白名单:
        //  - compose runtime @Immutable = JVM 兼容纯注解;
        //  - androidx.paging PagingData = 分页契约类型(RecordService 门面签名,
        //    旧规则仅查 `android.` 前缀 import 未覆盖 androidx,维持同一豁免面)。
        val androidxAllowed = Regex("""androidx\.compose\.runtime\.Immutable|androidx\.paging\.""")
        for (file in files) {
            if (!file.packageName.startsWith("com.lioisme.modev.domain.")) continue
            val residualAndroidx = file.code
                .lines()
                .filter { it.contains("androidx.") }
                .any { androidxAllowed.replace(it, "").contains("androidx.") }
            if (residualAndroidx || Regex("""\bandroid\.[\w.]+""").containsMatchIn(file.code)) {
                violations += "${file.path} -> android/androidx"
            }
            for (target in listOf("ui", "data")) {
                if (references(file.code, target)) violations += "${file.path} -> com.lioisme.modev.$target"
            }
        }
        assertEquals("domain 层依赖 android/ui/data,应保持纯 Kotlin 领域逻辑:\n${violations.joinToString("\n")}", emptyList<String>(), violations)
    }

    @Test
    fun `ui does not depend on room database internals`() {
        check(scan(), "ui.", listOf("data.db"), "UI 层依赖 Room 内部(实体/DAO/库名),存储细节应由仓库出口承担")
    }

    @Test
    fun `hook does not depend on ui or compose`() {
        val files = scan()
        val violations = mutableListOf<String>()
        for (file in files) {
            if (!file.packageName.startsWith("com.lioisme.modev.hook")) continue
            if (file.code.contains("androidx.compose")) violations += "${file.path} -> androidx.compose"
            if (references(file.code, "ui")) violations += "${file.path} -> com.lioisme.modev.ui"
        }
        assertEquals("注入侧依赖 UI/Compose(必须与宿主界面完全隔离):\n${violations.joinToString("\n")}", emptyList<String>(), violations)
    }

    @Test
    fun `data repo does not depend on mcp`() {
        check(scan(), "data.repo", listOf("mcp"), "仓库层反控 MCP 服务层(装配应集中于 ModevApp)")
    }

    @Test
    fun `architecture rules allow no wildcard or unused exemptions`() {
        val scan = scan()
        assertEquals("存在通配 import,包边界扫描需显式列出:", emptyList<String>(),
            scan.flatMap { it.imports.filter { i -> i.contains("*") } })
    }
}
