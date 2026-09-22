package com.lioisme.modev.architecture

import com.lioisme.modev.core.model.RecordType
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * UI ↔ MCP 能力对称门禁(V45 T3.5,需求 3「双一等公民」的可执行口径)。
 *
 * 三条规则,全部从源码派生(不靠人肉清单自证):
 *  * **R1 动作单点**:两个工具不得广告同一个**可变上游方法**
 *    (`logs.clearPackage` 被 `records_clear_package` 与已删除的 `app_clear_records` 各包一层的形态,
 *    即 DUAL-11 的根因)。登记在 [SHARED_UPSTREAM_EXCEPTIONS] 的成对例外必须给理由
 *    (listen/feature 两面共享 `setOverride` 是**设计**:双域闸分列,不是重复)。
 *  * **R2 工具全覆盖**:每个 MCP 工具必须落在一条 [Capability] 行上,行里的工具名必须真实存在
 *    (新增工具不登记即红;删了工具而行还引用也是红)。
 *  * **R3 单侧必须给理由**:`ui` 为空(Agent 专属,如记录导入)或 `tools` 为空
 *    (UI 专属,如 MCP 控制面/令牌管理)的行,`reason` 非空且不得是占位。
 *
 * 与 [com.lioisme.modev.mcp.McpToolParityTest] 的分工:那边锁「名单快照 + 令牌/自毁控制面排除」,
 * 本门禁锁「名单背后的能力归属与动作唯一性」。
 */
class UiMcpCapabilityParityTest {

    private val moduleRoot = File(System.getProperty("user.dir"))
    private val sourceRoot = File(moduleRoot, "src/main/java/com/lioisme/modev")

    /** 一条能力 = UI 入口 + MCP 工具 + 单侧理由。 */
    private data class Capability(
        val name: String,
        val ui: List<String>,
        val tools: List<String>,
        val reason: String = "",
    )

