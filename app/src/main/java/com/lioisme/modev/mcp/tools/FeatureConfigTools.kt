package com.lioisme.modev.mcp.tools

import com.google.gson.JsonObject
import com.lioisme.modev.core.model.AppListenOverride
import com.lioisme.modev.core.model.CapabilityDomain
import com.lioisme.modev.core.model.CategoryTaxonomy
import com.lioisme.modev.core.model.FeatureGroup
import com.lioisme.modev.core.model.FeatureGroups
import com.lioisme.modev.core.model.HookSwitch
import com.lioisme.modev.core.model.RecordType
import com.lioisme.modev.domain.config.ListenConfigService

/**
 * 功能配置组工具(V38,4 个):每应用功能偏差读/写/重置 + FEATURE 域分类法(6 组 37 类)。
 * 上游与 PerAppFeatureSettingsViewModel **同契约面**(V45 T2.2:同一 [ListenConfigService]
 * 偏差存储 —— feature 域字段 = masters 4 项 + 功能 kind + V37 功能数据段)。
 *
 * V47 WP-7:`spoofProfileId` 自此由「3 个编译期常量的 enum」放开为**画像库主键自由串**
 * (DIY 画像的键在运行时才存在,schema 枚举无法广告)。`spoofOverrides` 按决策 D-3
 * **原样冻结保留**为只读遗留通道:不删、不扩写入面。
 */
class FeatureConfigTools(private val settings: ListenConfigService) {

    val tools: List<McpTool> = listOf(
        featureSettingsGet(), featureSettingsSet(), featureSettingsReset(), taxonomyFeature(),
    )

    /** 功能域 master 位(null = 回默认;名集 = FEATURE 域类别类型派生,路由共享 [applyMaster])。 */
    private fun withFeatureMaster(current: AppListenOverride, master: String, value: Boolean?): AppListenOverride {
        val type = RecordType.fromWire(master)
            ?: throw McpTool.McpToolException(
                "Unknown feature master '$master'; valid: ${featureMasterWires.joinToString(",")}",
            )
        if (master !in featureMasterWires) {
            throw McpTool.McpToolException(
                "'$master' is not a feature-domain master; valid: ${featureMasterWires.joinToString(",")}",
            )
        }
        return applyMaster(current, type, value)
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
            "deviationCount. Feature masters (see feature_settings_set 'master'): " +
            featureMasterWires.joinToString("/") + ".",
        schema = { required(listOf("packageName")); str("packageName", "Target package name") },
    ) { args ->
        val pkg = McpJson.str(args, "packageName") ?: throw McpTool.McpToolException("packageName is required")
        settingsJson(pkg, settings.effectiveSwitch(pkg), settings.overrideFor(pkg))
    }

