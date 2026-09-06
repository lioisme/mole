package com.lioisme.modev.mcp.tools

import com.google.gson.JsonObject
import com.lioisme.modev.core.model.FeatureConfig
import com.lioisme.modev.core.model.KeywordEntry
import com.lioisme.modev.core.model.KeywordMatchMode
import com.lioisme.modev.core.model.SpoofProfiles
import com.lioisme.modev.data.repo.KeywordLibraryRepository
import com.lioisme.modev.data.repo.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 全局资产组工具(V38,8 个):关键词库 CRUD + 伪装画像 + 黑名单追加。
 * 上游与 KeywordLibraryViewModel/SpoofProfileViewModel/FeatureBlacklistViewModel 同源
 * (KeywordLibraryRepository + setGlobalFeature,推送链路 pushToAllConnected 自动)。
 */
class GlobalAssetTools(
    private val keywords: KeywordLibraryRepository,
    private val settings: SettingsRepository,
) {

    val tools: List<McpTool> = listOf(
        keywordsList(), keywordsAdd(), keywordsUpdate(), keywordsRemove(),
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
            throw McpTool.McpToolException("Unknown matchMode '$mode'; valid: CONTAINS|REGEX|EQUALS")
        }
    }

    /**
     * 等待库快照反映变更(V38):[KeywordLibraryRepository.entries] 由 Room 失效追踪
     * 异步刷新,写后立即读存在竞态;超时 3s 仍不满足则放行(读侧自会反映现状)。
     */
    private suspend fun awaitSnapshot(predicate: (List<KeywordEntry>) -> Boolean) {
        withTimeoutOrNull(3_000) { keywords.entries.first { predicate(it) } }
    }

    private fun keywordsList() = schemaTool(
        name = "keywords_list",
        description = "Global dialog-intercept keyword library (Room-backed; text unique). " +
            "matchMode: CONTAINS|REGEX|EQUALS.",
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
            str("matchMode", "CONTAINS (default)|REGEX|EQUALS", listOf("CONTAINS", "REGEX", "EQUALS"))
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
            str("matchMode", "CONTAINS|REGEX|EQUALS (optional; default keeps current)")
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

    private fun spoofProfilesList() = schemaTool(
        name = "spoof_profiles_list",
        description = "Built-in spoof profiles (auto/pixel7pro/xiaomi13) with resolved Build/ro fields, " +
            "telephony/wifi/cpu fake values, plus the currently selected global profile id and overrides.",
        schema = emptySchema(),
    ) { _ ->
        val profiles = SpoofProfiles.BUILTINS.map { profile ->
            McpJson.obj(
                "id" to com.google.gson.JsonPrimitive(profile.id),
                "buildFields" to McpJson.tree(profile.buildFields),
                "roProperties" to McpJson.tree(profile.roProperties),
                "imeiPrefix" to com.google.gson.JsonPrimitive(profile.imeiPrefix),
                "imsiPrefix" to com.google.gson.JsonPrimitive(profile.imsiPrefix),
                "iccidPrefix" to com.google.gson.JsonPrimitive(profile.iccidPrefix),
                "phonePrefix" to com.google.gson.JsonPrimitive(profile.phonePrefix),
                "fakeSsid" to com.google.gson.JsonPrimitive(profile.fakeSsid),
                "fakeBssid" to com.google.gson.JsonPrimitive(profile.fakeBssid),
                "cpuCores" to com.google.gson.JsonPrimitive(profile.cpuCores),
            )
        }
        val global = settings.globalFeature.value
        McpJson.obj(
            "profiles" to McpJson.arr(profiles),
            "currentProfileId" to com.google.gson.JsonPrimitive(global.spoofProfileId),
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
            str("profileId", "auto|pixel7pro|xiaomi13 (optional)")
            obj("overridesAdd", "Key->value overrides to merge (optional)")
            array("overrideKeysRemove", "Override keys to remove (optional)")
        },
    ) { args ->
        val current = settings.globalFeature.value
        val profileId = McpJson.str(args, "profileId")
        if (profileId != null && SpoofProfiles.BUILTINS.none { it.id == profileId }) {
            throw McpTool.McpToolException("Unknown profileId '$profileId'; valid: ${SpoofProfiles.BUILTINS.joinToString(",") { it.id }}")
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
        description = "Add or remove user blacklist appends. kind: rootPackages|xposedPrefixes|" +
            "fridaPorts|maskKeywords. action: add|remove. fridaPorts values are integers.",
        schema = {
            required(listOf("kind", "action", "values"))
            str("kind", "Blacklist kind", listOf("rootPackages", "xposedPrefixes", "fridaPorts", "maskKeywords"))
            str("action", "add or remove", listOf("add", "remove"))
            array("values", "Values (strings; integers for fridaPorts)")
        },
    ) { args ->
        val kind = McpJson.str(args, "kind") ?: throw McpTool.McpToolException("kind is required")
        val action = McpJson.str(args, "action") ?: throw McpTool.McpToolException("action is required")
        if (action != "add" && action != "remove") {
            throw McpTool.McpToolException("action must be add or remove")
        }
        val current = settings.globalFeature.value
        val values = args.getAsJsonArray("values")?.mapNotNull { it?.takeIf { e -> e.isJsonPrimitive } }
            ?: throw McpTool.McpToolException("values is required")
        fun strings(): List<String> = values.mapNotNull { it.asJsonPrimitive.takeIf { p -> p.isString }?.asString }
        fun ints(): List<Int> = values.mapNotNull { it.asJsonPrimitive.takeIf { p -> p.isNumber }?.asInt }
        val next: FeatureConfig = when (kind) {
            "rootPackages" -> {
                val delta = strings()
                current.copy(extraRootPackages = applyList(current.extraRootPackages, delta, action))
            }
            "xposedPrefixes" -> {
                val delta = strings()
                current.copy(extraXposedPrefixes = applyList(current.extraXposedPrefixes, delta, action))
            }
            "maskKeywords" -> {
                val delta = strings()
                current.copy(extraMaskKeywords = applyList(current.extraMaskKeywords, delta, action))
            }
            "fridaPorts" -> {
                val delta = ints()
                if (delta.isEmpty()) throw McpTool.McpToolException("fridaPorts values must be integers")
                current.copy(extraFridaPorts = applyList(current.extraFridaPorts, delta, action))
            }
            else -> throw McpTool.McpToolException("Unknown blacklist kind '$kind'")
        }
        kotlinx.coroutines.runBlocking { settings.setGlobalFeature(next) }
        McpJson.obj(
            "extraRootPackages" to McpJson.tree(next.extraRootPackages),
            "extraXposedPrefixes" to McpJson.tree(next.extraXposedPrefixes),
            "extraFridaPorts" to McpJson.tree(next.extraFridaPorts),
            "extraMaskKeywords" to McpJson.tree(next.extraMaskKeywords),
        )
    }

    private fun <T> applyList(current: List<T>, delta: List<T>, action: String): List<T> = when (action) {
        "add" -> (current + delta).distinct()
        else -> current - delta.toSet()
    }
}