    private val capabilities = listOf(
        Capability(
            name = "记录浏览(过滤 / 搜索 / 类别 / 详情 / 邻接 / 存储)",
            ui = listOf(
                "RecordsViewModel.setQuery",
                "RecordsViewModel.setKindName",
                "RecordDetailViewModel.goOlder",
                "RecordDetailViewModel.goNewer",
                "RecordDetailViewModel.retry",
            ),
            tools = listOf(
                "records_query", "records_count", "records_type_counts", "records_category_counts",
                "record_get", "record_neighbor", "storage_usage",
            ),
        ),
        Capability(
            name = "记录清理(范围 / 本类 / 全库)",
            ui = listOf(
                "RecordsViewModel.clearCurrentScope",
                "AppMonitorViewModel.clearLogs",
                "DataSettingsViewModel.clearAll",
            ),
            tools = listOf("records_delete_by_filter", "records_clear_package", "records_clear_type", "records_clear_all"),
        ),
        Capability(
            name = "记录写入(导入 / 改写 / 单条删除)",
            ui = emptyList(),
            tools = listOf("record_create", "record_update", "record_delete"),
            reason = "Agent 专属:测试夹具注入与跨重启数据整形。UI 无手写记录入口 —— " +
                "采集事实对使用者不可编辑(被动采集红线 AGENTS #11)," +
                "故「读得出就写得回」只在方法层与 MCP 面成立。",
        ),
        Capability(
            name = "应用目录浏览与刷新",
            ui = listOf("AppsViewModel.setQuery", "AppsViewModel.refresh"),
            tools = listOf("apps_list", "apps_refresh", "app_monitor"),
        ),
        Capability(
            name = "应用启动",
            // UI 入口 = 监控页 Appbar 按钮(由 VM 的 launchable 状态驱动,AGENTS #24);
            // 动作本体是 nav 回调 onLaunch,不是 VM 方法。
            ui = listOf("AppMonitorViewModel.launchable"),
            tools = listOf("app_launch"),
        ),
        Capability(
            name = "应用列表偏好",
            ui = listOf(
                "AppsViewModel.setPinScoped",
                "AppsViewModel.setShowSystem",
                "AppsViewModel.setShowModules",
            ),
            tools = listOf("applist_prefs_get", "applist_prefs_set"),
        ),
        Capability(
            name = "框架作用域读与改",
            ui = listOf("AppsViewModel.toggle", "AppMonitorViewModel.toggleScope", "AppMonitorViewModel.removeScope"),
            tools = listOf("scope_state", "scope_add", "scope_remove"),
        ),
        Capability(
            name = "按应用监听配置(LISTEN 域)",
            ui = listOf(
                "PerAppListenSettingsViewModel.setMaster",
                "PerAppListenSettingsViewModel.setBehaviorMaster",
                "PerAppListenSettingsViewModel.setPrefsMaster",
                "PerAppListenSettingsViewModel.setWebviewMaster",
                "PerAppListenSettingsViewModel.setSystemMaster",
                "PerAppListenSettingsViewModel.setKind",
                "PerAppListenSettingsViewModel.setLogStack",
                "PerAppListenSettingsViewModel.setMaxBytesPerField",
                "PerAppListenSettingsViewModel.setIgnoreStackPrefixes",
                "PerAppListenSettingsViewModel.setMaxResultRows",
                "PerAppListenSettingsViewModel.setMaxResultChars",
                "PerAppListenSettingsViewModel.setFileContentCapture",
                "PerAppListenSettingsViewModel.setIgnorePathPrefixes",
                "PerAppListenSettingsViewModel.reset",
            ),
            tools = listOf("listen_settings_get", "listen_settings_set", "listen_settings_reset", "taxonomy_listen"),
        ),
        Capability(
            name = "按应用功能配置(FEATURE 域)",
            ui = listOf(
                "PerAppFeatureSettingsViewModel.setUiMaster",
                "PerAppFeatureSettingsViewModel.setNetworkMaster",
                "PerAppFeatureSettingsViewModel.setDeviceEnvMaster",
                "PerAppFeatureSettingsViewModel.setProcessControlMaster",
                "PerAppFeatureSettingsViewModel.setKind",
                "PerAppFeatureSettingsViewModel.setKeywordFollowGlobal",
                "PerAppFeatureSettingsViewModel.toggleKeyword",
                "PerAppFeatureSettingsViewModel.setSpoofProfile",
                "PerAppFeatureSettingsViewModel.reset",
            ),
            tools = listOf("feature_settings_get", "feature_settings_set", "feature_settings_reset", "taxonomy_feature"),
        ),
        Capability(
            name = "全局关键词库",
            ui = listOf(
                "KeywordLibraryViewModel.add",
                "KeywordLibraryViewModel.update",
                "KeywordLibraryViewModel.setEnabled",
                "KeywordLibraryViewModel.remove",
            ),
            tools = listOf(
                "keywords_list", "keywords_add", "keywords_update", "keywords_remove",
                "keyword_set_enabled", "keywords_clear",
            ),
        ),
        Capability(
            name = "伪装画像与字段覆写",
            ui = listOf(
                "SpoofProfileViewModel.setProfile",
                "SpoofProfileViewModel.setOverride",
                "SpoofProfileViewModel.removeOverride",
            ),
            tools = listOf("spoof_profiles_list", "spoof_global_set"),
        ),
        Capability(
            name = "检测黑名单追加项",
            ui = listOf("FeatureBlacklistViewModel.add", "FeatureBlacklistViewModel.remove"),
            tools = listOf("blacklist_list", "blacklist_edit"),
        ),
        Capability(
            name = "统一外部访问与 wire 端口",
            ui = listOf("ServicesViewModel.setExternalAccess", "ServicesViewModel.setWirePort"),
            tools = listOf("server_status", "server_set_external_access", "server_set_port"),
        ),
        Capability(
            name = "诊断快照",
            ui = listOf("DiagnosticsScreen.copyDiagnostics"),
            tools = listOf("diagnostics"),
            reason = "UI 侧是剪贴板动作(screen 局部函数,不在 ViewModel 动作面);" +
                "两侧同经 DiagnosticsReport.build 装配,数据逐字同源(标签英文锁定为 Agent 面)。",
        ),
        Capability(
            name = "导出与复制记录正文",
            ui = listOf("RecordDetailViewModel.exportJson", "RecordDetailScreen.copyFull", "MonitorRecordsScreen.copySummary"),
            tools = emptyList(),
            reason = "UI 专属:剪贴板 / 文件落盘是人机动作,Agent 侧 record_get 已返回同一份全量结构化内容," +
                "再开一个「导出到设备文件」工具只会多一条绕过响应通道的写盘路径。",
        ),
        Capability(
            name = "MCP 自身控制面(启停 / 端口 / 认证开关)",
            ui = listOf(
                "ServicesViewModel.setMcpEnabled",
                "ServicesViewModel.setMcpPort",
                "ServicesViewModel.setMcpTokenAuth",
            ),
            tools = emptyList(),
            reason = "UI 专属(自毁型控制面,AGENTS #26 + McpToolParityTest.self_disabling_control_plane):" +
                "停用 / 改端口 / 开认证都会终结当前连接,Agent 一旦调用即把自己关在门外。",
        ),
        Capability(
            name = "令牌管理",
            ui = listOf(
                "McpTokensViewModel.setTokenAuth",
                "McpTokensViewModel.addToken",
                "McpTokensViewModel.setTokenLabel",
                "McpTokensViewModel.revokeToken",
            ),
            tools = emptyList(),
            reason = "UI 专属(引导悖论 + PII,见 McpToolParityTest.token_management_is_documented_exclusion)。",
        ),
    )

