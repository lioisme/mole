package com.lioisme.modev.mcp.tools

import com.google.gson.JsonObject
import com.lioisme.modev.core.model.BlacklistKind
import com.lioisme.modev.core.model.DeviceProfileEntry
import com.lioisme.modev.core.model.FeatureConfig
import com.lioisme.modev.core.model.KeywordEntry
import com.lioisme.modev.core.model.KeywordMatchMode
import com.lioisme.modev.core.model.ProfileField
import com.lioisme.modev.core.model.ProfileFieldCatalog
import com.lioisme.modev.core.model.SpoofProfiles
import com.lioisme.modev.core.model.merged
import com.lioisme.modev.core.model.stringOf
import com.lioisme.modev.domain.asset.KeywordLibraryService
import com.lioisme.modev.domain.config.FeatureService
import com.lioisme.modev.domain.config.SpoofProfileService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 全局资产组工具(V38 8 个 + V44 条目启停/词表清空 + V47 WP-7 画像库改读真库 = 10 个):
 * 关键词库 CRUD + 启停 + 清空 + 伪装画像 + 黑名单追加。
 * 上游与 KeywordLibraryViewModel/SpoofProfileViewModel/FeatureBlacklistViewModel
 * **同契约面**(V45 T2.2:[KeywordLibraryService] + [FeatureService];
 * 推送链路 pushToAllConnected 由实现内自动触发)。
 *
 * V47 WP-7 起 [spoof_profiles_list] 不再回吐 `SpoofProfiles` 的三个编译期常量,而是
 * **画像库真身**([SpoofProfileService],含全部 DIY 档与内置档),键序与
 * `spoof_profile_catalog` 同源派生 ⇒ 读出的字段表可原样回灌 `spoof_profile_save`。
 * `SpoofProfiles` 仍由 [spoof_global_set] 与钩子侧消费(逐组接管前不删)。
 */
