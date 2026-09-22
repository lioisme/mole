package com.lioisme.modev.mcp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * MCP 工具面 parity 门禁(V38 D5,源码扫描):
 * - 冻结 50 工具名快照(新增 UI 能力必须同步工具面,否则此测试红);
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
    fun frozen_50_tool_names() {
        val expected = listOf(
            // 记录组 14
            "records_query", "records_count", "records_type_counts", "records_category_counts",
            "record_get", "record_neighbor", "record_create", "record_update", "record_delete",
            "records_delete_by_filter", "records_clear_package", "records_clear_type", "records_clear_all",
            "storage_usage",
            // 应用组 6(V45 T3.5:app_clear_records 与 records_clear_package 同上游,已去重)
            "apps_list", "apps_refresh", "app_monitor", "app_launch",
            "applist_prefs_get", "applist_prefs_set",
            // 监听配置组 4
            "listen_settings_get", "listen_settings_set", "listen_settings_reset", "taxonomy_listen",
            // 功能配置组 4
            "feature_settings_get", "feature_settings_set", "feature_settings_reset", "taxonomy_feature",
            // 全局资产组 10(V44 keyword_set_enabled / keywords_clear;V47 WP-7 spoof_profiles_list 改读画像库真身)
            "keywords_list", "keywords_add", "keywords_update", "keywords_remove",
            "keyword_set_enabled", "keywords_clear",
            "spoof_profiles_list", "spoof_global_set", "blacklist_list", "blacklist_edit",
            // 画像库组 5(V47 WP-7:DIY 端到端 = 存 / 删 / 绑 / 目录 / 导入)
            "spoof_profile_save", "spoof_profile_delete", "spoof_profile_apply",
            "spoof_profile_catalog", "spoof_profile_import",
            // 服务诊断组 4
            "server_status", "server_set_external_access", "server_set_port", "diagnostics",
            // 作用域组 3
            "scope_state", "scope_add", "scope_remove",
        )
        assertEquals(50, expected.size)
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
     * 本快照恒 50;若未来新增 UI 能力须同步工具面或在此登记排除理由。
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

    /**
     * V45 T3.1 / QA-4「声明未注册」门禁:工具组的 `val tools = listOf(a(), b(), …)` 与
     * 该文件内 `private fun x() = schemaTool(` 定义的**全部**工具构造器必须一一对应。
     *
     * 冻结快照只锁「名单」,锁不住「写了但没挂」:一个 `private fun mcpSetPort()` 若漏进
     * listOf,tools/list 就永远看不见它,而单测/快照都不会红(V44 审计 DUAL-4 的真实成因)。
     * 本门禁按文件逐个比对定义集与注册集,双向缺项都算违规。
     */
    @Test
    fun every_declared_tool_builder_is_registered() {
        val definedPattern = Regex("""private fun (\w+)\(\)\s*=\s*schemaTool\(""")
        val problems = mutableListOf<String>()
        mcpSources().filter { it.name.endsWith("Tools.kt") }.forEach { file ->
            val text = file.readText()
            val defined = definedPattern.findAll(text).map { it.groupValues[1] }.toSet()
            if (defined.isEmpty()) return@forEach
            val registered = registryBodies(text)
                .flatMap { Regex("""(\w+)\(\)""").findAll(it).map { m -> m.groupValues[1] } }
                .toSet()
            val unregistered = defined - registered
            val phantom = registered - defined
            if (unregistered.isNotEmpty()) problems += "${file.name} 定义未注册: $unregistered"
            if (phantom.isNotEmpty()) problems += "${file.name} 注册了未定义的工具构造器: $phantom"
        }
        assertEquals(
            "工具装配面漂移(声明未注册 / 注册指向不存在构造器):\n" + problems.joinToString("\n"),
            emptyList<String>(),
            problems,
        )
    }

    /**
     * 取工具组注册表 `val tools: List<McpTool> = listOf(...)` 的实参文本
     * (锚定声明头 + 括号配平手工扫描)。
     *
     * 不用正则是刻意的:①条目自带 `()` 调用,任何「懒匹配到第一个 `)`」的写法都会在第一
     * 个工具名处截断(实测恒得空集 = 门禁全绿假象);②文件内还有 `required(listOf(...))`
     * 等无关 listOf,不锚定声明头会把它们算进注册集造成假绿。
     */
    private fun registryBodies(text: String): List<String> {
        val marker = "tools: List<McpTool> = listOf("
        val bodies = mutableListOf<String>()
        var from = 0
        while (true) {
            val start = text.indexOf(marker, from)
            if (start < 0) return bodies
            var depth = 0
            var i = start + marker.length - 1
            while (i < text.length) {
                when (text[i]) {
                    '(' -> depth++
                    ')' -> depth--
                }
                if (depth == 0) break
                i++
            }
            bodies += text.substring(start + marker.length, i.coerceAtMost(text.length))
            from = i + 1
        }
    }

    /**
     * V45 T3.1 / DUAL-4 明示排除:MCP **不**提供 `mcp_set_enabled` / `mcp_set_port` /
     * `mcp_set_token_auth` 三个自毁型控制面开关(与令牌管理同一排除族)。
     *
     * 理由:①停用/改端口/开认证都会终结当前连接 ⇒ Agent 一旦调用就把自己关在门外
     * (回环自锁,与令牌引导悖论同型);②这些开关的**权威入口**已在服务页
     * (`ServicesViewModel.setMcpEnabled/setMcpPort/setMcpTokenAuth`,插桩矩阵 M-SRV-* 覆盖),
     * 双端「能力对称」在此让位于「不可逆控制面只留给本地操作者」——
     * 这是设计决定而非缺口,故在此登记并断言其确实不存在(防被无意加回)。
     */
    @Test
    fun self_disabling_control_plane_stays_out_of_tool_surface() {
        val declared = mcpSources()
            .flatMap { it.readLines() }
            .mapNotNull { line -> Regex("""name\s*=\s*"(mcp_[a-z_]+)"""").find(line)?.groupValues?.get(1) }
            .toSet()
        assertEquals(
            "mcp_* 自毁型开关不得进入工具面(见本用例 KDoc;UI 侧 ServicesViewModel 为唯一入口):" +
                declared,
            emptySet<String>(),
            declared,
        )
    }

    /**
     * B1 边界(V43 T7/V5.2 升级为文本级扫描——旧 import 行形态可被全限定引用
     * 内联绕过,且 McpDtos 曾实证 `is com.lioisme.modev.data.mapping.X` 形态;
     * 同时增补 data.mapping 禁令:行/详情模型已上移 domain.record,V5.4,
     * mcp 必须经领域类型消费,不得再绑存储映射层)。
     */
    @Test
    fun mcp_layer_boundary_allowlist() {
        val forbidden = listOf(
            Regex("""com\.lioisme\.modev\.ui\."""),
            Regex("""androidx\.compose\."""),
            Regex("""com\.lioisme\.modev\.data\.db\."""),
            Regex("""com\.lioisme\.modev\.hook\."""),
            Regex("""com\.lioisme\.modev\.data\.server\."""),
            Regex("""com\.lioisme\.modev\.framework\."""),
            Regex("""com\.lioisme\.modev\.data\.mapping\."""),
        )
        val blockComment = Regex("/\\*[\\s\\S]*?\\*/")
        val lineComment = Regex("//[^\n]*")
        val violations = mutableListOf<String>()
        mcpSources().forEach { file ->
            val code = lineComment.replace(blockComment.replace(file.readText(), " "), " ")
            code.lineSequence().map { it.trim() }.forEach { line ->
                forbidden.forEach { pattern ->
                    if (pattern.containsMatchIn(line)) {
                        violations += "${file.name}: $line"
                    }
                }
            }
        }
        assertEquals("mcp package boundary violations (B1):\n" + violations.joinToString("\n"), 0, violations.size)
    }
}