    private fun featureSettingsSet() = schemaTool(
        name = "feature_settings_set",
        description = "Merge per-app feature deviation. master: " +
            featureMasterWires.joinToString("|") + " with masterValue true/false/null. kindEntries for " +
            "feature category keys (kindKey form '<recordType>:<kind>' from taxonomy_feature; bare ids " +
            "accepted and resolved via taxonomy). Feature data fields: spoofProfileId (a device-profile " +
            "library key, free-form since V47 WP-7 - see spoof_profiles_list; spoof_profile_apply is the " +
            "validated single-purpose binding entry), spoofOverrides (legacy frozen override map, " +
            "null resets), extraRootPackages/extraXposedPrefixes/extraMaskKeywords (string arrays), " +
            "extraFridaPorts (integer array), keywordIds (library entry ids), keywordFollowGlobal " +
            "(true = follow global enabled set; normalized to default). Empty arrays reset the field to default.",
        schema = {
            required(listOf("packageName"))
            str("packageName", "Target package name")
            str("master", "Feature master (feature-domain record types)", featureMasterWires)
            bool("masterValue", "true/false for the chosen master; omit key or JSON null to reset to default")
            obj("kindEntries", "Map of feature kindKey -> true/false/null")
            str("spoofProfileId", "Device profile library key (see spoof_profiles_list)")
            obj("spoofOverrides", "Spoof field overrides map (optional; null resets)")
            array("extraRootPackages", "Root package blacklist appends (optional)")
            array("extraXposedPrefixes", "Xposed prefix blacklist appends (optional)")
            array("extraMaskKeywords", "Mask keyword appends (optional)")
            array("extraFridaPorts", "Frida port appends, integers (optional)", itemType = "integer")
            array("keywordIds", "Keyword library entry ids, integers (optional)", itemType = "integer")
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
                val parsed = AppListenOverride.parseKindKey(id)
                    ?: throw McpTool.McpToolException(
                        "Unknown kind '$id'; use kindKey '<recordType>:<kind>' from taxonomy_feature",
                    )
                val key = AppListenOverride.kindKey(parsed.first, parsed.second)
                // V45 T2.4 / DUAL-10:双域闸镜像面 —— 功能工具只接受 FEATURE 域类别
                // (被动采集类必须走 listen_settings_set,禁越域写;AGENTS #23)。
                if (!CategoryTaxonomy.isFeature(parsed.first, parsed.second)) {
                    throw McpTool.McpToolException(
                        "Kind '$key' is LISTEN-domain; set it via listen_settings_set",
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
            else if (v.isJsonPrimitive && v.asJsonPrimitive.isBoolean)
                // V44 Q2:true = 跟随全局 = 默认态,归一写 null(与 UI 写语义同型,防幽灵偏差落盘)
                next.copy(keywordFollowGlobal = if (v.asBoolean) null else false)
            else throw McpTool.McpToolException("keywordFollowGlobal must be boolean or null")
        }
        kotlinx.coroutines.runBlocking { settings.setOverride(pkg, next) }
        settingsJson(pkg, settings.effectiveSwitch(pkg), settings.overrideFor(pkg))
    }

    private fun featureSettingsReset() = schemaTool(
        name = "feature_settings_reset",
        description = "Remove the per-app FEATURE-domain deviation entirely (intervention switches, " +
            "spoof profile, blacklist appends, keyword subset back to built-in defaults). " +
            "Listen-domain capture deviations are left untouched - use listen_settings_reset for those.",
        schema = objSchema(required = listOf("packageName")) {
            str("packageName", "Target package name")
        },
    ) { args ->
        val pkg = McpJson.str(args, "packageName") ?: throw McpTool.McpToolException("packageName is required")
        // V45 T3.5 / DUAL-12:按域清(旧全量清键会连带抹掉监听页定制)
        kotlinx.coroutines.runBlocking { settings.resetOverride(pkg, CapabilityDomain.FEATURE) }
        settingsJson(pkg, settings.effectiveSwitch(pkg), settings.overrideFor(pkg))
    }

    private fun taxonomyFeature() = schemaTool(
        name = "taxonomy_feature",
        description = "FEATURE domain taxonomy: ${FeatureGroups.ALL.size} feature groups (" +
            FeatureGroups.ALL.joinToString(", ") { it.name } + ") with their " +
            FeatureGroups.ALL.sumOf { FeatureGroups.kindIds(it).size } + " kind ids (kindKey = " +
            "'<recordType>:<kind>'), observed record type and default state. Use kindKey for " +
            "feature_settings_set kindEntries.",
        schema = emptySchema(),
    ) { _ ->
        val groups = FeatureGroups.ALL.map { group ->
            val kinds = FeatureGroups.kindRefs(group).map { (type, kindId) ->
                val category = CategoryTaxonomy.byId(type, kindId)
                McpJson.obj(
                    "kind" to com.google.gson.JsonPrimitive(kindId),
                    "kindKey" to com.google.gson.JsonPrimitive(AppListenOverride.kindKey(type, kindId)),
                    "recordType" to McpJson.tree(type.wire),
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