    /**
     * 共享可变上游的**成对例外**(键 = `接收者.方法`,值 = 为什么这不是重复广告)。
     * 新增共享必须先在此登记理由,否则 R1 红 —— 例外是审计面,不是逃生门。
     */
    private val sharedUpstreamExceptions = mapOf(
        "settings.setOverride" to "listen/feature 两面写同一份 AppListenOverride 存储,由 CategoryTaxonomy 双域闸分列(AGENTS #23)",
        "settings.resetOverride" to "同一契约方法带**域参数** = 两个正交动作(V45 T3.5/DUAL-12:按域清,互不越界)",
        "settings.setGlobalFeature" to "FeatureConfig 是一个聚合(画像 + 黑名单追加),两面各写自己的段",
    )

    /** 参与 R1 判定的上游接收者(全部是方法层契约,不是实现类型)。 */
    private val upstreamReceivers = listOf(
        "logs", "settings", "keywords", "listPrefs", "scopeRepository", "catalog", "mcpServer",
    )

    private val mutatorName = Regex(
        """\.(set|add|remove|reset|clear|delete|update|create|toggle|apply|write|push|revoke)[A-Za-z]*\(""",
    )

    private fun toolSourceFiles(): List<File> {
        val dir = File(sourceRoot, "mcp/tools")
        assertTrue("mcp tools dir missing: $dir", dir.isDirectory)
        return dir.listFiles { f: File -> f.name.endsWith("Tools.kt") }?.toList().orEmpty()
    }

