package com.lioisme.modev.mcp.tools

import com.google.gson.JsonObject
import com.lioisme.modev.core.model.AppListenOverride
import com.lioisme.modev.core.model.CapabilityDomain
import com.lioisme.modev.core.model.CategoryTaxonomy
import com.lioisme.modev.core.model.FeatureGroup
import com.lioisme.modev.core.model.FeatureGroups
import com.lioisme.modev.core.model.HookSwitch
import com.lioisme.modev.data.repo.SettingsRepository

/**
 * 功能配置组工具(V38,4 个):每应用功能偏差读/写/重置 + FEATURE 域分类法(6 组 37 类)。
 * 上游与 PerAppFeatureSettingsViewModel 完全同源(同一 AppListenOverride 存储,
 * feature 域字段 = masters 4 项 + 功能 kind + V37 功能数据段)。
 */
class FeatureConfigTools(private val settings: SettingsRepository) {

    val tools: List<McpTool> = listOf(
        featureSettingsGet(), featureSettingsSet(), featureSettingsReset(), taxonomyFeature(),
    )

    /** 功能域 master 位(null = 回默认)。 */
    private fun withFeatureMaster(current: AppListenOverride, master: String, value: Boolean?): AppListenOverride =
        when (master) {
            "ui" -> current.copy(uiListen = value)
            "network" -> current.copy(networkListen = value)
            "device_env" -> current.copy(deviceEnvListen = value)
            "process" -> current.copy(processControlListen = value)
            else -> throw McpTool.McpToolException("Unknown feature master '$master'")
        }

    private fun settingsJson(pkg: String, effective: HookSwitch, override: AppListenOverride): JsonObject =
        McpJson.obj(
            "packageName" to com.google.gson.JsonPrimitive(pkg),
            "effective" to McpJson.tree(effective),
            "override" to McpJson.tree(override),
            "deviationCount" to com.google.gson.JsonPrimitive(override.deviationCount),
        )

    private fun featureSettingsGet() = schemaTool(
        name = "feature_settings_get",
        description = "Read per-app feature (intervention) deviation: effective HookSwitch incl. the feature " +
            "section (spoof profile, blacklist appends, keyword subset), raw AppListenOverride and " +
            "deviationCount. Feature masters: uiListen/networkListen/deviceEnvListen/processControlListen.",
        schema = { required(listOf("packageName")); str("packageName", "Target package name") },
    ) { args ->
        val pkg = McpJson.str(args, "packageName") ?: throw McpTool.McpToolException("packageName is required")
        settingsJson(pkg, settings.effectiveSwitch(pkg), settings.overrideFor(pkg))
    }

