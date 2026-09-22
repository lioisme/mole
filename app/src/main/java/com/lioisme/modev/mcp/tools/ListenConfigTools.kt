package com.lioisme.modev.mcp.tools

import com.google.gson.JsonObject
import com.lioisme.modev.core.model.AppListenOverride
import com.lioisme.modev.core.model.CapabilityDomain
import com.lioisme.modev.core.model.CategoryTaxonomy
import com.lioisme.modev.core.model.HookSwitch
import com.lioisme.modev.core.model.RecordType
import com.lioisme.modev.domain.config.ListenConfigService

/**
 * 监听配置组工具(V38,4 个):每应用监听偏差读/写/重置 + LISTEN 域分类法。
 * 上游与 PerAppListenSettingsViewModel **同契约面**(V45 T2.2:持
 * [ListenConfigService] 接口而非仓库实现;推送链路 pushToPackage 由实现内自动触发)。
 */
class ListenConfigTools(private val settings: ListenConfigService) {

    val tools: List<McpTool> = listOf(
        listenSettingsGet(), listenSettingsSet(), listenSettingsReset(), taxonomyListen(),
    )

    private fun settingsJson(pkg: String, effective: HookSwitch, override: AppListenOverride): JsonObject =
        McpJson.obj(
            "packageName" to com.google.gson.JsonPrimitive(pkg),
            "effective" to McpJson.tree(effective),
            "override" to McpJson.tree(override),
            "deviationCount" to com.google.gson.JsonPrimitive(override.deviationCount),
        )

    private fun listenSettingsGet() = schemaTool(
        name = "listen_settings_get",
        description = "Read per-app listen settings: effective HookSwitch (default ⊕ deviation), the raw " +
            "AppListenOverride deviation and deviationCount. Master switches (one per record type): " +
            allMasterWires.joinToString("/") + ".",
        schema = { required(listOf("packageName")); str("packageName", "Target package name") },
    ) { args ->
        val pkg = McpJson.str(args, "packageName") ?: throw McpTool.McpToolException("packageName is required")
        settingsJson(pkg, settings.effectiveSwitch(pkg), settings.overrideFor(pkg))
    }

    private fun listenSettingsSet() = schemaTool(
        name = "listen_settings_set",
        description = "Merge per-app listen deviation. Only provided fields change; boolean fields accept " +
            "true/false, and null resets a field to default. kindEntries maps category keys " +
            "(kindKey form '<recordType>:<kind>' from taxonomy_listen, e.g. \"prefs:OPEN\"; bare ids " +
            "accepted and resolved via taxonomy) to true/false/null. Tweaks: logStack, " +
            "maxBytesPerField (0 = unlimited), ignoreStackPrefixes, maxResultRows, maxResultChars, " +
            "fileContentCapture, ignorePathPrefixes. Zero-deviation result deletes the stored " +
            "override key (equals default).",
        schema = {
            required(listOf("packageName"))
            str("packageName", "Target package name")
            str("master", "Master switch to change (one per record type)", allMasterWires)
            bool("masterValue", "true/false for the chosen master; omit key or JSON null to reset to default")
            obj("kindEntries", "Map of category kindKey -> true/false/null")
            bool("logStack", "Record call stacks (optional)")
            int("maxBytesPerField", "Per-field byte cap, <=0 unlimited (optional)")
            array("ignoreStackPrefixes", "Stack ignore prefixes, empty clears (optional)")
            int("maxResultRows", "Result row gate (optional)")
            int("maxResultChars", "Result byte gate (optional)")
            bool("fileContentCapture", "File content capture (optional)")
            array("ignorePathPrefixes", "Path ignore prefixes, empty clears (optional)")
        },
    ) { args ->
        val pkg = McpJson.str(args, "packageName") ?: throw McpTool.McpToolException("packageName is required")
        val current = settings.overrideFor(pkg)
        var next = current
        // master 偏差(master 名与 RecordType.wire 一致;三态 null = 回默认)
        val master = McpJson.str(args, "master")
        val masterValue = args.get("masterValue")
        if (master != null) {
            val type = RecordType.fromWire(master)
                ?: throw McpTool.McpToolException(
                    "Unknown master '$master'; valid: ${allMasterWires.joinToString(",")}",
                )
            if (masterValue == null || masterValue.isJsonNull) {
                next = applyMaster(next, type, null)
            } else {
                require(masterValue.isJsonPrimitive && masterValue.asJsonPrimitive.isBoolean) {
                    "masterValue must be boolean or null"
                }
                next = applyMaster(next, type, masterValue.asBoolean)
            }
        }
        // kind 级偏差(V44:键经 taxonomy 裁决并归一为限定形态,未知键显式拒绝)
        val kindEntries = args.getAsJsonObject("kindEntries")
        if (kindEntries != null) {
            val kinds = current.kinds.toMutableMap()
            kindEntries.entrySet().forEach { (id, value) ->
                val parsed = AppListenOverride.parseKindKey(id)
                    ?: throw McpTool.McpToolException(
                        "Unknown kind '$id'; use kindKey '<recordType>:<kind>' from taxonomy_listen",
                    )
                val key = AppListenOverride.kindKey(parsed.first, parsed.second)
                // V45 T2.4 / DUAL-10:双域闸(AGENTS #23)—— 监听工具只接受 LISTEN 域类别,
                // 干预类必须走 feature_settings_set(UI 两页本就按域分列,此前 MCP 可越域写)。
                if (CategoryTaxonomy.isFeature(parsed.first, parsed.second)) {
                    throw McpTool.McpToolException(
                        "Kind '$key' is FEATURE-domain; set it via feature_settings_set",
                    )
                }
                when {
                    value.isJsonNull -> kinds.remove(key)
                    value.isJsonPrimitive && value.asJsonPrimitive.isBoolean -> kinds[key] = value.asBoolean
                    else -> throw McpTool.McpToolException("kindEntries values must be boolean or null")
                }
            }
            next = next.copy(kinds = kinds)
        }
        // 微调项(提供才改;JSON null = 回默认)
        fun boolField(key: String, getter: () -> Boolean?, setter: (Boolean?) -> Unit) {
            val v = args.get(key) ?: return
            if (v.isJsonNull) setter(null)
            else if (v.isJsonPrimitive && v.asJsonPrimitive.isBoolean) setter(v.asBoolean)
            else throw McpTool.McpToolException("$key must be boolean or null")
        }
        fun intField(key: String, setter: (Int?) -> Unit) {
            val v = args.get(key) ?: return
            if (v.isJsonNull) setter(null)
            else if (v.isJsonPrimitive && v.asJsonPrimitive.isNumber) setter(v.asInt)
            else throw McpTool.McpToolException("$key must be number or null")
        }
        fun listField(key: String, setter: (List<String>?) -> Unit) {
            val v = args.get(key) ?: return
            if (v.isJsonNull) setter(null)
            else if (v.isJsonArray) setter(v.asJsonArray.mapNotNull { it?.takeIf { e -> e.isJsonPrimitive }?.asString }.ifEmpty { null })
            else throw McpTool.McpToolException("$key must be array or null")
        }
        boolField("logStack", { next.logStack }, { next = next.copy(logStack = it) })
        intField("maxBytesPerField", { next = next.copy(maxBytesPerField = it) })
        listField("ignoreStackPrefixes", { next = next.copy(ignoreStackPrefixes = it) })
        intField("maxResultRows", { next = next.copy(maxResultRows = it) })
        intField("maxResultChars", { next = next.copy(maxResultChars = it) })
        boolField("fileContentCapture", { next.fileContentCapture }, { next = next.copy(fileContentCapture = it) })
        listField("ignorePathPrefixes", { next = next.copy(ignorePathPrefixes = it) })
        kotlinx.coroutines.runBlocking { settings.setOverride(pkg, next) }
        settingsJson(pkg, settings.effectiveSwitch(pkg), settings.overrideFor(pkg))
    }