    /** 每个工具名 → 该工具函数体文本(schemaTool 声明到下一个同类声明之间)。 */
    private fun toolBlocks(): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        val declPattern = Regex("""private fun \w+\(\)\s*=\s*schemaTool\(""")
        val namePattern = Regex("name\\s*=\\s*\"([a-z_]+)\"")
        toolSourceFiles().forEach { file ->
            val text = file.readText()
            val starts = declPattern.findAll(text).map { it.range.first }.toList()
            starts.forEachIndexed { i, start ->
                val end = starts.getOrNull(i + 1) ?: text.length
                val block = text.substring(start, end)
                val name = namePattern.find(block)?.groupValues?.get(1) ?: return@forEachIndexed
                out[name] = block
            }
        }
        return out
    }

    @Test
    fun r1_no_two_tools_advertise_the_same_mutation() {
        val byUpstream = LinkedHashMap<String, MutableList<String>>()
        toolBlocks().forEach { (name, block) ->
            // 去注释后扫,避免 KDoc 里的示例调用被当真实上游
            val code = stripComments(block)
            upstreamReceivers.forEach { receiver ->
                Regex(Regex.escape("$receiver.") + """(\w+)\(""").findAll(code).forEach { m ->
                    val method = m.groupValues[1]
                    if (mutatorName.matches(".$method(")) {
                        byUpstream.getOrPut("$receiver.$method") { mutableListOf() } += name
                    }
                }
            }
        }
        val duplicates = byUpstream.filter { (upstream, tools) ->
            tools.distinct().size > 1 && !sharedUpstreamExceptions.containsKey(upstream)
        }
        assertEquals(
            "同一可变上游被多个工具广告(DUAL-11 形态);确属设计请登记理由:\n" +
                duplicates.map { (k, v) -> "$k -> ${v.distinct()}" }.joinToString("\n"),
            emptyMap<String, List<String>>(),
            duplicates,
        )
        // 例外清单不得变成死条款(上游已不再共享 = 该删)
        val dead = sharedUpstreamExceptions.keys.filterNot { upstream ->
            byUpstream[upstream]?.distinct()?.size?.let { it > 1 } == true
        }
        assertTrue("登记了但已不再共享上游的例外需删除: $dead", dead.isEmpty())
    }

    @Test
    fun r2_every_tool_is_owned_by_exactly_one_capability() {
        val declared = toolBlocks().keys
        assertEquals("工具面快照应为 45", 45, declared.size)
        val owned = capabilities.flatMap { it.tools }
        val unowned = declared - owned.toSet()
        val phantom = owned - declared.toSet()
        val duplicated = owned.groupBy { it }.filterValues { it.size > 1 }.keys
        assertTrue("未归属任何能力行的工具: $unowned", unowned.isEmpty())
        assertTrue("能力行引用了不存在的工具: $phantom", phantom.isEmpty())
        assertTrue("同一工具被多行重复归属: $duplicated", duplicated.isEmpty())
    }

    @Test
    fun r3_single_sided_rows_must_carry_reason() {
        val lazy = capabilities.filter { row ->
            (row.ui.isEmpty() || row.tools.isEmpty()) && row.reason.isBlank()
        }
        assertTrue("单侧能力缺理由: ${lazy.map { it.name }}", lazy.isEmpty())
    }

    /**
     * UI 侧入口必须真实存在(V45 T3.4/DUAL-11 的同型治理:声明面不得写一个不存在的符号)。
     * 扫描面 = ui 子树全部源码;`Screen` 级条目(如 `DiagnosticsScreen.copyDiagnostics`)按函数名命中。
     */
    @Test
    fun r4_ui_entries_referenced_by_the_matrix_exist() {
        val uiCode = File(sourceRoot, "ui").walkTopDown()
            .filter { it.isFile && it.name.endsWith(".kt") }
            .joinToString("\n") { stripComments(it.readText()) }
        val missing = capabilities.flatMap { it.ui }.distinct().filterNot { entry ->
            val symbol = entry.substringAfterLast('.')
            Regex("""\bfun ${Regex.escape(symbol)}\(""").containsMatchIn(uiCode) ||
                Regex("""\bval ${Regex.escape(symbol)}\b""").containsMatchIn(uiCode)
        }
        assertTrue("能力行引用了 UI 侧不存在的动作: $missing", missing.isEmpty())
    }

    /** 反向覆盖:ViewModel 里的可变动作必须被某行认领(新增 UI 动作不登记即红)。 */
    @Test
    fun r5_every_viewmodel_mutation_is_claimed_by_a_capability() {
        val claimed = capabilities.flatMap { it.ui }.map { it.substringAfterLast('.') }.toSet()
        val unclaimed = viewModelFunNames().filter { name ->
            mutatorName.matches(".$name(") && name !in claimed
        }
        assertTrue(
            "ViewModel 新增可变动作未登记能力行(UI/MCP 对称性缺口;要么补行,要么在此登记理由):\n" +
                unclaimed.joinToString("\n"),
            unclaimed.isEmpty(),
        )
    }

    /** 扫 `class *ViewModel(...) : ViewModel()` 体内的 `fun 名(`(只认 4 空格缩进的类成员)。 */
    private fun viewModelFunNames(): List<String> {
        val names = mutableListOf<String>()
        File(sourceRoot, "ui").walkTopDown()
            .filter { it.isFile && it.name.endsWith(".kt") }
            .forEach { file ->
                var insideViewModel = false
                stripComments(file.readText()).lineSequence().forEach { line ->
                    when {
                        Regex("""^class \w+ViewModel\b""").containsMatchIn(line) -> insideViewModel = true
                        Regex("""^\}""").containsMatchIn(line) -> insideViewModel = false
                        insideViewModel -> {
                            Regex("""^    fun (\w+)\(""").find(line)?.let { names += it.groupValues[1] }
                        }
                    }
                }
            }
        return names
    }

    /**
     * 去注释但**保留行结构**(整块替换成空格而非删除):
     * 用 `Regex.replace(text, " ")` 会把跨行注释连同换行一起吃掉,
     * 于是注释后面的 `class XViewModel(` 变成「有前导空格的一行」,
     * 行首锚定的扫描会静默不命中 = 假绿(V45 门禁自我实锤过的形态)。
     */
    private fun stripComments(text: String): String {
        val out = StringBuilder(text)
        val block = Regex("/\\*[\\s\\S]*?\\*/")
        block.findAll(text).forEach { m ->
            for (i in m.range) if (out[i] != '\n' && out[i] != '\r') out[i] = ' '
        }
        val line = Regex("//[^\n]*")
        line.findAll(out.toString()).forEach { m ->
            for (i in m.range) if (out[i] != '\n' && out[i] != '\r') out[i] = ' '
        }
        return out.toString()
    }

    /** 防漂移:能力行的记录类型覆盖面必须与 [RecordType] 同步(新增类型要补断言)。 */
    @Test
    fun r6_record_type_surface_is_ten() {
        assertEquals(10, RecordType.entries.size)
    }
}
