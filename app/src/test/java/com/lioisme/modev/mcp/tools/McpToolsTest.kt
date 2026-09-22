package com.lioisme.modev.mcp.tools

import androidx.room.Room
import com.google.gson.JsonObject
import com.lioisme.modev.core.model.AppListenOverride
import com.lioisme.modev.core.model.BehaviorKind
import com.lioisme.modev.core.model.KeywordMatchMode
import com.lioisme.modev.core.model.RecordTypes
import com.lioisme.modev.data.db.ModevDatabase
import com.lioisme.modev.data.repo.KeywordLibraryRepository
import com.lioisme.modev.data.repo.LogRepository
import com.lioisme.modev.data.repo.SettingsRepository
import com.lioisme.modev.data.repo.SpoofProfileRepository
import com.lioisme.modev.data.server.ConfigServer
import com.lioisme.modev.domain.record.RecordFilter
import com.lioisme.modev.core.model.CryptoKind
import com.lioisme.modev.core.model.CryptoOperation
import com.lioisme.modev.core.model.CryptoRecord
import com.lioisme.modev.mcp.protocol.McpProtocol
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 工具面分组门禁(V38,Robolectric 真仓库):记录 CRUD 闭环、配置 merge 三态语义、
 * 资产推送、parity 快照(50 工具冻结 + 上游 allowlist)。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class McpToolsTest {

    private lateinit var db: ModevDatabase
    private lateinit var logs: LogRepository
    private lateinit var settings: SettingsRepository
    private lateinit var keywords: KeywordLibraryRepository
    private lateinit var profiles: SpoofProfileRepository
    private lateinit var recordTools: RecordTools
    private lateinit var listenTools: ListenConfigTools
    private lateinit var featureTools: FeatureConfigTools
    private lateinit var assetTools: GlobalAssetTools
    private lateinit var profileTools: SpoofProfileTools

    /** 全局资产库(关键词 + 画像)收集器 scope:随测试关闭,防 arch_disk_io 线程泄漏污染后续 Robolectric 用例。 */
    private lateinit var libraryScope: kotlinx.coroutines.CoroutineScope

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, ModevDatabase::class.java).build()
        val server = ConfigServer()
        logs = LogRepository(db.cryptoRecordDao(), server)
        settings = SettingsRepository(
            server = server,
            writer = object : com.lioisme.modev.data.repo.ConfigWriter {
                private val map = mutableMapOf<String, String>()
                override fun set(switch: com.lioisme.modev.core.model.HookSwitch) {
                    map["switch_global"] = switch.let { com.google.gson.Gson().toJson(it) }
                }
                override fun get(): com.lioisme.modev.core.model.HookSwitch? =
                    map["switch_global"]?.let { com.lioisme.modev.core.model.HookSwitch.fromJson(com.google.gson.Gson(), it) }
                override fun migrateLegacy() {}
                override fun put(key: String, value: String) { map[key] = value }
                override fun remove(key: String) { map.remove(key) }
                override fun getLocal(key: String): String? = map[key]
                override fun localKeysWithPrefix(prefix: String): Set<String> = map.keys.filter { it.startsWith(prefix) }.toSet()
            },
            gson = com.google.gson.Gson(),
            localPrefs = context.getSharedPreferences("test_settings", 0),
        )
        libraryScope = kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO,
        )
        keywords = KeywordLibraryRepository(db.keywordLibraryDao(), libraryScope)
        // 画像库(V47 WP-7):与 AppContainer 同序补内置三档,spoof_profiles_list 才有真身可列
        profiles = SpoofProfileRepository(db.spoofProfileDao(), libraryScope)
        runBlocking { profiles.synchronizeBuiltins() }
        // init 需要装配关键词库(与 AppContainer 同序)
        settings.attachKeywordLibrary(keywords)
        recordTools = RecordTools(logs)
        listenTools = ListenConfigTools(settings)
        featureTools = FeatureConfigTools(settings)
        assetTools = GlobalAssetTools(keywords, settings, profiles)
        profileTools = SpoofProfileTools(profiles, settings)
    }

    @After
    fun tearDown() {
        // 先取消并等待收集器退出,再关库:防 Room 磁盘 IO 线程在库关闭后
        // 仍被发射路径触达(Illegal connection pointer 异步污染后续用例)。
        runBlocking {
            kotlinx.coroutines.withTimeoutOrNull(3_000) {
                libraryScope.coroutineContext[kotlinx.coroutines.Job]?.cancelAndJoin()
            }
        }
        db.close()
    }

    private fun draft(kind: String, pkg: String = "pkg.a", ts: Long = 100) = CryptoRecord(
        packageName = pkg,
        kind = CryptoKind.valueOf(kind),
        operation = CryptoOperation.ENCRYPT,
        algorithm = "AES",
        threadName = "main",
        timestamp = ts,
    )

    /** 关键词库 StateFlow 为异步 Room 流:轮询等待(5s 超时)。 */
    private fun awaitKeywordCount(size: Int) {
        val deadline = System.currentTimeMillis() + 5_000
        while (keywords.entries.value.size != size && System.currentTimeMillis() < deadline) {
            Thread.sleep(50)
        }
    }

    // ---- 记录组 ----

    @Test
    fun records_query_count_get_delete_roundtrip() = runBlocking {
        val id = logs.create(draft("CIPHER"))
        val query = recordTools.tools.first { it.name == "records_query" }
        val out = query.execute(JsonObject())
        val result = out.get("total")?.let { out } ?: out
        assertEquals(1L, out.get("total").asLong)
        assertEquals(1, out.get("items").asJsonArray.size())
        assertEquals(id, out.get("items").asJsonArray[0].asJsonObject.get("id").asLong)

        val count = recordTools.tools.first { it.name == "records_count" }
        assertEquals(1L, count.execute(JsonObject()).get("count").asLong)

        val get = recordTools.tools.first { it.name == "record_get" }
        val detail = get.execute(JsonObject().apply { addProperty("id", id) })
        assertEquals("crypto", detail.get("recordType").asString)
        assertEquals("AES", detail.get("record").asJsonObject.get("algorithm").asString)

        val del = recordTools.tools.first { it.name == "record_delete" }
        assertTrue(del.execute(JsonObject().apply { addProperty("id", id) }).get("ok").asBoolean)
        assertEquals(0L, logs.count(RecordFilter()).first())
    }

    @Test
    fun records_query_filter_by_kind() = runBlocking {
        logs.create(draft("CIPHER"))
        logs.create(
            CryptoRecord(
                packageName = "pkg.a",
                kind = CryptoKind.MAC,
                operation = CryptoOperation.SIGN,
                algorithm = "HmacSHA256",
                threadName = "main",
            ),
        )
        val query = recordTools.tools.first { it.name == "records_query" }
        val out = query.execute(JsonObject().apply { addProperty("kind", "MAC") })
        assertEquals(1L, out.get("total").asLong)
        assertEquals("MAC", out.get("items").asJsonArray[0].asJsonObject.get("kind").asString)
    }

    @Test
    fun records_clear_all_requires_confirm() = runBlocking {
        logs.create(draft("CIPHER"))
        val clearAll = recordTools.tools.first { it.name == "records_clear_all" }
        // 无 confirm → 业务错误
        val err = runCatching { clearAll.execute(JsonObject()) }
        assertTrue(err.isFailure)
        // confirm=false → 业务错误
        val err2 = runCatching { clearAll.execute(JsonObject().apply { addProperty("confirm", false) }) }
        assertTrue(err2.isFailure)
        assertEquals(1L, logs.count(RecordFilter()).first())
        // confirm=true → 清空
        clearAll.execute(JsonObject().apply { addProperty("confirm", true) })
        assertEquals(0L, logs.count(RecordFilter()).first())
    }

    @Test
    fun records_type_counts_and_storage() = runBlocking {
        logs.create(draft("CIPHER"))
        val typeCounts = recordTools.tools.first { it.name == "records_type_counts" }
        val counts = typeCounts.execute(JsonObject()).get("counts").asJsonArray
        val crypto = counts.first { it.asJsonObject.get("recordType").asString == "crypto" }
        assertEquals(1L, crypto.asJsonObject.get("count").asLong)
        val storage = recordTools.tools.first { it.name == "storage_usage" }
        assertTrue(storage.execute(JsonObject()).get("bytes").asLong >= 0)
    }

    // ---- 监听配置组 ----

    @Test
    fun listen_settings_set_merge_and_null_reset() = runBlocking {
        val set = listenTools.tools.first { it.name == "listen_settings_set" }
        // 关 FILE_READ(行为 kind)
        val args = JsonObject().apply {
            addProperty("packageName", "pkg.a")
            add("kindEntries", JsonObject().apply { addProperty(BehaviorKind.FILE_READ.name, false) })
        }
        val out = set.execute(args)
        assertEquals(1, out.get("deviationCount").asInt)
        // override 收敛:裸 FILE_READ 归一为限定键 behavior:FILE_READ;effective 平铺传导
        val override = out.get("override").asJsonObject
        assertEquals(false, override.get("kinds").asJsonObject.get("behavior:FILE_READ").asBoolean)
        val effectiveSwitch = com.lioisme.modev.core.model.HookSwitch.fromJson(
            McpJson.gson, out.get("effective").asJsonObject.toString(),
        )!!
        assertEquals(
            false,
            com.lioisme.modev.core.model.EffectiveSwitch.readKind(
                effectiveSwitch,
                com.lioisme.modev.core.model.RecordType.BEHAVIOR,
                BehaviorKind.FILE_READ.name,
            ),
        )
        // V44:kindEntries 未知键显式拒绝(不再静默入库后被白名单丢弃)
        assertTrue(
            runCatching {
                set.execute(JsonObject().apply {
                    addProperty("packageName", "pkg.a")
                    add("kindEntries", JsonObject().apply { addProperty("bogus:NOT_A_KIND", true) })
                })
            }.isFailure,
        )
        // master 三态:crypto master = true
        val masterOut = set.execute(JsonObject().apply {
            addProperty("packageName", "pkg.a")
            addProperty("master", "crypto")
            addProperty("masterValue", true)
        })
        assertEquals(2, masterOut.get("deviationCount").asInt)
        // null masterValue 回默认
        val resetMaster = set.execute(JsonObject().apply {
            addProperty("packageName", "pkg.a")
            addProperty("master", "crypto")
            add("masterValue", com.google.gson.JsonNull.INSTANCE)
        })
        assertEquals(1, resetMaster.get("deviationCount").asInt)
        // reset 全部
        val reset = listenTools.tools.first { it.name == "listen_settings_reset" }
        val clean = reset.execute(JsonObject().apply { addProperty("packageName", "pkg.a") })
        assertEquals(0, clean.get("deviationCount").asInt)
    }

    /**
     * V45 T0.2(DUAL-1 根因治理):工具必须经 [McpToolRegistry] 的 schema 校验路径可达。
     * 旧形态把 `masterValue` 声明为 string 而实现 `require(isBoolean)` —— 真协议路径两种取值
     * 全被拒(10 个 master 开关经 MCP 完全不可用),而直调 `execute()` 的用例照常全绿 = 门禁盲区。
     * 本用例锁「schema 声明与实现语义互逆」。
     */
    @Test
    fun registry_schema_and_handler_agree_on_masterValue_tristate() = runBlocking {
        val registry = McpToolRegistry(listenTools.tools + featureTools.tools)
        suspend fun call(tool: String, masterValue: com.google.gson.JsonElement): McpToolRegistry.Outcome =
            registry.call(tool, JsonObject().apply {
                addProperty("packageName", "pkg.a")
                addProperty("master", if (tool.startsWith("listen")) "crypto" else "ui")
                add("masterValue", masterValue)
            }) { t, a -> t.execute(a) }

        // ① 布尔:协议路径必须成功(回归 = 旧形态 -32602 expects string)
        val ok = call("listen_settings_set", com.google.gson.JsonPrimitive(true))
        assertTrue("boolean masterValue must pass registry, got $ok", ok is McpToolRegistry.Outcome.Ok)
        assertEquals(
            1,
            (ok as McpToolRegistry.Outcome.Ok).result.getAsJsonObject("structuredContent")
                .get("deviationCount").asInt,
        )
        // ② 字符串:schema 已声明 boolean → 协议层拒(不再穿透成业务错)
        val bad = call("listen_settings_set", com.google.gson.JsonPrimitive("true"))
        assertTrue("string masterValue must be a protocol error, got $bad", bad is McpToolRegistry.Outcome.ProtocolError)
        assertEquals(com.lioisme.modev.mcp.protocol.JsonRpc.INVALID_PARAMS, (bad as McpToolRegistry.Outcome.ProtocolError).code)
        // ③ JSON null:类型豁免 + 「回默认」语义(偏差清零)
        val reset = call("listen_settings_set", com.google.gson.JsonNull.INSTANCE)
        assertTrue("null masterValue must pass registry, got $reset", reset is McpToolRegistry.Outcome.Ok)
        assertEquals(
            0,
            (reset as McpToolRegistry.Outcome.Ok).result.getAsJsonObject("structuredContent")
                .get("deviationCount").asInt,
        )
        // ④ 功能面同构声明(两侧 schema 必须一致)
        listOf(listenTools.tools, featureTools.tools).forEach { group ->
            val schema = group.first { it.name.endsWith("_settings_set") }.inputSchema()
            assertEquals(
                "boolean",
                schema.getAsJsonObject("properties").getAsJsonObject("masterValue").get("type").asString,
            )
        }
    }

    /**
     * V45 T2.4 / DUAL-10 双域闸:同一记录类型内跨域写必须被拒
     * (UI 两页按 `CategoryTaxonomy.domain` 分列,MCP 此前可越域直写 → 双端语义分叉)。
     */
    @Test
    fun kindEntries_are_gated_by_capability_domain() = runBlocking {
        val registry = McpToolRegistry(listenTools.tools + featureTools.tools)
        val listenKey = com.lioisme.modev.core.model.AppListenOverride.kindKey(
            com.lioisme.modev.core.model.RecordType.UI_INTERACTION,
            "DIALOG_SHOW", // LISTEN 域
        )
        val featureKey = com.lioisme.modev.core.model.AppListenOverride.kindKey(
            com.lioisme.modev.core.model.RecordType.UI_INTERACTION,
            "DIALOG_KEYWORD", // FEATURE 域
        )

        suspend fun set(tool: String, key: String): McpToolRegistry.Outcome =
            registry.call(tool, JsonObject().apply {
                addProperty("packageName", "pkg.a")
                add("kindEntries", JsonObject().apply { addProperty(key, true) })
            }) { t, a -> t.execute(a) }

        // ① 监听工具写功能域类别 → 业务错(非协议错:参数形态合法,语义越域)
        val crossIntoListen = set("listen_settings_set", featureKey)
        assertTrue("FEATURE kind must be rejected by listen tool: $crossIntoListen",
            crossIntoListen is McpToolRegistry.Outcome.BusinessError)
        // ② 功能工具写监听域类别 → 业务错(镜像面)
        val crossIntoFeature = set("feature_settings_set", listenKey)
        assertTrue("LISTEN kind must be rejected by feature tool: $crossIntoFeature",
            crossIntoFeature is McpToolRegistry.Outcome.BusinessError)
        // ③ 同域正写必须通过(防「闸门焊死」的假绿)
        assertTrue("LISTEN kind must pass listen tool",
            set("listen_settings_set", listenKey) is McpToolRegistry.Outcome.Ok)
        assertTrue("FEATURE kind must pass feature tool",
            set("feature_settings_set", featureKey) is McpToolRegistry.Outcome.Ok)
    }

    @Test
    fun taxonomy_listen_has_44_entries() = kotlinx.coroutines.runBlocking {
        val taxonomy = listenTools.tools.first { it.name == "taxonomy_listen" }
        val categories = taxonomy.execute(JsonObject()).get("categories").asJsonArray
        assertEquals(44, categories.size())
        assertTrue(categories.all { it.asJsonObject.get("domain").asString == "LISTEN" })
    }

    // ---- 功能配置组 ----

    @Test
    fun feature_settings_set_spoof_and_blacklist() = runBlocking {
        val set = featureTools.tools.first { it.name == "feature_settings_set" }
        val out = set.execute(JsonObject().apply {
            addProperty("packageName", "pkg.a")
            addProperty("spoofProfileId", "pixel7pro")
            add("extraRootPackages", com.google.gson.JsonArray().apply { add("com.example.root") })
        })
        val ovr = out.get("override").asJsonObject
        assertEquals("pixel7pro", ovr.get("spoofProfileId").asString)
        // effective.feature 传导
        val feature = out.get("effective").asJsonObject.get("feature").asJsonObject
        assertEquals("pixel7pro", feature.get("spoofProfileId").asString)
        assertEquals("com.example.root", feature.get("extraRootPackages").asJsonArray[0].asString)
    }

    @Test
    fun taxonomy_feature_has_37_kinds_in_6_groups() = kotlinx.coroutines.runBlocking {
        val taxonomy = featureTools.tools.first { it.name == "taxonomy_feature" }
        val groups = taxonomy.execute(JsonObject()).get("groups").asJsonArray
        assertEquals(6, groups.size())
        assertEquals(37, groups.sumOf { it.asJsonObject.get("kinds").asJsonArray.size() })
    }

    // ---- 全局资产组 ----

    @Test
    fun keywords_add_list_remove_flow() = runBlocking {
        val add = assetTools.tools.first { it.name == "keywords_add" }
        val out = add.execute(JsonObject().apply {
            addProperty("text", "ModevKeywordHit")
            addProperty("matchMode", "CONTAINS")
        })
        assertTrue(out.get("ok").asBoolean)
        awaitKeywordCount(1)
        val list = assetTools.tools.first { it.name == "keywords_list" }
        val entries = list.execute(JsonObject()).get("entries").asJsonArray
        assertEquals(1, entries.size())
        assertEquals("ModevKeywordHit", entries[0].asJsonObject.get("text").asString)
        // 重复 → false
        val dup = add.execute(JsonObject().apply { addProperty("text", "ModevKeywordHit") })
        assertFalse(dup.get("ok").asBoolean)
        // remove
        val remove = assetTools.tools.first { it.name == "keywords_remove" }
        remove.execute(JsonObject().apply { addProperty("id", entries[0].asJsonObject.get("id").asLong) })
        awaitKeywordCount(0)
        assertEquals(0, list.execute(JsonObject()).get("entries").asJsonArray.size())
    }

    @Test
    fun spoof_and_blacklist_global_flow() = runBlocking {
        val profiles = assetTools.tools.first { it.name == "spoof_profiles_list" }
        val listed = profiles.execute(JsonObject())
        assertEquals(3, listed.get("profiles").asJsonArray.size())
        // set pixel7pro
        val setProfile = assetTools.tools.first { it.name == "spoof_global_set" }
        val out = setProfile.execute(JsonObject().apply { addProperty("profileId", "pixel7pro") })
        assertEquals("pixel7pro", out.get("profileId").asString)
        // blacklist add/remove
        val edit = assetTools.tools.first { it.name == "blacklist_edit" }
        edit.execute(JsonObject().apply {
            addProperty("kind", "rootPackages")
            addProperty("action", "add")
            add("values", com.google.gson.JsonArray().apply { add("com.demo.root") })
        })
        val list = assetTools.tools.first { it.name == "blacklist_list" }
        val black = list.execute(JsonObject())
        assertEquals("com.demo.root", black.get("extraRootPackages").asJsonArray[0].asString)
        edit.execute(JsonObject().apply {
            addProperty("kind", "rootPackages")
            addProperty("action", "remove")
            add("values", com.google.gson.JsonArray().apply { add("com.demo.root") })
        })
        assertEquals(0, list.execute(JsonObject()).get("extraRootPackages").asJsonArray.size())
    }

    // ---- parity 门禁 ----

    @Test
    fun parity_tools_frozen_snapshot_32_assembled() {
        val names = recordTools.tools + listenTools.tools + featureTools.tools + assetTools.tools
        // 本测试不装配 App/Server/Scope(需 Android 全家桶),快照断言覆盖 32 个;
        // 全量 50 断言在 McpToolParityTest(源码级,防漂移)。
        assertEquals(32, names.size)
        val expected = setOf(
            "records_query", "records_count", "records_type_counts", "records_category_counts",
            "record_get", "record_neighbor", "record_create", "record_update", "record_delete",
            "records_delete_by_filter", "records_clear_package", "records_clear_type", "records_clear_all",
            "storage_usage",
            "listen_settings_get", "listen_settings_set", "listen_settings_reset", "taxonomy_listen",
            "feature_settings_get", "feature_settings_set", "feature_settings_reset", "taxonomy_feature",
            "keywords_list", "keywords_add", "keywords_update", "keywords_remove",
            "keyword_set_enabled", "keywords_clear",
            "spoof_profiles_list", "spoof_global_set", "blacklist_list", "blacklist_edit",
        )
        assertEquals(expected, names.map { it.name }.toSet())
        // schema 非空
        names.forEach { tool ->
            assertNotNull(tool.name, tool.inputSchema().getAsJsonObject("properties"))
        }
    }

    @Test
    fun keyword_rules_flow_into_effective_switch() = runBlocking {
        keywords.add("ModevKeywordHit", KeywordMatchMode.CONTAINS)
        awaitKeywordCount(1)
        val get = listenTools.tools.first { it.name == "listen_settings_get" }
        val out = get.execute(JsonObject().apply { addProperty("packageName", "pkg.a") })
        val rules = out.get("effective").asJsonObject.get("feature").asJsonObject.get("dialogKeywordRules").asJsonArray
        assertTrue("library keyword must flow into effective rules", rules.size() >= 1)
    }

    // ---- V45 T3.2:schema 与实现镜像(DUAL-6) ----

    private fun allTools(): List<McpTool> =
        recordTools.tools + listenTools.tools + featureTools.tools + assetTools.tools + profileTools.tools

    private suspend fun call(
        tool: String,
        args: JsonObject,
        tools: List<McpTool> = allTools(),
    ): McpToolRegistry.Outcome = McpToolRegistry(tools).call(tool, args) { t, a -> t.execute(a) }

    private fun propertiesOf(tool: String): Set<String> =
        allTools().first { it.name == tool }.inputSchema().getAsJsonObject("properties").keySet()

    /**
     * 写入面 schema **派生自模型**:旧手抄表把存储列名当参数广告
     * (`contentHex`/`prefKey`/`httpMethod`/`keyLength`/`bindArgsJson`/`paramsJson`…),
     * 而 handler 只收类型化记录字段(`content`/`key`/`method`/`bindArgs`/`params`),
     * 于是「照 schema 写记录必被拒、真字段无处可查」。本用例锁反方向:虚构键恒不存在、
     * 真字段恒在广告面。
     */
    @Test
    fun record_schema_advertises_model_fields_not_storage_columns() {
        val props = propertiesOf("record_create")
        listOf(
            "contentHex", "contentB64", "contentText", "prefKey", "httpMethod", "keyLength",
            "keyHex", "keyB64", "keyText", "ivHex", "inputHex", "outputHex", "saltHex", "aadHex",
            "publicKeyHex", "paramsJson", "bindArgsJson", "rowsJson", "rowsNdjson",
        ).forEach { fictional ->
            assertFalse("fictional storage-column key must be gone: $fictional", fictional in props)
        }
        listOf(
            "recordType", "packageName", "kind", "operation", "algorithm", "key", "iv", "input",
            "output", "params", "stack", "content", "bindArgs", "rows", "method", "statusCode",
            "logcatMessage", "interventionAction", "timestamp", "threadName",
        ).forEach { real ->
            assertTrue("model field must be advertised: $real", real in props)
        }
        // 过滤面参数不得混进写入面(旧形态两者共用一张 60 键平铺表)
        listOf("query", "sort", "timeStart", "timeEnd", "page", "pageSize").forEach {
            assertFalse("filter-only param must not appear on record_create: $it", it in props)
        }
    }

    /** 逐参数矩阵:声明即生效 —— 未声明参数在协议层硬拒,不存在「静默忽略」灰区。 */
    @Test
    fun undeclared_parameter_is_protocol_error() = runBlocking {
        val unknown = call("records_count", JsonObject().apply { addProperty("page", 1) })
        assertTrue("undeclared page must be -32602, got $unknown", unknown is McpToolRegistry.Outcome.ProtocolError)
        assertEquals(
            com.lioisme.modev.mcp.protocol.JsonRpc.INVALID_PARAMS,
            (unknown as McpToolRegistry.Outcome.ProtocolError).code,
        )
        // 分页参数只在 records_query 生效(T3.2 拆分后 count/delete 不再广告)
        assertTrue("page must stay declared for records_query", "page" in propertiesOf("records_query"))
        assertFalse("records_count must not advertise page", "page" in propertiesOf("records_count"))
        assertFalse("records_delete_by_filter must not advertise page", "page" in propertiesOf("records_delete_by_filter"))
        val ok = call("records_query", JsonObject().apply { addProperty("page", 0) })
        assertTrue(ok is McpToolRegistry.Outcome.Ok)
    }

    /** required 显式 null 与缺失同一分型(T3.2:旧形态一个是 -32602、一个是 isError)。 */
    @Test
    fun required_param_explicit_null_is_protocol_error() = runBlocking {
        val missing = call("record_get", JsonObject())
        val nullish = call("record_get", JsonObject().apply { add("id", com.google.gson.JsonNull.INSTANCE) })
        assertTrue("$missing", missing is McpToolRegistry.Outcome.ProtocolError)
        assertTrue("$nullish", nullish is McpToolRegistry.Outcome.ProtocolError)
        assertEquals(
            (missing as McpToolRegistry.Outcome.ProtocolError).code,
            (nullish as McpToolRegistry.Outcome.ProtocolError).code,
        )
        // 非 required 的 null 保留「回默认」语义(类型豁免不变)
        val reset = call(
            "listen_settings_set",
            JsonObject().apply {
                addProperty("packageName", "pkg.a")
                add("masterValue", com.google.gson.JsonNull.INSTANCE)
            },
        )
        assertTrue("$reset", reset is McpToolRegistry.Outcome.Ok)
    }

    /** 声明了 enum 就由 schema 自己生效:非法枚举 = 协议错误,不再穿透成业务错误。 */
    @Test
    fun enum_violation_is_protocol_error() = runBlocking {
        val badMode = call("keywords_add", JsonObject().apply {
            addProperty("text", "ModevEnumProbe")
            addProperty("matchMode", "contains") // 真值 = CONTAINS(枚举名大小写敏感)
        })
        assertTrue("$badMode", badMode is McpToolRegistry.Outcome.ProtocolError)
        val badSort = call("records_query", JsonObject().apply { addProperty("sort", "ASC") })
        assertTrue("sort must be enum-checked: $badSort", badSort is McpToolRegistry.Outcome.ProtocolError)
        val badType = call("records_count", JsonObject().apply { addProperty("recordType", "bogus") })
        assertTrue("$badType", badType is McpToolRegistry.Outcome.ProtocolError)
        val badProfile = call("spoof_global_set", JsonObject().apply { addProperty("profileId", "nexus5") })
        assertTrue("$badProfile", badProfile is McpToolRegistry.Outcome.ProtocolError)
        // 正例放行(防「枚举闸焊死」假绿)
        assertTrue(
            call("keywords_add", JsonObject().apply {
                addProperty("text", "ModevEnumProbe")
                addProperty("matchMode", "CONTAINS")
            }) is McpToolRegistry.Outcome.Ok,
        )
        assertTrue(
            call("records_query", JsonObject().apply { addProperty("sort", "asc") }) is McpToolRegistry.Outcome.Ok,
        )
    }

    /**
     * 三级分型矩阵(T3.2 验收):未声明键 = 协议错误、跨类型字段 = 业务错误、
     * 派生面字段 = 成功。证明「schema ≡ handler」不是单向声明而是双向可执行。
     */
    @Test
    fun payload_three_tier_dispatch() = runBlocking {
        // ① 模型里没有的键 → -32602(旧形态:进 handler 才报「未知字段」)
        val fictional = call("record_create", JsonObject().apply {
            addProperty("recordType", "behavior")
            addProperty("packageName", "pkg.a")
            addProperty("kind", "FILE_WRITE")
            addProperty("operation", "WRITE_ALL")
            addProperty("contentHex", "00ff")
        })
        assertTrue("contentHex must be refused at schema level: $fictional",
            fictional is McpToolRegistry.Outcome.ProtocolError)
        // ② 他类型字段(声明面有、本类型不收)→ 业务错误
        val crossType = call("record_create", JsonObject().apply {
            addProperty("recordType", "crypto")
            addProperty("packageName", "pkg.a")
            addProperty("kind", "CIPHER")
            addProperty("operation", "ENCRYPT")
            addProperty("threadName", "main")
            addProperty("algorithm", "AES")
            addProperty("sql", "SELECT 1")
        })
        assertTrue("cross-type sql must be refused by the per-type gate: $crossType",
            crossType is McpToolRegistry.Outcome.BusinessError)
        // ③ 派生面字段 → 成功
        val valid = call("record_create", JsonObject().apply {
            addProperty("recordType", "behavior")
            addProperty("packageName", "pkg.a")
            addProperty("kind", "FILE_WRITE")
            addProperty("operation", "WRITE_ALL")
            addProperty("threadName", "main")
            addProperty("path", "/data/local/tmp/modev-probe.txt")
            addProperty("fileSize", 12L)
            add(
                "content",
                JsonObject().apply {
                    addProperty("hex", "6162")
                    addProperty("text", "ab")
                    addProperty("totalLength", 2)
                    addProperty("truncated", false)
                },
            )
        })
        assertTrue("derived payload must be accepted: $valid", valid is McpToolRegistry.Outcome.Ok)
    }

    /** 读 → 改 → 写回闭环:record_get 的 `record` 原样回灌 record_update 必须成立。 */
    @Test
    fun record_get_payload_round_trips_through_record_update() = runBlocking {
        val id = logs.create(
            CryptoRecord(
                packageName = "pkg.a",
                kind = CryptoKind.CIPHER,
                operation = CryptoOperation.ENCRYPT,
                algorithm = "AES/CBC/PKCS5Padding",
                key = com.lioisme.modev.core.util.BytesView(
                    hex = "00112233", base64 = null, text = null, totalLength = 4, truncated = false,
                ),
                threadName = "main",
                stack = listOf("com.demo.Main.run(Main.kt:1)"),
            ),
        )
        val get = allTools().first { it.name == "record_get" }
        val detail = get.execute(JsonObject().apply { addProperty("id", id) })
        val record = detail.get("record").asJsonObject
        // 派生面必须覆盖读侧全部键(读得出就写得回)
        val declared = propertiesOf("record_create")
        val undeclared = record.keySet().filterNot { it in declared || it == "id" }
        assertTrue("record_get output keys missing from schema: $undeclared", undeclared.isEmpty())

        record.addProperty("algorithm", "AES/GCM/NoPadding")
        val update = call("record_update", JsonObject().apply {
            addProperty("id", id)
            add("record", record)
        })
        assertTrue("round-trip update failed: $update", update is McpToolRegistry.Outcome.Ok)

        val after = get.execute(JsonObject().apply { addProperty("id", id) })
        assertEquals("AES/GCM/NoPadding", after.get("record").asJsonObject.get("algorithm").asString)
        assertEquals(
            "key material must survive round-trip",
            "00112233",
            after.get("record").asJsonObject.getAsJsonObject("key").get("hex").asString,
        )
        assertEquals(1, after.get("stack").asJsonArray.size())
    }

    /** 注释面不得越权:FIELD_NOTES 的每个键必须在派生键集里(模型改名即红)。 */
    @Test
    fun field_notes_never_advertise_unknown_keys() {
        val derived = RecordPayloadSchema.mergedProperties().keySet()
        val stale = FIELD_NOTES.keys.filterNot { it in derived }
        assertTrue("stale payload field notes (not in model-derived schema): $stale", stale.isEmpty())
    }

    /** 派生 schema 与 handler 白名单是两个独立推导,必须逐类型同集合。 */
    @Test
    fun payload_schema_and_handler_agree_per_record_type() = runBlocking {
        com.lioisme.modev.core.model.RecordType.entries.forEach { type ->
            val schemaKeys = RecordPayloadSchema.keysOf(type)
            val empty = McpJson.gson.fromJson<com.lioisme.modev.core.model.MonitoredRecord>(
                "{}",
                RecordPayloadSchema.modelClassOf(type),
            )!!
            val gsonKeys = McpJson.gson.toJsonTree(empty).asJsonObject.keySet()
            assertEquals("payload keys must match for ${type.wire}", gsonKeys, schemaKeys)
        }
    }

    /**
     * 十型写入矩阵(V45 T3.2 验收主体):每型取其**已登记必填键 + 一个合法 kind/operation**
     * 真写一条记录。少登记一个「无默认值非空字段」→ Room NOT NULL 抛错即红;
     * 多登记一个 → 缺值检查报错亦红(两侧都逼着清单与模型同步)。
     */
    @Test
    fun record_write_matrix_covers_every_type() = runBlocking {
        com.lioisme.modev.core.model.RecordType.entries.forEach { type ->
            val kind = com.lioisme.modev.core.model.CategoryTaxonomy.forType(type).first().id
            val operation = com.lioisme.modev.core.model.CategoryTaxonomy.operationsOf(type).first()
            val args = JsonObject().apply {
                addProperty("recordType", type.wire)
                addProperty("packageName", "pkg.matrix")
                addProperty("kind", kind)
                addProperty("operation", operation)
                addProperty("threadName", "main")
            }
            WRITE_REQUIRED_BY_TYPE[type].orEmpty().forEach { key ->
                when (key) {
                    "algorithm" -> args.addProperty(key, "AES")
                    "databaseName" -> args.addProperty(key, "modev.db")
                    "processName" -> args.addProperty(key, "p1")
                    else -> args.addProperty(key, "probe")
                }
            }
            val outcome = call("record_create", args)
            assertTrue("create must succeed for ${type.wire}: $outcome", outcome is McpToolRegistry.Outcome.Ok)
            // 必填键缺一即业务错误,且消息点名(清单登记完整性的反向证明)
            WRITE_REQUIRED_BY_TYPE[type].orEmpty().forEach { key ->
                val pruned = args.deepCopy().apply { remove(key) }
                val bad = call("record_create", pruned) as? McpToolRegistry.Outcome.BusinessError
                assertTrue(
                    "missing $key on ${type.wire} must be a named refusal: $bad",
                    bad?.message?.contains(key) == true,
                )
            }
        }
        assertEquals(
            "one record per record type",
            com.lioisme.modev.core.model.RecordType.entries.size.toLong(),
            logs.count(RecordFilter()).first(),
        )
    }

    /**
     * 可选字段缺省 = Kotlin 默认值(V45 T3.2 根因治理):
     * Gson 默认用 Unsafe 实例化,**跳过全部默认值表达式** —— 旧形态下 `stack` 缺席落 null
     * (写路径 NPE)、`timestamp` 缺席落 0(记录停在 1970),而工具描述一直宣称
     * "missing optional fields fall back to defaults"。声明自此为真。
     */
    @Test
    fun record_create_honours_kotlin_defaults_for_omitted_optional_fields() = runBlocking {
        val before = System.currentTimeMillis()
        val outcome = call("record_create", JsonObject().apply {
            addProperty("recordType", "crypto")
            addProperty("packageName", "pkg.default")
            addProperty("kind", "CIPHER")
            addProperty("operation", "ENCRYPT")
            addProperty("algorithm", "AES")
            addProperty("threadName", "main")
        })
        assertTrue("omit-and-default create must succeed: $outcome", outcome is McpToolRegistry.Outcome.Ok)
        val id = (outcome as McpToolRegistry.Outcome.Ok)
            .result.getAsJsonObject("structuredContent").get("id").asLong
        val detail = recordTools.tools.first { it.name == "record_get" }
            .execute(JsonObject().apply { addProperty("id", id) })
        val record = detail.get("record").asJsonObject
        assertTrue("stack must default to an empty list, not null", record.get("stack").isJsonArray)
        assertEquals(0, record.get("stack").asJsonArray.size())
        assertTrue(
            "timestamp must default to now, not 0",
            record.get("timestamp").asLong >= before,
        )
        assertEquals("behavior default", true, record.get("keyExportable").asBoolean)
    }

    /**
     * V45 T3.5 / DUAL-12 双端同口径:两面 reset **只清自己那域**。
     * 旧形态 listen_settings_reset 与 feature_settings_reset 都调全量删键 ——
     * 在监听页/监听工具点恢复会连带抹掉功能页定制(画像、黑名单、关键词子集),反之亦然。
     */
    @Test
    fun reset_is_scoped_to_its_own_domain_on_both_surfaces() = runBlocking {
        val pkg = "pkg.reset"
        // 两域各写一条偏差
        assertTrue(
            call("listen_settings_set", JsonObject().apply {
                addProperty("packageName", pkg)
                add("kindEntries", JsonObject().apply { addProperty("behavior:FILE_READ", false) })
            }) is McpToolRegistry.Outcome.Ok,
        )
        assertTrue(
            call("feature_settings_set", JsonObject().apply {
                addProperty("packageName", pkg)
                addProperty("spoofProfileId", "pixel7pro")
            }) is McpToolRegistry.Outcome.Ok,
        )
        val both = listenTools.tools.first { it.name == "listen_settings_get" }
            .execute(JsonObject().apply { addProperty("packageName", pkg) })
            .get("override").asJsonObject
        assertEquals(false, both.get("kinds").asJsonObject.get("behavior:FILE_READ").asBoolean)
        assertEquals("pixel7pro", both.get("spoofProfileId").asString)

        // 监听面恢复默认 → 功能域定制必须原样留下
        val afterListen = listenTools.tools.first { it.name == "listen_settings_reset" }
            .execute(JsonObject().apply { addProperty("packageName", pkg) })
        assertEquals(
            "listen deviation must be gone: ${afterListen.get("override")}",
            0,
            afterListen.get("override").asJsonObject.get("kinds").asJsonObject.size(),
        )
        assertEquals(
            "feature deviation must survive listen reset",
            "pixel7pro",
            afterListen.get("override").asJsonObject.get("spoofProfileId").asString,
        )
        // 功能面恢复默认 → 清掉画像(此时监听域已空,不受影响)
        val afterFeature = featureTools.tools.first { it.name == "feature_settings_reset" }
            .execute(JsonObject().apply { addProperty("packageName", pkg) })
            .get("override").asJsonObject
        assertTrue("feature deviation must be gone: $afterFeature", afterFeature.get("spoofProfileId").isJsonNull)
    }
}