class GlobalAssetTools(
    private val keywords: KeywordLibraryService,
    private val settings: FeatureService,
    private val profiles: SpoofProfileService,
) {

    val tools: List<McpTool> = listOf(
        keywordsList(), keywordsAdd(), keywordsUpdate(), keywordsRemove(),
        keywordSetEnabled(), keywordsClear(),
        spoofProfilesList(), spoofGlobalSet(), blacklistList(), blacklistEdit(),
    )

    private fun entryJson(entry: com.lioisme.modev.core.model.KeywordEntry): JsonObject = McpJson.obj(
        "id" to com.google.gson.JsonPrimitive(entry.id),
        "text" to com.google.gson.JsonPrimitive(entry.text),
        "matchMode" to com.google.gson.JsonPrimitive(entry.matchMode.name),
        "enabled" to com.google.gson.JsonPrimitive(entry.enabled),
        "createdAt" to com.google.gson.JsonPrimitive(entry.createdAt),
    )

    private fun parseMatchMode(args: JsonObject): KeywordMatchMode {
        val mode = McpJson.str(args, "matchMode") ?: KeywordMatchMode.CONTAINS.name
        return runCatching { KeywordMatchMode.valueOf(mode) }.getOrElse {
            throw McpTool.McpToolException("Unknown matchMode '$mode'; valid: ${MATCH_MODES.joinToString("|")}")
        }
    }

    /**
     * 等待库快照反映变更(V38):[KeywordLibraryService.entries] 由 Room 失效追踪
     * 异步刷新,写后立即读存在竞态;超时 3s 仍不满足则放行(读侧自会反映现状)。
     */
    private suspend fun awaitSnapshot(predicate: (List<KeywordEntry>) -> Boolean) {
        withTimeoutOrNull(3_000) { keywords.entries.first { predicate(it) } }
    }

    private fun keywordsList() = schemaTool(
        name = "keywords_list",
        description = "Global dialog-intercept keyword library (Room-backed; text unique). " +
            "matchMode: ${MATCH_MODES.joinToString("|")}.",
        schema = emptySchema(),
    ) { _ ->
        McpJson.obj("entries" to McpJson.arr(keywords.entries.value.map { entryJson(it) }))
    }

    private fun keywordsAdd() = schemaTool(
        name = "keywords_add",
        description = "Add a keyword to the global library. Blank/duplicate text returns ok=false. " +
            "Library changes are re-pushed to all connected targets automatically.",
        schema = {
            required(listOf("text"))
            str("text", "Keyword text (unique, trimmed)")
            str("matchMode", "Match mode (default CONTAINS)", MATCH_MODES)
        },
    ) { args ->
        val text = McpJson.str(args, "text") ?: throw McpTool.McpToolException("text is required")
        val ok = kotlinx.coroutines.runBlocking {
            val added = keywords.add(text, parseMatchMode(args))
            if (added) awaitSnapshot { entries -> entries.any { it.text == text.trim() } }
            added
        }
        McpJson.obj("ok" to com.google.gson.JsonPrimitive(ok))
    }

    private fun keywordsUpdate() = schemaTool(
        name = "keywords_update",
        description = "Update keyword text and/or match mode by id. Blank/conflicting text returns ok=false.",
        schema = {
            required(listOf("id", "text"))
            long("id", "Keyword entry id")
            str("text", "New keyword text")
            str("matchMode", "Match mode (optional; default keeps current)", MATCH_MODES)
        },
    ) { args ->
        val id = McpJson.long(args, "id") ?: throw McpTool.McpToolException("id is required")
        val text = McpJson.str(args, "text") ?: throw McpTool.McpToolException("text is required")
        val current = keywords.entries.value.firstOrNull { it.id == id }
            ?: throw McpTool.McpToolException("Keyword $id not found")
        val mode = if (args.has("matchMode") && !args.get("matchMode").isJsonNull) parseMatchMode(args) else current.matchMode
        val ok = kotlinx.coroutines.runBlocking {
            val updated = keywords.update(id, text, mode)
            if (updated) awaitSnapshot { entries -> entries.any { it.id == id && it.text == text.trim() } }
            updated
        }
        McpJson.obj("ok" to com.google.gson.JsonPrimitive(ok))
    }

    private fun keywordsRemove() = schemaTool(
        name = "keywords_remove",
        description = "Remove a keyword from the global library by id.",
        schema = { required(listOf("id")); long("id", "Keyword entry id") },
    ) { args ->
        val id = McpJson.long(args, "id") ?: throw McpTool.McpToolException("id is required")
        kotlinx.coroutines.runBlocking {
            keywords.remove(id)
            awaitSnapshot { entries -> entries.none { it.id == id } }
        }
        McpJson.obj("ok" to com.google.gson.JsonPrimitive(true))
    }

    /** V44 Q5 能力补齐:UI「条目启停」同上游(setEnabled → 库变更重推)。 */
    private fun keywordSetEnabled() = schemaTool(
        name = "keyword_set_enabled",
        description = "Enable or disable one keyword library entry by id (per-entry toggle, same upstream " +
            "as the UI; library changes are re-pushed to connected targets).",
        schema = {
            required(listOf("id", "enabled"))
            long("id", "Keyword entry id")
            bool("enabled", "true = entry enabled, false = disabled")
        },
    ) { args ->
        val id = McpJson.long(args, "id") ?: throw McpTool.McpToolException("id is required")
        val enabled = McpJson.bool(args, "enabled") ?: throw McpTool.McpToolException("enabled is required")
        kotlinx.coroutines.runBlocking {
            keywords.setEnabled(id, enabled)
            awaitSnapshot { entries -> entries.firstOrNull { it.id == id }?.enabled == enabled }
        }
        McpJson.obj("ok" to com.google.gson.JsonPrimitive(true), "enabled" to com.google.gson.JsonPrimitive(enabled))
    }

    /** V44 Q5 能力补齐:UI「清空词表」同上游(危险操作,恒需 confirm,与 records_clear_all 同门槛)。 */
    private fun keywordsClear() = schemaTool(
        name = "keywords_clear",
        description = "DANGER: delete ALL keyword library entries. Requires confirm=true.",
        schema = {
            required(listOf("confirm"))
            bool("confirm", "Must be true to execute this destructive action")
        },
    ) { args ->
        if (McpJson.bool(args, "confirm") != true) {
            throw McpTool.McpToolException("confirm=true is required for destructive clear")
        }
        kotlinx.coroutines.runBlocking {
            keywords.clear()
            awaitSnapshot { entries -> entries.isEmpty() }
        }
        McpJson.obj("ok" to com.google.gson.JsonPrimitive(true))
    }

    /**
     * 画像条目 → 出参(V47 WP-7):身份 + 账本元数据 + 整张字段表。
     *
     * 字段表键序**派生自** [ProfileFieldCatalog](`jsonKeyOf` 即 wire/MCP 唯一键名),
     * 值经 [stringOf] 取**编辑态文本**(未生效项 = 空串)⇒ 与 `spoof_profile_save`
     * 的入参形态逐字对称,Agent 读出来改一笔就能写回去。
     */
    private fun profileJson(entry: DeviceProfileEntry): JsonObject {
        val fields = LinkedHashMap<String, String>()
        ProfileField.entries.forEach { field ->
            fields[ProfileFieldCatalog.jsonKeyOf(field)] = field.stringOf(entry.profile)
        }
        return McpJson.obj(
            "id" to entry.id,
            "name" to entry.name,
            "summary" to entry.summary,
            "builtinSeedId" to entry.builtinSeedId,
            "builtinVersion" to entry.builtinVersion,
            "builtin" to entry.isBuiltin,
            "managed" to entry.managed,
            "filledCount" to entry.filledCount,
            "createTime" to entry.createTime,
            "updateTime" to entry.updateTime,
            "fields" to fields,
        )
    }

    private fun spoofProfilesList() = schemaTool(
        name = "spoof_profiles_list",
        description = "Device profile library (Room-backed: built-in tiers plus every DIY profile), each " +
            "entry with id, name, builtin seed id, filledCount and the full field map keyed by " +
            "spoof_profile_catalog json keys (empty value = that field does nothing). Also returns the " +
            "currently selected global profile id and the frozen legacy field-override table. " +
            "Per-app bindings go through spoof_profile_apply.",
        schema = emptySchema(),
    ) { _ ->
        val entries = profiles.profiles.value.map { entryJson -> profileJson(entryJson) }
        val global = settings.globalFeature.value
        McpJson.obj(
            "profiles" to McpJson.arr(entries),
            "total" to entries.size,
            "currentProfileId" to global.spoofProfileId,
            "currentOverrides" to McpJson.tree(global.spoofOverrides),
        )
    }

    private fun spoofGlobalSet() = schemaTool(
        name = "spoof_global_set",
        description = "Set the global spoof profile and/or field overrides (applies to all scoped apps " +
            "via config push). profileId=null keeps current; overridesAdd merges key->value pairs " +
            "(keys: build.<FIELD>, ro.<prop>, telephony.imeiPrefix|imsiPrefix|iccidPrefix|phonePrefix, " +
            "wifi.ssid|wifi.bssid, cpu.cores); overrideKeysRemove deletes listed keys.",
        schema = {
            str("profileId", "Spoof profile id (optional)", PROFILE_IDS)
            obj("overridesAdd", "Key->value overrides to merge (optional)")
            array("overrideKeysRemove", "Override keys to remove (optional)")
        },
    ) { args ->
        val current = settings.globalFeature.value
        val profileId = McpJson.str(args, "profileId")
        if (profileId != null && SpoofProfiles.BUILTINS.none { it.id == profileId }) {
            throw McpTool.McpToolException(
                "Unknown profileId '$profileId'; valid: ${PROFILE_IDS.joinToString(",")}",
            )
        }
        var overrides = current.spoofOverrides
        args.getAsJsonObject("overridesAdd")?.let { add ->
            overrides = overrides + add.entrySet().mapNotNull { (k, e) ->
                e?.takeIf { it.isJsonPrimitive }?.asString?.let { k to it }
            }
        }
        McpJson.strList(args, "overrideKeysRemove")?.let { remove ->
            overrides = overrides - remove.toSet()
        }
        val next = current.copy(
            spoofProfileId = profileId ?: current.spoofProfileId,
            spoofOverrides = overrides,
        )
        kotlinx.coroutines.runBlocking { settings.setGlobalFeature(next) }
        McpJson.obj(
            "profileId" to com.google.gson.JsonPrimitive(next.spoofProfileId),
            "overrides" to McpJson.tree(next.spoofOverrides),
        )
    }

    private fun blacklistList() = schemaTool(
        name = "blacklist_list",
        description = "User blacklist appends per kind (extraRootPackages/extraXposedPrefixes/" +
            "extraFridaPorts/extraMaskKeywords). Effective detection set = built-in baseline UNION " +
            "these appends (baseline is immutable and not listed here).",
        schema = emptySchema(),
    ) { _ ->
        val feature: FeatureConfig = settings.globalFeature.value
        McpJson.obj(
            "extraRootPackages" to McpJson.tree(feature.extraRootPackages),
            "extraXposedPrefixes" to McpJson.tree(feature.extraXposedPrefixes),
            "extraFridaPorts" to McpJson.tree(feature.extraFridaPorts),
            "extraMaskKeywords" to McpJson.tree(feature.extraMaskKeywords),
        )
    }

    private fun blacklistEdit() = schemaTool(
        name = "blacklist_edit",
        description = "Add or remove user blacklist appends. kind: " +
            BLACKLIST_WIRES.joinToString("|") + ". action: add|remove. fridaPorts values are integers.",
        schema = {
            required(listOf("kind", "action", "values"))
            str("kind", "Blacklist kind", BLACKLIST_WIRES)
            str("action", "add or remove", listOf("add", "remove"))
            // V45 T3.2:元素类型随 kind 变化(fridaPorts 必须是整数,其余字符串),
            // 单一 items 类型声明会谎报 —— 故只声明数组,值类型由 handler 分型裁决。
            arrayRaw("values", "Values (strings; integers for kind=fridaPorts)")
        },
    ) { args ->
        val kind = BlacklistKind.fromWire(McpJson.str(args, "kind"))
            ?: throw McpTool.McpToolException(
                "Unknown blacklist kind; valid: ${BLACKLIST_WIRES.joinToString("|")}",
            )
        val action = McpJson.str(args, "action") ?: throw McpTool.McpToolException("action is required")
        if (action != "add" && action != "remove") {
            throw McpTool.McpToolException("action must be add or remove")
        }
        val values = args.getAsJsonArray("values")?.mapNotNull { it?.takeIf { e -> e.isJsonPrimitive } }
            ?: throw McpTool.McpToolException("values is required")
        if (kind == BlacklistKind.FRIDA_PORTS &&
            values.any { p -> !p.asJsonPrimitive.isNumber }
        ) {
            throw McpTool.McpToolException("fridaPorts values must be integers")
        }
        val current = settings.globalFeature.value
        val next = current.merged(kind, values.map { it.asJsonPrimitive.asString }, add = action == "add")
        kotlinx.coroutines.runBlocking { settings.setGlobalFeature(next) }
        McpJson.obj(
            "extraRootPackages" to McpJson.tree(next.extraRootPackages),
            "extraXposedPrefixes" to McpJson.tree(next.extraXposedPrefixes),
            "extraFridaPorts" to McpJson.tree(next.extraFridaPorts),
            "extraMaskKeywords" to McpJson.tree(next.extraMaskKeywords),
        )
    }

    private companion object {
        /** 匹配模式名集(派生自枚举,协议文档与校验同源)。 */
        val MATCH_MODES: List<String> = KeywordMatchMode.entries.map { it.name }

        /** 内置画像 id 集(派生自 SpoofProfiles 唯一事实源)。 */
        val PROFILE_IDS: List<String> = SpoofProfiles.BUILTINS.map { it.id }

        /** 黑名单 kind 线名集(派生自 core [BlacklistKind])。 */
        val BLACKLIST_WIRES: List<String> = BlacklistKind.entries.map { it.wire }
    }
}
