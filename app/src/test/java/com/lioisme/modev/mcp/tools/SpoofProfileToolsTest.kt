package com.lioisme.modev.mcp.tools

import androidx.room.Room
import com.google.gson.JsonObject
import com.lioisme.modev.core.codec.Json
import com.lioisme.modev.core.model.BuiltinDeviceProfiles
import com.lioisme.modev.core.model.DeviceProfile
import com.lioisme.modev.core.model.ProfileEnvelope
import com.lioisme.modev.core.model.ProfileField
import com.lioisme.modev.core.model.ProfileFieldCatalog
import com.lioisme.modev.core.model.ProfileImportVerdict
import com.lioisme.modev.core.model.ProfileTransferItem
import com.lioisme.modev.core.model.ProfileValues
import com.lioisme.modev.core.model.ProfileValueType
import com.lioisme.modev.core.model.legalSample
import com.lioisme.modev.data.db.ModevDatabase
import com.lioisme.modev.data.repo.KeywordLibraryRepository
import com.lioisme.modev.data.repo.SettingsRepository
import com.lioisme.modev.data.repo.SpoofProfileRepository
import com.lioisme.modev.data.server.ConfigServer
import com.lioisme.modev.mcp.protocol.JsonRpc
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 画像库工具组行为门禁(V47 WP-7,Robolectric 真 Room 库 + 真配置仓库,零 mock 上游):
 *
 *  - **DIY 端到端**:存 → 列 → 改 → 绑 → 解绑 → 删,每步断言**用户可见结果**
 *    (库里到底有什么、绑定解析到哪一态),不用「某方法有没有被调用」这类代理指标;
 *  - **校验即拒绝**:非法值整笔拒并点名(字段 + 错误 + 原值),未知键硬拒并回有效键集,
 *    且**库里不留半成品**;
 *  - **声明面即生效面**:一律经 [McpToolRegistry] 的 schema 校验路径调用 ——
 *    缺 `confirm` / 未声明参数 / 类型不符 = 协议错误 -32602,
 *    语义不合法(未知画像 id、非法字段值)= 业务错误,两种分型不得混淆;
 *  - **目录即事实源**:`spoof_profile_catalog` 的条数与键集恒等于 [ProfileFieldCatalog]
 *    的派生结果,并且**照目录构造的载荷必被 save 收下**(声明与生效互逆)。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SpoofProfileToolsTest {

    private lateinit var db: ModevDatabase
    private lateinit var settings: SettingsRepository
    private lateinit var profiles: SpoofProfileRepository
    private lateinit var profileTools: SpoofProfileTools
    private lateinit var assetTools: GlobalAssetTools
    private lateinit var featureTools: FeatureConfigTools
    private lateinit var registry: McpToolRegistry

    /** 资产库收集器 scope:随测试关闭,防 arch_disk_io 线程泄漏污染后续 Robolectric 用例。 */
    private lateinit var scope: CoroutineScope

    private val pkg = "pkg.profile.tools"

    /** 内置档已随 setUp 补档 ⇒ 库内非 DIY 行数基线(取自唯一事实源,扩充内置档时不必改这里)。 */
    private val builtinRows = BuiltinDeviceProfiles.BUILTINS.size

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, ModevDatabase::class.java).build()
        val server = ConfigServer()
        settings = SettingsRepository(
            server = server,
            writer = object : com.lioisme.modev.data.repo.ConfigWriter {
                private val map = mutableMapOf<String, String>()
                override fun set(switch: com.lioisme.modev.core.model.HookSwitch) {
                    map["switch_global"] = switch.let { com.google.gson.Gson().toJson(it) }
                }
                override fun get(): com.lioisme.modev.core.model.HookSwitch? =
                    map["switch_global"]?.let {
                        com.lioisme.modev.core.model.HookSwitch.fromJson(com.google.gson.Gson(), it)
                    }
                override fun migrateLegacy() {}
                override fun put(key: String, value: String) { map[key] = value }
                override fun remove(key: String) { map.remove(key) }
                override fun getLocal(key: String): String? = map[key]
                override fun localKeysWithPrefix(prefix: String): Set<String> =
                    map.keys.filter { it.startsWith(prefix) }.toSet()
            },
            gson = com.google.gson.Gson(),
            localPrefs = context.getSharedPreferences("test_spoof_profile_tools", 0),
        )
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        // 与 AppContainer 同序:先补内置三档,库面才有「内置 + DIY」两族可列
        profiles = SpoofProfileRepository(db.spoofProfileDao(), scope)
        runBlocking { profiles.synchronizeBuiltins() }
        profileTools = SpoofProfileTools(profiles, settings)
        assetTools = GlobalAssetTools(KeywordLibraryRepository(db.keywordLibraryDao(), scope), settings, profiles)
        featureTools = FeatureConfigTools(settings)
        registry = McpToolRegistry(profileTools.tools + assetTools.tools + featureTools.tools)
    }

    @After
    fun tearDown() {
        // 先取消并等待收集器退出,再关库:防 Room 磁盘 IO 线程在库关闭后仍被发射路径触达。
        runBlocking { withTimeoutOrNull(3_000) { scope.coroutineContext[Job]?.cancelAndJoin() } }
        db.close()
    }

    // ---------------------------------------------------------------- 助手

    private fun tool(name: String) = profileTools.tools.first { it.name == name }

    private fun fieldsJson(vararg pairs: Pair<String, Any?>): JsonObject = JsonObject().apply {
        pairs.forEach { (key, value) -> add(key, McpJson.value(value)) }
    }

    /** 经注册表(schema 校验路径)调用 —— 直调 `execute` 会绕过 -32602 分型,正是门禁盲区。 */
    private suspend fun call(name: String, args: JsonObject): McpToolRegistry.Outcome =
        registry.call(name, args) { t, a -> t.execute(a) }

    private fun McpToolRegistry.Outcome.content(): JsonObject =
        (this as McpToolRegistry.Outcome.Ok).result.getAsJsonObject("structuredContent")

    private suspend fun save(args: JsonObject): JsonObject {
        val outcome = call("spoof_profile_save", args)
        assertTrue("save must succeed: $outcome", outcome is McpToolRegistry.Outcome.Ok)
        return outcome.content()
    }

    private suspend fun save(name: String, vararg fields: Pair<String, Any?>): String =
        save(
            JsonObject().apply {
                addProperty("name", name)
                add("fields", fieldsJson(*fields))
            },
        ).get("profileId").asString

    /** 库列表(`spoof_profiles_list` 出参,与 MCP 面同一份渲染路径)。 */
    private suspend fun listedProfiles(): List<JsonObject> =
        assetTools.tools.first { it.name == "spoof_profiles_list" }
            .execute(JsonObject())
            .getAsJsonArray("profiles")
            .map { it.asJsonObject }

    private fun catalogKeys(): List<String> =
        ProfileField.entries.map { ProfileFieldCatalog.jsonKeyOf(it) }

    // ---------------------------------------------------------------- 存 + 列

    @Test
    fun save_creates_and_list_shows_the_entry_with_a_catalog_keyed_field_map() = runBlocking {
        val id = save("Tools DIY", "brand" to "google", "model" to "Pixel 7 Pro", "densityDpi" to "500")
        val rows = listedProfiles()
        assertEquals(builtinRows + 1, rows.size)
        val row = rows.first { it.get("id").asString == id }
        assertEquals("Tools DIY", row.get("name").asString)
        assertEquals(3, row.get("filledCount").asInt)
        assertFalse("DIY 条目不得被认作内置档", row.get("builtin").asBoolean)
        assertTrue("DIY 条目无内置 seed 键", row.get("builtinSeedId").isJsonNull)
        assertTrue("时间戳必须是 epoch millis(非 0)", row.get("createTime").asLong > 0L)
        assertTrue(
            "更新时间与创建时间同源派生",
            row.get("updateTime").asLong >= row.get("createTime").asLong,
        )

        // 字段表恒为**全量**目录键,值 = 编辑态文本(未生效项 = 空串)
        val fields = row.getAsJsonObject("fields")
        assertEquals(catalogKeys().toSet(), fields.keySet())
        assertEquals("google", fields.get("brand").asString)
        assertEquals("500", fields.get("densityDpi").asString)
        assertEquals("", fields.get("imei").asString)
    }

    @Test
    fun save_merges_into_an_existing_entry_and_the_field_map_round_trips() = runBlocking {
        val id = save("Round Trip", "brand" to "google", "model" to "Pixel 7 Pro")
        val listed = listedProfiles().first { it.get("id").asString == id }

        // 把读出的整张字段表原样回灌 ⇒ 必须仍然合法且已填项数不变(读得出就写得回)
        val again = save(
            JsonObject().apply {
                addProperty("name", "Round Trip")
                addProperty("profileId", id)
                add("fields", listed.getAsJsonObject("fields").deepCopy())
            },
        )
        assertFalse("带 profileId 必须是更新而非新建", again.get("created").asBoolean)
        assertEquals(id, again.get("profileId").asString)
        assertEquals(2, again.get("filledCount").asInt)
        assertEquals(1, listedProfiles().count { it.get("name").asString == "Round Trip" })

        // 未提交的字段保持原值,提交的字段生效,空串清除单项
        val merged = save(
            JsonObject().apply {
                addProperty("name", "Round Trip")
                addProperty("profileId", id)
                add("fields", fieldsJson("model" to "", "cpuCores" to 8))
            },
        )
        assertEquals(2, merged.get("filledCount").asInt)
        val fields = listedProfiles().first { it.get("id").asString == id }.getAsJsonObject("fields")
        assertEquals("", fields.get("model").asString)
        assertEquals("google", fields.get("brand").asString)
        assertEquals("8", fields.get("cpuCores").asString)
    }

    /** 直调 handler 取业务异常(经注册表会被包成 BusinessError,拿不到原始点名信息)。 */
    private fun saveFailure(args: JsonObject): String = runBlocking {
        val failure = runCatching { tool("spoof_profile_save").execute(args) }.exceptionOrNull()
        (failure as McpTool.McpToolException).message.orEmpty()
    }

    @Test
    fun save_rejects_an_invalid_imei_and_names_the_field() {
        val message = saveFailure(
            JsonObject().apply {
                addProperty("name", "Bad Imei")
                add("fields", fieldsJson("imei" to "490171581696173"))
            },
        )
        assertTrue("must name the field: $message", message.contains("imei"))
        assertTrue("must name the catalog error: $message", message.contains("IMEI_FORMAT"))
        assertTrue("must echo the submitted value: $message", message.contains("490171581696173"))
        // 拒绝而非钳制:库里没有留下任何半成品
        assertTrue(profiles.profiles.value.none { it.name == "Bad Imei" })
        assertEquals(builtinRows, profiles.profiles.value.size)
        // 同一拒绝经协议路径仍是业务错误(不是 -32602)
        val outcome = runBlocking {
            call(
                "spoof_profile_save",
                JsonObject().apply {
                    addProperty("name", "Bad Imei")
                    add("fields", fieldsJson("imei" to "490171581696173"))
                },
            )
        }
        assertTrue("$outcome", outcome is McpToolRegistry.Outcome.BusinessError)
    }

    @Test
    fun save_rejects_unknown_field_keys_and_returns_the_valid_key_list() {
        val message = saveFailure(
            JsonObject().apply {
                addProperty("name", "Bogus Key")
                add("fields", fieldsJson("model" to "Pixel 7 Pro", "contentHex" to "00ff"))
            },
        )
        assertTrue("must name the unknown key: $message", message.contains("contentHex"))
        assertTrue("must return the valid key set: $message", message.contains("densityDpi"))
        assertTrue("must return the valid key set: $message", message.contains("cpuCores"))
        assertEquals(builtinRows, profiles.profiles.value.size)
    }

    @Test
    fun save_requires_a_name_and_at_least_one_effective_field() = runBlocking {
        // 空白名 = 业务错误(参数形态合法、语义不合法)
        assertTrue(
            call(
                "spoof_profile_save",
                JsonObject().apply {
                    addProperty("name", "   ")
                    add("fields", fieldsJson("brand" to "google"))
                },
            ) is McpToolRegistry.Outcome.BusinessError,
        )
        // 全部清空 = 零字段画像,无可伪装 ⇒ 拒绝落库(不留空行)
        val emptied = call(
            "spoof_profile_save",
            JsonObject().apply {
                addProperty("name", "Nothing Here")
                add("fields", fieldsJson("brand" to ""))
            },
        )
        assertTrue("empty profile must be refused: $emptied", emptied is McpToolRegistry.Outcome.BusinessError)
        assertTrue(
            call(
                "spoof_profile_save",
                JsonObject().apply {
                    addProperty("name", "No Fields")
                    add("fields", JsonObject())
                },
            ) is McpToolRegistry.Outcome.BusinessError,
        )
        // 未知 profileId = 业务错误(不是「静默新建第二档」)
        assertTrue(
            call(
                "spoof_profile_save",
                JsonObject().apply {
                    addProperty("name", "Orphan Update")
                    addProperty("profileId", "no-such-profile")
                    add("fields", fieldsJson("brand" to "google"))
                },
            ) is McpToolRegistry.Outcome.BusinessError,
        )
        assertEquals(builtinRows, profiles.profiles.value.size)
    }

    // ---------------------------------------------------------------- 删

    @Test
    fun delete_requires_confirm_and_refuses_an_unknown_id() = runBlocking {
        val id = save("Doomed", "brand" to "google")

        // 缺 confirm / 显式 null = 协议错误 -32602(与 records_clear_all 同一门槛)
        val missing = call("spoof_profile_delete", JsonObject().apply { addProperty("profileId", id) })
        assertTrue("$missing", missing is McpToolRegistry.Outcome.ProtocolError)
        assertEquals(JsonRpc.INVALID_PARAMS, (missing as McpToolRegistry.Outcome.ProtocolError).code)
        val nullish = call(
            "spoof_profile_delete",
            JsonObject().apply {
                addProperty("profileId", id)
                add("confirm", com.google.gson.JsonNull.INSTANCE)
            },
        )
        assertEquals(JsonRpc.INVALID_PARAMS, (nullish as McpToolRegistry.Outcome.ProtocolError).code)
        // confirm=false = 业务错误,行必须还在
        val refused = call(
            "spoof_profile_delete",
            JsonObject().apply {
                addProperty("profileId", id)
                addProperty("confirm", false)
            },
        )
        assertTrue("$refused", refused is McpToolRegistry.Outcome.BusinessError)
        assertNotNull(profiles.find(id))
        // confirm=true = 删除,列表随之收敛
        val gone = call(
            "spoof_profile_delete",
            JsonObject().apply {
                addProperty("profileId", id)
                addProperty("confirm", true)
            },
        )
        assertTrue("$gone", gone is McpToolRegistry.Outcome.Ok)
        assertNull(profiles.find(id))
        assertTrue(listedProfiles().none { it.get("id").asString == id })
        // 未知 id 仍是业务错误(不是「删了 0 行也算成」)
        assertTrue(
            call(
                "spoof_profile_delete",
                JsonObject().apply {
                    addProperty("profileId", "no-such-profile")
                    addProperty("confirm", true)
                },
            ) is McpToolRegistry.Outcome.BusinessError,
        )
    }

    @Test
    fun deleting_a_builtin_profile_is_a_tombstone_and_the_library_stays_consistent() = runBlocking {
        val pixelId = com.lioisme.modev.core.model.BuiltinDeviceProfiles
            .idFor(com.lioisme.modev.core.model.BuiltinDeviceProfiles.PIXEL_7_PRO)
        assertTrue(profiles.profiles.value.any { it.id == pixelId })
        val out = call(
            "spoof_profile_delete",
            JsonObject().apply {
                addProperty("profileId", pixelId)
                addProperty("confirm", true)
            },
        )
        assertTrue("$out", out is McpToolRegistry.Outcome.Ok)
        assertEquals(builtinRows - 1, listedProfiles().size)
        assertNull(profiles.find(pixelId))
    }

    // ---------------------------------------------------------------- 绑

    @Test
    fun apply_binds_a_package_and_unbinds_it_again() = runBlocking {
        val id = save("Bound Target", "brand" to "google", "model" to "Pixel 7 Pro")

        val bound = call("spoof_profile_apply", JsonObject().apply {
            addProperty("packageName", pkg)
            addProperty("profileId", id)
        })
        assertTrue("$bound", bound is McpToolRegistry.Outcome.Ok)
        val boundOut = bound.content()
        assertEquals("BOUND", boundOut.get("resolution").asString)
        assertEquals(id, boundOut.get("profileId").asString)
        assertEquals("Bound Target", boundOut.get("boundName").asString)
        assertEquals(2, boundOut.get("filledCount").asInt)
        assertEquals(1, boundOut.get("deviationCount").asInt)
        // 落点 = 每应用功能域偏差(与 feature_settings_set 同一条写路径,非另开 prefs 旁路)
        assertEquals(id, settings.overrideFor(pkg).spoofProfileId)
        assertEquals(id, settings.effectiveSwitch(pkg).feature.spoofProfileId)

        // 解绑:省略 profileId
        val unbound = call("spoof_profile_apply", JsonObject().apply { addProperty("packageName", pkg) })
        assertTrue("$unbound", unbound is McpToolRegistry.Outcome.Ok)
        assertEquals("UNBOUND", unbound.content().get("resolution").asString)
        assertTrue(unbound.content().get("profileId").isJsonNull)
        assertEquals(0, unbound.content().get("deviationCount").asInt)
        assertNull(settings.overrideFor(pkg).spoofProfileId)

        // 解绑亦可传空串;packageName 恒必填(缺失 = 协议错误)
        val rebound = call("spoof_profile_apply", JsonObject().apply {
            addProperty("packageName", pkg)
            addProperty("profileId", id)
        })
        assertTrue("$rebound", rebound is McpToolRegistry.Outcome.Ok)
        val cleared = call("spoof_profile_apply", JsonObject().apply {
            addProperty("packageName", pkg)
            addProperty("profileId", "")
        })
        assertTrue("$cleared", cleared is McpToolRegistry.Outcome.Ok)
        assertEquals("UNBOUND", cleared.content().get("resolution").asString)
        assertTrue(
            call("spoof_profile_apply", JsonObject()) is McpToolRegistry.Outcome.ProtocolError,
        )
    }

    @Test
    fun apply_refuses_an_unknown_profile_id_instead_of_falling_back() = runBlocking {
        val outcome = call("spoof_profile_apply", JsonObject().apply {
            addProperty("packageName", pkg)
            addProperty("profileId", "ghost-profile")
        })
        assertTrue("unknown id must be a named refusal: $outcome",
            outcome is McpToolRegistry.Outcome.BusinessError)
        assertTrue(
            (outcome as McpToolRegistry.Outcome.BusinessError).message.contains("ghost-profile"),
        )
        assertNull("binding must not be persisted", settings.overrideFor(pkg).spoofProfileId)
    }

    /**
     * V47 WP-7 契约:`feature_settings_set` 的 `spoofProfileId` 由「三个编译期常量的 enum」
     * 放开为**画像库主键自由串**(DIY 档的键在运行时才存在,枚举无法广告),
     * 但类型闸必须原样留着;`spoofOverrides` 按决策 D-3 冻结保留。
     */
    @Test
    fun feature_settings_set_accepts_any_library_key_but_still_type_checks_it() = runBlocking {
        val id = save("Legacy Channel", "brand" to "google")
        val schema = featureTools.tools.first { it.name == "feature_settings_set" }
            .inputSchema().getAsJsonObject("properties").getAsJsonObject("spoofProfileId")
        assertFalse("spoofProfileId must no longer advertise an enum", schema.has("enum"))
        assertEquals("string", schema.get("type").asString)

        val ok = call("feature_settings_set", JsonObject().apply {
            addProperty("packageName", pkg)
            addProperty("spoofProfileId", id)
        })
        assertTrue("$ok", ok is McpToolRegistry.Outcome.Ok)
        assertEquals(id, ok.content().getAsJsonObject("override").get("spoofProfileId").asString)

        // 类型闸不得随枚举一起掉
        val wrongType = call("feature_settings_set", JsonObject().apply {
            addProperty("packageName", pkg)
            addProperty("spoofProfileId", 42)
        })
        assertTrue("$wrongType", wrongType is McpToolRegistry.Outcome.ProtocolError)
        // 冻结的遗留覆写通道原样可用(不删、不扩)
        val legacy = call("feature_settings_set", JsonObject().apply {
            addProperty("packageName", pkg)
            add("spoofOverrides", JsonObject().apply { addProperty("build.BRAND", "Acme") })
        })
        assertTrue("$legacy", legacy is McpToolRegistry.Outcome.Ok)
        assertEquals("Acme", settings.overrideFor(pkg).spoofOverrides?.get("build.BRAND"))
    }

    // ---------------------------------------------------------------- 目录

    @Test
    fun catalog_covers_every_profile_field_and_is_derived_from_the_catalog() = runBlocking {
        assertEquals("字段面恒 41 项(V47 T1 快照)", 41, ProfileField.entries.size)
        val out = tool("spoof_profile_catalog").execute(JsonObject())
        assertEquals(ProfileField.entries.size, out.get("total").asInt)
        val entries = out.getAsJsonArray("fields").map { it.asJsonObject }
        assertEquals(ProfileField.entries.size, entries.size)
        // 键集与行序都派生自目录(本断言即「无第二份字段表」的证明)
        assertEquals(catalogKeys(), entries.map { it.get("key").asString })
        assertEquals(ProfileField.entries.map { it.name }, entries.map { it.get("field").asString })

        ProfileField.entries.forEach { field ->
            val json = entries.first { it.get("key").asString == ProfileFieldCatalog.jsonKeyOf(field) }
            assertEquals(field.kind.name, json.get("kind").asString)
            assertEquals(field.section.name, json.get("section").asString)
            assertEquals(field.type.name, json.get("type").asString)
            assertEquals(field.baselineKey.orEmpty(), json.get("baselineKey").let { if (it.isJsonNull) "" else it.asString })
            assertEquals(ProfileFieldCatalog.isRandomizable(field), json.get("randomizable").asBoolean)
            val range = ProfileFieldCatalog.rangeOf(field)
            if (range == null) {
                assertTrue("${field.name} must advertise no range", json.get("rangeMin").isJsonNull)
                assertTrue("${field.name} must advertise no range", json.get("rangeMax").isJsonNull)
            } else {
                assertEquals(range.first, json.get("rangeMin").asInt)
                assertEquals(range.last, json.get("rangeMax").asInt)
            }
            assertFalse("${field.name} must carry a sentinel description", json.get("sentinel").asString.isEmpty())
            // 枚举槽字段的合法槽集必须**由目录 validate 裁决**(反推所得),两侧都不许手抄槽表:
            // 本工具第一版正是在说明文字里把 screenshotsFlag 的 0/1 两个槽写反了方向。
            val advertisedSlots = json.get("acceptedSlots")
            val isSlotField = field.type == ProfileValueType.NETWORK_TYPE ||
                field.type == ProfileValueType.SCREENSHOT_MODE
            if (isSlotField) {
                assertEquals(
                    (-20..60).filter { ProfileFieldCatalog.validate(field, it.toString()) == null }
                        .map { it.toString() },
                    advertisedSlots.asJsonArray.map { it.asString },
                )
            } else {
                assertTrue("${field.name} must not advertise slots", advertisedSlots.isJsonNull)
            }
        }

        // 抽查两个具体声明:界与哨兵确实来自 ProfileValues,而非手写字面量
        val density = entries.first { it.get("key").asString == "densityDpi" }
        assertEquals(ProfileValues.DENSITY_MIN, density.get("rangeMin").asInt)
        assertEquals(ProfileValues.DENSITY_MAX, density.get("rangeMax").asInt)
        assertEquals("INT", density.get("type").asString)
        assertEquals("${ProfileValues.UNSET_INT}", density.get("sentinel").asString)
        val imei = entries.first { it.get("key").asString == "imei" }
        assertEquals("PROFILE_UNIQUE_ID", imei.get("kind").asString)
        assertEquals("IDENTITY", imei.get("section").asString)
        assertEquals("empty string", imei.get("sentinel").asString)
        assertTrue(imei.get("randomizable").asBoolean)

        // 枚举槽集必须逐字等于 ProfileValues 声明的两端(槽义曾手抄写反 ⇒ 常量侧对账)
        assertEquals(
            listOf(
                "${ProfileValues.SCREENSHOT_UNHOOK}",
                "${ProfileValues.SCREENSHOT_DENY}",
                "${ProfileValues.SCREENSHOT_ALLOW}",
            ),
            entries.first { it.get("key").asString == "screenshotsFlag" }
                .getAsJsonArray("acceptedSlots").map { it.asString },
        )
        assertEquals(
            (ProfileValues.NET_NONE..ProfileValues.NET_MOBILE_5G).map { "$it" },
            entries.first { it.get("key").asString == "networkType" }
                .getAsJsonArray("acceptedSlots").map { it.asString },
        )

        // 分组面派生自同一张表:分区/类别的键并集 = 全量键集
        val flat = { array: com.google.gson.JsonArray ->
            array.flatMap { it.asJsonObject.getAsJsonArray("keys").map { key -> key.asString } }
        }
        val allKeys = entries.map { it.get("key").asString }.toSet()
        val sectionKeys = flat(out.getAsJsonArray("sections"))
        assertEquals(allKeys, sectionKeys.toSet())
        assertEquals(ProfileField.entries.size, sectionKeys.size)
        assertEquals(allKeys, flat(out.getAsJsonArray("kinds")).toSet())
    }

    /** 目录是「可执行」的:照目录键与目录哨兵构造的载荷必须被 save 整张收下。 */
    @Test
    fun catalog_advertises_exactly_the_keys_save_accepts() = runBlocking {
        val advertised = tool("spoof_profile_catalog").execute(JsonObject())
            .getAsJsonArray("fields").map { it.asJsonObject.get("key").asString }
        val payload = fieldsJson(
            *advertised.map { key ->
                val field = requireNotNull(ProfileFieldCatalog.fieldByJsonKey(key)) { "catalog leaked $key" }
                key to legalSample(field)
            }.toTypedArray(),
        )
        val out = save(
            JsonObject().apply {
                addProperty("name", "Catalog Filled")
                add("fields", payload)
            },
        )
        assertEquals(advertised.size, out.get("filledCount").asInt)
        val fields = listedProfiles().first { it.get("id").asString == out.get("profileId").asString }
            .getAsJsonObject("fields")
        assertEquals(advertised.toSet(), fields.keySet())
    }

    // ---------------------------------------------------------------- 导入

    @Test
    fun import_reports_schema_rejected_for_a_foreign_envelope_version() = runBlocking {
        val out = tool("spoof_profile_import").execute(
            JsonObject().apply {
                addProperty("text", """{"schemaVersion":99,"exportedAt":1,"items":[]}""")
            },
        )
        assertTrue("version mismatch must be surfaced", out.get("schemaRejected").asBoolean)
        assertEquals(0, out.get("imported").asInt)
        assertEquals(builtinRows, profiles.profiles.value.size)
        // 不可解析文本同一分型(整包拒,不做跨版本猜测)
        assertTrue(
            tool("spoof_profile_import")
                .execute(JsonObject().apply { addProperty("text", "not json at all") })
                .get("schemaRejected").asBoolean,
        )
        // 缺 text = 协议错误
        assertTrue(
            call("spoof_profile_import", JsonObject()) is McpToolRegistry.Outcome.ProtocolError,
        )
    }

    @Test
    fun import_round_trips_an_export_and_reports_per_item_verdicts() = runBlocking {
        val id = save("Exported One", "brand" to "google", "model" to "Pixel 7 Pro", "densityDpi" to "500")
        val envelope = profiles.exportJson(listOf(id))
        runBlocking { profiles.delete(id) }
        assertEquals(builtinRows, profiles.profiles.value.size)

        val dry = tool("spoof_profile_import").execute(
            JsonObject().apply {
                addProperty("text", envelope)
                addProperty("dryRun", true)
            },
        )
        assertFalse(dry.get("schemaRejected").asBoolean)
        assertEquals(1, dry.get("imported").asInt)
        assertEquals(builtinRows, profiles.profiles.value.size)
        assertEquals(ProfileImportVerdict.DRY_RUN_OK.name, dry.getAsJsonArray("verdicts")[0].asString)
        assertEquals(1, dry.getAsJsonObject("verdictCounts").get(ProfileImportVerdict.DRY_RUN_OK.name).asInt)

        val wet = tool("spoof_profile_import").execute(JsonObject().apply { addProperty("text", envelope) })
        assertEquals(1, wet.get("imported").asInt)
        assertEquals(0, wet.get("skipped").asInt)
        assertEquals(0, wet.get("rejected").asInt)
        assertEquals(builtinRows + 1, profiles.profiles.value.size)
        val restored = profiles.profiles.value.first { it.name == "Exported One" }
        assertEquals(
            DeviceProfile.EMPTY.copy(brand = "google", model = "Pixel 7 Pro", densityDpi = 500),
            restored.profile,
        )

        // 同包再导一次 = 内容指纹命中,零重复建档
        val again = tool("spoof_profile_import").execute(JsonObject().apply { addProperty("text", envelope) })
        assertEquals(0, again.get("imported").asInt)
        assertEquals(1, again.get("skipped").asInt)
        assertEquals(ProfileImportVerdict.DUPLICATE.name, again.getAsJsonArray("verdicts")[0].asString)
        assertEquals(builtinRows + 1, profiles.profiles.value.size)
    }

    @Test
    fun import_rejects_empty_and_illegal_items_without_touching_the_library() = runBlocking {
        val envelope = Json.gson.toJson(
            ProfileEnvelope(
                items = listOf(
                    ProfileTransferItem(name = "Good", profile = DeviceProfile(brand = "google")),
                    ProfileTransferItem(name = "Empty"),
                    ProfileTransferItem(name = "Illegal", profile = DeviceProfile(imei = "490171581696173")),
                    ProfileTransferItem(name = "   ", summary = "no name", profile = DeviceProfile(model = "One")),
                ),
            ),
        )
        val out = tool("spoof_profile_import").execute(JsonObject().apply { addProperty("text", envelope) })
        assertFalse(out.get("schemaRejected").asBoolean)
        assertEquals(2, out.get("imported").asInt)
        assertEquals(0, out.get("skipped").asInt)
        assertEquals(2, out.get("rejected").asInt)
        assertEquals(4, out.get("items").asInt)
        assertEquals(
            listOf(
                ProfileImportVerdict.IMPORTED.name,
                ProfileImportVerdict.REJECTED_EMPTY.name,
                ProfileImportVerdict.REJECTED_INVALID.name,
                ProfileImportVerdict.IMPORTED_NAME_FALLBACK.name,
            ),
            out.getAsJsonArray("verdicts").map { it.asString },
        )
        assertEquals(1, out.getAsJsonObject("verdictCounts").get(ProfileImportVerdict.REJECTED_INVALID.name).asInt)
        assertTrue(profiles.profiles.value.any { it.name == "Good" })
        assertTrue(
            "无名列必须落到调用方提供的默认名(MCP 面恒英文)",
            profiles.profiles.value.any { it.name == "Imported profile" },
        )
        assertTrue(profiles.profiles.value.none { it.name == "Illegal" })
    }

    // ---------------------------------------------------------------- 分型与装配面

    /** 声明面即生效面:画像库五工具的未声明参数一律协议错误。 */
    @Test
    fun undeclared_parameters_are_protocol_errors_across_the_group() = runBlocking {
        val probes = mapOf(
            "spoof_profile_save" to JsonObject().apply {
                addProperty("name", "Probe")
                add("fields", fieldsJson("brand" to "google"))
                addProperty("profileId2", "nope")
            },
            "spoof_profile_delete" to JsonObject().apply {
                addProperty("profileId", "whatever")
                addProperty("confirm", true)
                addProperty("force", true)
            },
            "spoof_profile_apply" to JsonObject().apply {
                addProperty("packageName", pkg)
                addProperty("profileIds", "nope")
            },
            "spoof_profile_import" to JsonObject().apply {
                addProperty("text", "{}")
                addProperty("merge", true)
            },
            "spoof_profile_catalog" to JsonObject().apply { addProperty("kind", "PROFILE_BUILD") },
        )
        probes.forEach { (name, args) ->
            val outcome = call(name, args)
            assertTrue("$name must refuse undeclared keys, got $outcome",
                outcome is McpToolRegistry.Outcome.ProtocolError)
            assertEquals(
                JsonRpc.INVALID_PARAMS,
                (outcome as McpToolRegistry.Outcome.ProtocolError).code,
            )
        }
        assertEquals(builtinRows, profiles.profiles.value.size)
    }

    /** 五工具齐备、schema 有广告面且恒禁未声明键(V45 T3.1「写了但没挂」的镜像防线)。 */
    @Test
    fun group_exposes_five_tools_with_declared_surfaces() {
        assertEquals(
            setOf(
                "spoof_profile_save", "spoof_profile_delete", "spoof_profile_apply",
                "spoof_profile_catalog", "spoof_profile_import",
            ),
            profileTools.tools.map { it.name }.toSet(),
        )
        assertEquals(5, profileTools.tools.size)
        profileTools.tools.forEach { it: McpTool ->
            val schema = it.inputSchema()
            val properties = schema.getAsJsonObject("properties")
            assertNotNull(it.name, properties)
            assertFalse("${it.name} must forbid undeclared keys", schema.get("additionalProperties").asBoolean)
            assertFalse("${it.name} must carry an Agent-facing description", it.description.isBlank())
            if (it.name == "spoof_profile_catalog") {
                assertEquals(0, properties.keySet().size)
            } else {
                assertTrue("${it.name} must advertise parameters", properties.keySet().isNotEmpty())
            }
        }
    }
}