    private fun listenSettingsReset() = schemaTool(
        name = "listen_settings_reset",
        description = "Remove the per-app LISTEN-domain deviation entirely (app falls back to built-in " +
            "defaults for passive capture). Feature-domain deviations (spoof profile, blacklist appends, " +
            "keyword subset) are left untouched - use feature_settings_reset for those.",
        schema = objSchema(required = listOf("packageName")) {
            str("packageName", "Target package name")
        },
    ) { args ->
        val pkg = McpJson.str(args, "packageName") ?: throw McpTool.McpToolException("packageName is required")
        // V45 T3.5 / DUAL-12:按域清(旧全量清键会连带抹掉功能页定制)
        kotlinx.coroutines.runBlocking { settings.resetOverride(pkg, CapabilityDomain.LISTEN) }
        settingsJson(pkg, settings.effectiveSwitch(pkg), settings.overrideFor(pkg))
    }

    private fun taxonomyListen() = schemaTool(
        name = "taxonomy_listen",
        description = "LISTEN domain category taxonomy (${CategoryTaxonomy.forDomain(CapabilityDomain.LISTEN).size} " +
            "entries): recordType, kind id, kindKey ('recordType:kind'), noise tier, defaultEnabled. " +
            "Use kindKey for records filter and listen_settings_set kindEntries. " +
            "Labels are UI-localized and intentionally not exposed.",
        schema = emptySchema(),
    ) { _ ->
        val entries = CategoryTaxonomy.forDomain(CapabilityDomain.LISTEN).map { category ->
            McpJson.obj(
                "recordType" to com.google.gson.JsonPrimitive(category.type.wire),
                "kind" to com.google.gson.JsonPrimitive(category.id),
                "kindKey" to com.google.gson.JsonPrimitive(
                    AppListenOverride.kindKey(category.type, category.id),
                ),
                "domain" to com.google.gson.JsonPrimitive(category.domain.name),
                "defaultEnabled" to com.google.gson.JsonPrimitive(category.defaultEnabled),
                "noiseTier" to com.google.gson.JsonPrimitive(category.noiseTier.name),
            )
        }
        McpJson.obj("categories" to McpJson.arr(entries))
    }
}