    private fun featureSettingsSet() = schemaTool(
        name = "feature_settings_set",
        description = "Merge per-app feature deviation. master: ui|network|device_env|process with " +
            "masterValue true/false/null. kindEntries for feature kind ids (taxonomy_feature). " +
            "Feature data fields: spoofProfileId (auto|pixel7pro|xiaomi13), spoofOverrides (map, " +
            "null resets), extraRootPackages/extraXposedPrefixes/extraMaskKeywords (string arrays), " +
            "extraFridaPorts (integer array), keywordIds (library entry ids), keywordFollowGlobal " +
            "(true = follow global enabled set). Empty arrays reset the field to default.",
        schema = {
            required(listOf("packageName"))
            str("packageName", "Target package name")
            str("master", "Feature master: ui|network|device_env|process", listOf("ui", "network", "device_env", "process"))
            str("masterValue", "true/false, or null to reset to default")
            obj("kindEntries", "Map of feature kind id -> true/false/null")
            str("spoofProfileId", "Spoof profile id (optional)")
            obj("spoofOverrides", "Spoof field overrides map (optional; null resets)")
            array("extraRootPackages", "Root package blacklist appends (optional)")
            array("extraXposedPrefixes", "Xposed prefix blacklist appends (optional)")
            array("extraFridaPorts", "Frida port appends, integers (optional)")
            array("extraMaskKeywords", "Mask keyword appends (optional)")
            array("keywordIds", "Keyword library entry ids, integers (optional)")
            bool("keywordFollowGlobal", "Follow global enabled keyword set (optional)")
        },
    ) { args ->
        val pkg = McpJson.str(args, "packageName") ?: throw McpTool.McpToolException("packageName is required")
        var next = settings.overrideFor(pkg)
        val master = McpJson.str(args, "master")
        val masterValue = args.get("masterValue")
        if (master != null) {
            next = if (masterValue == null || masterValue.isJsonNull) {
                withFeatureMaster(next, master, null)
            } else {
                require(masterValue.isJsonPrimitive && masterValue.asJsonPrimitive.isBoolean) {
                    "masterValue must be boolean or null"
                }
                withFeatureMaster(next, master, masterValue.asBoolean)
            }
        }
        val kindEntries = args.getAsJsonObject("kindEntries")
        if (kindEntries != null) {
            val kinds = next.kinds.toMutableMap()
            kindEntries.entrySet().forEach { (id, value) ->
                when {
                    value.isJsonNull -> kinds.remove(id)
                    value.isJsonPrimitive && value.asJsonPrimitive.isBoolean -> kinds[id] = value.asBoolean
                    else -> throw McpTool.McpToolException("kindEntries values must be boolean or null")
                }
            }
            next = next.copy(kinds = kinds)
        }
        McpJson.str(args, "spoofProfileId")?.let { next = next.copy(spoofProfileId = it) }
        args.get("spoofOverrides")?.let { v ->
            next = if (v.isJsonNull) {
                next.copy(spoofOverrides = null)
            } else {
                require(v.isJsonObject) { "spoofOverrides must be object or null" }
                val map = v.asJsonObject.entrySet().mapNotNull { (k, e) ->
                    e?.takeIf { it.isJsonPrimitive }?.asString?.let { k to it }
                }.toMap()
                next.copy(spoofOverrides = map.ifEmpty { null })
            }
        }
        McpJson.strList(args, "extraRootPackages")?.let { next = next.copy(extraRootPackages = it) }
        McpJson.strList(args, "extraXposedPrefixes")?.let { next = next.copy(extraXposedPrefixes = it) }
        McpJson.strList(args, "extraMaskKeywords")?.let { next = next.copy(extraMaskKeywords = it) }
        args.get("extraFridaPorts")?.let { v ->
            if (v.isJsonArray) {
                val ports = v.asJsonArray.mapNotNull { it?.takeIf { e -> e.isJsonPrimitive && e.asJsonPrimitive.isNumber }?.asInt }
                next = next.copy(extraFridaPorts = ports.ifEmpty { null })
            } else if (v.isJsonNull) {
                next = next.copy(extraFridaPorts = null)
            }
        }
        args.get("keywordIds")?.let { v ->
            if (v.isJsonArray) {
                val ids = v.asJsonArray.mapNotNull { it?.takeIf { e -> e.isJsonPrimitive && e.asJsonPrimitive.isNumber }?.asLong }
                next = next.copy(keywordIds = ids.ifEmpty { null })
            } else if (v.isJsonNull) {
                next = next.copy(keywordIds = null)
            }
        }
        args.get("keywordFollowGlobal")?.let { v ->
            next = if (v.isJsonNull) next.copy(keywordFollowGlobal = null)
            else if (v.isJsonPrimitive && v.asJsonPrimitive.isBoolean) next.copy(keywordFollowGlobal = v.asBoolean)
            else throw McpTool.McpToolException("keywordFollowGlobal must be boolean or null")
        }
        kotlinx.coroutines.runBlocking { settings.setOverride(pkg, next) }
        settingsJson(pkg, settings.effectiveSwitch(pkg), settings.overrideFor(pkg))
    }

    private fun featureSettingsReset() = schemaTool(
        name = "feature_settings_reset",
        description = "Remove the per-app feature deviation entirely.",
        schema = { required(listOf("packageName")); str("packageName", "Target package name") },
    ) { args ->
        val pkg = McpJson.str(args, "packageName") ?: throw McpTool.McpToolException("packageName is required")
        kotlinx.coroutines.runBlocking { settings.removeOverride(pkg) }
        settingsJson(pkg, settings.effectiveSwitch(pkg), settings.overrideFor(pkg))
    }

    private fun taxonomyFeature() = schemaTool(
        name = "taxonomy_feature",
        description = "FEATURE domain taxonomy: 6 feature groups (DIALOG_INTERCEPT, PROCESS_GUARD, " +
            "SSL_BYPASS, PROXY_VPN_HIDE, ROOT_XPOSED_HIDE, DEVICE_SPOOF) with their 37 kind ids, " +
            "observed record type and default state. Use kind ids for feature_settings_set kindEntries.",
        schema = emptySchema(),
    ) { _ ->
        val groups = FeatureGroups.ALL.map { group ->
            val kinds = FeatureGroups.kindIds(group).map { kindId ->
                val recordType = FeatureGroups.recordType(group)
                val category = recordType?.let { CategoryTaxonomy.byId(it, kindId) }
                McpJson.obj(
                    "kind" to com.google.gson.JsonPrimitive(kindId),
                    "recordType" to McpJson.tree(recordType?.wire),
                    "defaultEnabled" to com.google.gson.JsonPrimitive(category?.defaultEnabled ?: false),
                )
            }
            McpJson.obj(
                "group" to com.google.gson.JsonPrimitive(group.name),
                "kinds" to McpJson.arr(kinds),
            )
        }
        McpJson.obj("groups" to McpJson.arr(groups))
    }
}
