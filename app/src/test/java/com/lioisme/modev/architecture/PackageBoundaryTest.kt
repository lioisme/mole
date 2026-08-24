package com.lioisme.modev.architecture

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 包边界架构测试(P5-1,2026-08-17;V23 R4 增补 R3):
 *
 * 规则(与 docs §3 P5-1 一致):
 *  R1:ui 子树不得依赖注入侧(hook)与传输实现(data.server)——经 domain 抽象(ConnectionMonitor 等)交互。
 *  R2:domain 子树不得依赖 android 框架(文案/资源)与 ui 层——纯 Kotlin 领域逻辑。
 *  R3(V23):ui 子树不得依赖 data.db(Room 实体/DAO/库名)——存储细节由仓库出口承担
 *    (如存储占用走 LogRepository.storageUsage),为 MCP 服务层复用同一契约面兜底。
 *
 * 扫描基准为模块内 src/main/java;全部通过才允许新增豁免(注释说明理由)。
 */
class PackageBoundaryTest {

    private val sourceRoot: File =
        File(System.getProperty("user.dir"), "src/main/java/com/lioisme/modev")

    private data class KotlinFile(val packageName: String, val imports: List<String>, val path: String)

    private fun scan(): List<KotlinFile> {
        assertTrue("源码目录不存在: $sourceRoot", sourceRoot.isDirectory)
        return sourceRoot.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".kt") }
            .map { file ->
                val lines = file.readLines().map { it.removePrefix("\uFEFF").trimEnd() }
                val packageName = lines.first { it.startsWith("package ") }
                    .removePrefix("package ").removeSuffix(";")
                val imports = lines.filter { it.startsWith("import ") }
                    .map { it.removePrefix("import ").removeSuffix(";") }
                KotlinFile(packageName, imports, file.relativeTo(sourceRoot).path)
            }
            .toList()
    }

    @Test
    fun `ui does not depend on hook or data server implementations`() {
        val violations = mutableListOf<String>()
        for (file in scan()) {
            if (!file.packageName.startsWith("com.lioisme.modev.ui.")) continue
            for (target in listOf("com.lioisme.modev.hook", "com.lioisme.modev.data.server")) {
                for (imp in file.imports) {
                    if (imp == target || imp.startsWith("$target.")) {
                        violations += "${file.path} -> $imp"
                    }
                }
            }
        }
        assertEquals("UI 层依赖注入侧/传输实现,应经 domain 抽象交互:\n${violations.joinToString("\n")}", emptyList<String>(), violations)
    }

    @Test
    fun `domain does not depend on android framework or ui layer`() {
        val violations = mutableListOf<String>()
        for (file in scan()) {
            if (!file.packageName.startsWith("com.lioisme.modev.domain.")) continue
            for (imp in file.imports) {
                if (imp.startsWith("android.") || imp.startsWith("com.lioisme.modev.ui")) {
                    violations += "${file.path} -> $imp"
                }
            }
        }
        assertEquals("domain 层依赖 android/ui,应保持纯 Kotlin:\n${violations.joinToString("\n")}", emptyList<String>(), violations)
    }

    @Test
    fun `ui does not depend on room database internals`() {
        val violations = mutableListOf<String>()
        for (file in scan()) {
            if (!file.packageName.startsWith("com.lioisme.modev.ui.")) continue
            for (imp in file.imports) {
                if (imp == "com.lioisme.modev.data.db" || imp.startsWith("com.lioisme.modev.data.db.")) {
                    violations += "${file.path} -> $imp"
                }
            }
        }
        assertEquals(
            "UI 层依赖 Room 内部(实体/DAO/库名),存储细节应由仓库出口承担:\n${violations.joinToString("\n")}",
            emptyList<String>(),
            violations,
        )
    }

    @Test
    fun `architecture rules allow no wildcard or unused exemptions`() {
        val scan = scan()
        assertEquals("存在通配 import,包边界扫描需显式列出:", emptyList<String>(),
            scan.flatMap { it.imports.filter { i -> i.contains("*") } })
    }
}