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
import com.lioisme.modev.data.server.ConfigServer
import com.lioisme.modev.domain.record.RecordFilter
import com.lioisme.modev.domain.record.RecordDraft
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
 * 资产推送、parity 快照(43 工具冻结 + 上游 allowlist)。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class McpToolsTest {

    private lateinit var db: ModevDatabase
    private lateinit var logs: LogRepository
    private lateinit var settings: SettingsRepository
    private lateinit var keywords: KeywordLibraryRepository
    private lateinit var recordTools: RecordTools
    private lateinit var listenTools: ListenConfigTools
    private lateinit var featureTools: FeatureConfigTools
    private lateinit var assetTools: GlobalAssetTools

    /** 关键词库 scope:随测试关闭,防 arch_disk_io 线程泄漏污染后续 Robolectric 用例。 */
    private lateinit var keywordScope: kotlinx.coroutines.CoroutineScope

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
        keywordScope = kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO,
        )
        keywords = KeywordLibraryRepository(db.keywordLibraryDao(), keywordScope)
        // init 需要装配关键词库(与 AppContainer 同序)
        settings.attachKeywordLibrary(keywords)
        recordTools = RecordTools(logs)
        listenTools = ListenConfigTools(settings)
        featureTools = FeatureConfigTools(settings)
        assetTools = GlobalAssetTools(keywords, settings)
    }

    @After
    fun tearDown() {
        // 先取消并等待收集器退出,再关库:防 Room 磁盘 IO 线程在库关闭后
        // 仍被发射路径触达(Illegal connection pointer 异步污染后续用例)。
        runBlocking {
            kotlinx.coroutines.withTimeoutOrNull(3_000) {
                keywordScope.coroutineContext[kotlinx.coroutines.Job]?.cancelAndJoin()
            }
        }
        db.close()
    }

    private fun draft(kind: String, pkg: String = "pkg.a", ts: Long = 100) = RecordDraft(
        packageName = pkg,
        kind = kind,
        operation = "ENCRYPT",
        algorithm = "AES",
        threadName = "main",
        timestamp = ts,
        recordType = RecordTypes.CRYPTO,
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
        logs.create(RecordDraft(packageName = "pkg.a", kind = "MAC", operation = "SIGN", algorithm = "HmacSHA256", threadName = "main"))
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
        // override 收敛 FILE_READ=false;effective 经 EffectiveSwitch.resolve 平铺传导
        val override = out.get("override").asJsonObject
        assertEquals(false, override.get("kinds").asJsonObject.get(BehaviorKind.FILE_READ.name).asBoolean)
        val effectiveSwitch = com.lioisme.modev.core.model.HookSwitch.fromJson(
            McpJson.gson, out.get("effective").asJsonObject.toString(),
        )!!
        assertEquals(false, com.lioisme.modev.core.model.EffectiveSwitch.readKind(effectiveSwitch, BehaviorKind.FILE_READ.name))
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
    fun parity_43_tools_frozen_snapshot() {
        val names = recordTools.tools + listenTools.tools + featureTools.tools + assetTools.tools
        // 本测试不装配 App/Server/Scope(需 Android 全家桶),快照断言覆盖 30 个;
        // 全量 43 断言在 McpToolParityTest(源码级,防漂移)。
        assertEquals(30, names.size)
        val expected = setOf(
            "records_query", "records_count", "records_type_counts", "records_category_counts",
            "record_get", "record_neighbor", "record_create", "record_update", "record_delete",
            "records_delete_by_filter", "records_clear_package", "records_clear_type", "records_clear_all",
            "storage_usage",
            "listen_settings_get", "listen_settings_set", "listen_settings_reset", "taxonomy_listen",
            "feature_settings_get", "feature_settings_set", "feature_settings_reset", "taxonomy_feature",
            "keywords_list", "keywords_add", "keywords_update", "keywords_remove",
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
}
