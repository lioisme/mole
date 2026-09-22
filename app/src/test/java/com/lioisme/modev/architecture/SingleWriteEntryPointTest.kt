package com.lioisme.modev.architecture

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 写侧单点门禁(V45 T2.4,需求 1/2 的写路径镜像):
 * 每一项「可变事实」必须只有一个写入口,否则两侧(UI / MCP / 装配)各写一份必然漂移。
 * 本门禁按源码文本扫描调用点,新增写点必须先登记理由。
 *
 *  W1 SharedPreferences 文件打开者 = 各文件的设计所有者(配置写路径唯一收口在
 *     `SettingsRepository` + `MirroredConfigWriter`,其余子树不得自开 prefs 绕门面);
 *  W2 服务端可变面(`externalAccess` / `port` / `requireToken` / `tokenAuthEnabled` /
 *     `requiredTokens`)只允许「装配层 ModevApp」与「配置仓库 SettingsRepository」写;
 *  W3 端口写方法调用点 = 两个消费面(服务页 VM、MCP ServerTools)+ 实现方自身,
 *     任何第三处直写即红(V45 DUAL-3:UI 与 MCP 同经 [PortWriteGuard] 硬闸)。
 */
class SingleWriteEntryPointTest {

    private val sourceRoot: File =
        File(System.getProperty("user.dir"), "src/main/java/com/lioisme/modev")

    private data class Site(val path: String, val line: String)

    private fun sites(pattern: Regex): List<Site> {
        assertTrue("源码目录不存在: $sourceRoot", sourceRoot.isDirectory)
        return sourceRoot.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".kt") }
            .flatMap { file ->
                val rel = file.relativeTo(sourceRoot).path.replace('\\', '/')
                file.readLines().mapNotNull { line ->
                    if (pattern.containsMatchIn(line)) Site(rel, line.trim()) else null
                }
            }
            .toList()
    }

    private fun assertOnly(label: String, found: List<Site>, allowed: Set<String>) {
        val unexpected = found.filterNot { it.path in allowed }
        assertEquals(
            "$label —— 出现未登记写点(新增需说明理由并登记):\n" +
                unexpected.joinToString("\n") { "${it.path}: ${it.line}" },
            emptyList<Site>(),
            unexpected,
        )
        assertTrue("$label —— 扫描结果不应为空(规则失效信号)", found.isNotEmpty())
    }

    @Test
    fun `only designated owners open their preference files`() {
        assertOnly(
            "W1 prefs 打开者",
            sites(Regex("""getSharedPreferences\(""")),
            setOf(
                "ModevApp.kt",
                "data/repo/AppListSettings.kt",
                "i18n/LocaleManager.kt",
                "ui/theme/ThemeSettingsManager.kt",
            ),
        )
    }

    @Test
    fun `only assembly and config repository mutate server runtime state`() {
        assertOnly(
            "W2 服务端可变面写入",
            sites(
                Regex(
                    """\.(externalAccess|requireToken|tokenAuthEnabled|requiredTokens)\s*=[^=]|""" +
                        """\bbuilt\.port\s*=[^=]""" + """|(?<![\w.])server\.port\s*=[^=]""",
                ),
            ),
            setOf("ModevApp.kt", "data/repo/SettingsRepository.kt"),
        )
    }

    @Test
    fun `port writes go only through the guarded contract`() {
        val callers = sites(Regex("""\.set(?:Server|Mcp)Port\("""))
        assertOnly(
            "W3 端口写调用点",
            callers,
            setOf(
                // 契约声明 + 实现(唯一落盘处,内建 PortWriteGuard 硬闸)
                "domain/server/ServerConfigService.kt",
                "data/repo/SettingsRepository.kt",
                // 两个消费面:UI = 屏 → VM → 契约;MCP = 工具 → 契约(V44 起同名方法)
                "ui/settings/ServicesScreen.kt",
                "ui/settings/ServicesViewModel.kt",
                "mcp/tools/ServerTools.kt",
            ),
        )
        // 两个消费面必须都在(缺一即「双端同源」断言失去意义)
        val paths = callers.map { it.path }.toSet()
        assertTrue("W3 应同时覆盖 UI 与 MCP 两个端口写面: $paths", "ui/settings/ServicesViewModel.kt" in paths)
        assertTrue("W3 应同时覆盖 UI 与 MCP 两个端口写面: $paths", "mcp/tools/ServerTools.kt" in paths)
    }
}
