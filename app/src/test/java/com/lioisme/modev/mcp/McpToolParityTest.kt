package com.lioisme.modev.mcp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * MCP 工具面 parity 门禁(V38 D5,源码扫描):
 * - 冻结 43 工具名快照(新增 UI 能力必须同步工具面,否则此测试红);
 * - mcp 包上游 allowlist:禁 import ui/compose/data.db/hook/data.server(B1),
 *   允许 domain/data.repo/core(B2)——与 UI 同源继承的结构性保证。
 */
class McpToolParityTest {

    private fun sourceDir(vararg segments: String): File {
        var dir = File(System.getProperty("user.dir")!!)
        repeat(2) { if (dir.name != "modev" && dir.resolve("app").exists().not()) dir = dir.parentFile ?: dir }
        val candidates = listOf(
            File(System.getProperty("user.dir")!!),
            File(System.getProperty("user.dir")!!).parentFile,
        )
        val root = candidates.firstOrNull { File(it, "app/src/main/java/com/lioisme/modev").exists() }
            ?: error("cannot locate project root from ${System.getProperty("user.dir")}")
        return File(root, segments.joinToString("/"))
    }

    private fun mcpSources(): List<File> {
        val dir = sourceDir("app", "src", "main", "java", "com", "lioisme", "modev", "mcp")
        require(dir.isDirectory) { "mcp package missing: $dir" }
        return dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    @Test
    fun frozen_43_tool_names() {
        val expected = listOf(
            // 记录组 14
            "records_query", "records_count", "records_type_counts", "records_category_counts",
            "record_get", "record_neighbor", "record_create", "record_update", "record_delete",
            "records_delete_by_filter", "records_clear_package", "records_clear_type", "records_clear_all",
            "storage_usage",
            // 应用组 6
            "apps_list", "app_monitor", "app_launch", "app_clear_records", "applist_prefs_get", "applist_prefs_set",
            // 监听配置组 4
            "listen_settings_get", "listen_settings_set", "listen_settings_reset", "taxonomy_listen",
            // 功能配置组 4
            "feature_settings_get", "feature_settings_set", "feature_settings_reset", "taxonomy_feature",
            // 全局资产组 8
            "keywords_list", "keywords_add", "keywords_update", "keywords_remove",
            "spoof_profiles_list", "spoof_global_set", "blacklist_list", "blacklist_edit",
            // 服务诊断组 4
            "server_status", "server_set_external_access", "server_set_port", "diagnostics",
            // 作用域组 3
            "scope_state", "scope_add", "scope_remove",
        )
        assertEquals(43, expected.size)
        assertEquals(expected.size, expected.toSet().size)
        // 源码内 name= 声明与快照一致
        val declared = mcpSources()
            .flatMap { it.readLines() }
            .mapNotNull { line ->
                Regex("""name\s*=\s*"([a-z_]+)"""").find(line)?.groupValues?.get(1)
            }
            .toSet()
        val missing = expected.filterNot { it in declared }
        assertTrue("tools missing from source declarations: $missing", missing.isEmpty())
    }

    /**
     * V39 §5.3 明示排除:令牌管理(McpTokensScreen:认证开关 / 多令牌创建撤销)
     * **不**暴露为 MCP 工具——引导悖论(启用认证后需令牌才能调用造令牌工具,
     * 回环自锁风险)+ PII 纪律(令牌值属宿主本地秘密,Agent 面零可见)。
     * 本快照恒 43;若未来新增 UI 能力须同步工具面或在此登记排除理由。
     */
    @Test
    fun token_management_is_documented_exclusion() {
        val tokenTools = mcpSources()
            .flatMap { it.readLines() }
            .mapNotNull { line ->
                Regex("""name\s*=\s*"([a-z_]*token[a-z_]*)"""").find(line)?.groupValues?.get(1)
            }
            .toSet()
        assertTrue(
            "token management must stay out of the MCP tool surface (bootstrap paradox + PII); " +
                "unexpected token tools: $tokenTools",
            tokenTools.isEmpty(),
        )
    }

    @Test
    fun mcp_layer_boundary_allowlist() {
        val forbidden = listOf(
            Regex("""import\s+com\.lioisme\.modev\.ui\."""),
            Regex("""import\s+androidx\.compose\."""),
            Regex("""import\s+com\.lioisme\.modev\.data\.db\."""),
            Regex("""import\s+com\.lioisme\.modev\.hook\."""),
            Regex("""import\s+com\.lioisme\.modev\.data\.server\."""),
            Regex("""import\s+com\.lioisme\.modev\.framework\."""),
        )
        val violations = mutableListOf<String>()
        mcpSources().forEach { file ->
            file.readLines().forEachIndexed { index, line ->
                forbidden.forEach { pattern ->
                    if (pattern.containsMatchIn(line)) {
                        violations += "${file.name}:L${index + 1} ${line.trim()}"
                    }
                }
            }
        }
        assertEquals("mcp package boundary violations (B1):\n" + violations.joinToString("\n"), 0, violations.size)
    }
}
