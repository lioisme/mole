package com.lioisme.modev.mcp.tools

import com.google.gson.JsonObject
import com.lioisme.modev.core.model.DeviceProfile
import com.lioisme.modev.core.model.ProfileEditorForm
import com.lioisme.modev.core.model.ProfileField
import com.lioisme.modev.core.model.ProfileFieldCatalog
import com.lioisme.modev.core.model.ProfileImportOutcome
import com.lioisme.modev.core.model.ProfileImportVerdict
import com.lioisme.modev.core.model.ProfileResolution
import com.lioisme.modev.core.model.ProfileValues
import com.lioisme.modev.core.model.ProfileValueType
import com.lioisme.modev.domain.config.ListenConfigService
import com.lioisme.modev.domain.config.SpoofProfileService

/**
 * 设备画像库工具组(V47 WP-7,5 个):画像存 / 删 / 绑 / 字段目录 / 信封导入。
 *
 * 上游 = [SpoofProfileService](画像库唯一写入口,AGENTS #18 方法层契约族)+
 * [ListenConfigService](每应用绑定 = 功能域偏差,与 `feature_settings_set` 同一条写路径,
 * 工具内**永不**直写 prefs、**永不**另开第二条绑定通路)。
 *
 * 三条不得退化的语义:
 *  1. **校验即拒绝**:提交的每个键值一律过 `ProfileFieldCatalog.validate`,非法即整笔拒
 *     并点名(字段 + `ProfileFieldError` + 原值),不钳制、不静默丢弃 —— 与契约 KDoc
 *     同一条红线,Agent 据此能改对而不是猜对;
 *  2. **未知键硬拒并回有效键集**:声明面即生效面(AGENTS #26)在画像面的镜像形态,
 *     「照抄了一个不存在的键」必须是显式失败;
 *  3. **禁静默回落**:绑定了不存在的画像 id 直接拒,绝不回落成「按默认档伪装」
 *     (旧 `SpoofProfiles.resolve` 把未知 id 吞成 auto 是 GAP-02 的实锤根因)。
 *
 * 画像正文**不是秘密**(与令牌族不同),故全量上屏:出参给完整的 id / 名称 /
 * 已填项数,`spoof_profiles_list` 给整张字段表,读出的键值可原样回灌 `spoof_profile_save`。
 */
class SpoofProfileTools(
    private val profiles: SpoofProfileService,
    private val settings: ListenConfigService,
) {

    val tools: List<McpTool> = listOf(
        spoofProfileSave(), spoofProfileDelete(), spoofProfileApply(),
        spoofProfileCatalog(), spoofProfileImport(),
    )

    // ---------------------------------------------------------------- 内部

    /**
     * 提交载荷 → 字段映射(目录序稳定)。
     *
     * 值一律归一为**编辑态文本**:字符串直取,数值/布尔取其字面形态(`withText` 桥本就
     * 按文本解析),`null` 归零为空串(= 取消该项干预,与 UI 清空输入框同一语义)。
     * 对象/数组不是标量 ⇒ 硬拒(静默 toString 会让 `{"imei":{"a":1}}` 变成一条合法请求)。
     */
    private fun suppliedFields(args: JsonObject): Map<ProfileField, String> {
        val payload = args.getAsJsonObject("fields")
            ?: throw McpTool.McpToolException("fields is required")
        val unknown = mutableListOf<String>()
        val out = LinkedHashMap<ProfileField, String>()
        payload.entrySet().forEach { (key, value) ->
            val field = ProfileFieldCatalog.fieldByJsonKey(key)
            if (field == null) {
                unknown += key
            } else {
                out[field] = when {
                    value == null || value.isJsonNull -> ""
                    value.isJsonPrimitive -> value.asString
                    else -> throw McpTool.McpToolException(
                        "fields.$key must be a string, number, boolean or null",
                    )
                }
            }
        }
        if (unknown.isNotEmpty()) {
            throw McpTool.McpToolException(
                "Unknown profile field key(s): ${unknown.joinToString(",")} " +
                    "(valid keys: ${FIELD_KEYS.joinToString(",")})",
            )
        }
        if (out.isEmpty()) {
            throw McpTool.McpToolException("fields must declare at least one catalog key")
        }
        return out
    }

    /** 绑定解析三态 → 线名(与 [ProfileResolution] 一一对应,穷尽 when)。 */
    private fun resolutionName(resolution: ProfileResolution): String = when (resolution) {
        ProfileResolution.Unbound -> "UNBOUND"
        is ProfileResolution.Bound -> "BOUND"
        is ProfileResolution.Missing -> "MISSING"
    }

    /** 哨兵行为派生:「该值 = 不生效」的判定随取值形态而定(穷尽 [ProfileValueType])。 */
    private fun sentinelOf(type: ProfileValueType): String = when (type) {
        ProfileValueType.TEXT -> "empty string"
        ProfileValueType.INT -> "${ProfileValues.UNSET_INT}"
        ProfileValueType.DOUBLE -> "${ProfileValues.UNSET_DOUBLE}"
        ProfileValueType.BOOL -> "false"
        ProfileValueType.NETWORK_TYPE -> "${ProfileValues.NET_UNHOOK}"
        ProfileValueType.SCREENSHOT_MODE -> "${ProfileValues.SCREENSHOT_UNHOOK}"
    }

    /**
     * 枚举槽字段的合法槽位集(V47 WP-7):由 `validate` **反推**(扫一遍取值窗口留合法者),
     * 而不是在本文件再抄一张槽表。
     *
     * 手抄是真的会写错的:本工具第一版在说明文字里把 `screenshotsFlag` 的 0/1 两个槽
     * 写反了方向(0 实为「强制禁止」)—— 槽集自此只有目录能回答,文字不复述槽位含义。
     */
    private fun acceptedSlots(field: ProfileField): List<Int>? = when (field.type) {
        ProfileValueType.NETWORK_TYPE, ProfileValueType.SCREENSHOT_MODE ->
            SLOT_PROBE_WINDOW.filter { ProfileFieldCatalog.validate(field, it.toString()) == null }
        else -> null
    }

    /** 单字段元数据:全部派生自 [ProfileFieldCatalog],本文件零手抄字段表。 */
    private fun fieldJson(field: ProfileField): JsonObject {
        val range = ProfileFieldCatalog.rangeOf(field)
        return McpJson.obj(
            "key" to ProfileFieldCatalog.jsonKeyOf(field),
            "field" to field.name,
            "kind" to field.kind.name,
            "section" to field.section.name,
            "type" to field.type.name,
            "baselineKey" to field.baselineKey,
            "randomizable" to ProfileFieldCatalog.isRandomizable(field),
            "rangeMin" to range?.first,
            "rangeMax" to range?.last,
            "acceptedSlots" to acceptedSlots(field)?.map { it.toString() },
            "sentinel" to sentinelOf(field.type),
        )
    }

    /** 导入结论 → 出参(UI 提示与 MCP 同一份 [ProfileImportOutcome],禁两侧各算一遍)。 */
    private fun outcomeJson(outcome: ProfileImportOutcome): JsonObject {
        val counts = LinkedHashMap<String, Int>()
        ProfileImportVerdict.entries.forEach { verdict ->
            counts[verdict.name] = outcome.verdicts.count { it == verdict }
        }
        return McpJson.obj(
            "schemaRejected" to outcome.schemaRejected,
            "imported" to outcome.imported,
            "skipped" to outcome.skipped,
            "rejected" to outcome.rejected,
            "items" to outcome.verdicts.size,
            "verdicts" to McpJson.arr(outcome.verdicts.map { it.name }),
            "verdictCounts" to counts,
        )
    }

    // ---------------------------------------------------------------- 工具

    private fun spoofProfileSave() = schemaTool(
        name = "spoof_profile_save",
        description = "Create a DIY device profile (omit profileId) or update an existing one (profileId " +
            "present; supplied fields merge into that entry's current body, the rest stay). " +
            "fields is an object keyed by spoof_profile_catalog keys; values may be strings, numbers " +
            "or booleans, and null or empty string clears that single intervention (see each field's " +
            "sentinel there; acceptedSlots there lists the legal slots of enum-typed fields). " +
            "Every value is validated by the field catalog and any illegal one rejects the whole call " +
            "naming the field, its error and the submitted value - nothing is clamped or silently " +
            "dropped. A profile must fill at least one field. Returns profileId + filledCount.",
        schema = {
            required(listOf("name", "fields"))
            str("name", "Profile display name (non-blank)")
            str("summary", "Optional one-line summary")
            str("profileId", "Existing profile id to update (optional; omit to create)")
            obj("fields", "Catalog json-key -> value; at least one key")
        },
    ) { args ->
        val name = McpJson.str(args, "name")?.trim().orEmpty()
        if (name.isEmpty()) throw McpTool.McpToolException("name must not be blank")
        val summary = McpJson.str(args, "summary").orEmpty().trim()
        val requestedId = McpJson.str(args, "profileId")?.trim().orEmpty()
        val supplied = suppliedFields(args)
        val existing = if (requestedId.isEmpty()) {
            null
        } else {
            profiles.find(requestedId)
                ?: throw McpTool.McpToolException("Unknown profileId '$requestedId' (see spoof_profiles_list)")
        }
        // 表单 = 编辑态与正文之间唯一中间层(UI 与 MCP 同一张表,同一套校验)。
        val form = ProfileEditorForm(existing?.profile ?: DeviceProfile.EMPTY)
        supplied.forEach { (field, text) -> form.update(field, text) }
        val invalid = ProfileField.entries.filter { it in supplied }.mapNotNull { field ->
            form.errorOf(field)?.let { field to it }
        }
        if (invalid.isNotEmpty()) {
            throw McpTool.McpToolException(
                "Rejected ${invalid.size} invalid profile field(s): " +
                    invalid.joinToString("; ") { (field, error) ->
                        "${ProfileFieldCatalog.jsonKeyOf(field)}='${form.textOf(field)}' -> $error"
                    },
            )
        }
        val profile = form.snapshot()
        val saved = if (existing == null) {
            kotlinx.coroutines.runBlocking { profiles.create(name, summary, profile) }
                ?: throw McpTool.McpToolException(
                    "Create rejected: a saved profile must fill at least one field " +
                        "(submitted filledCount=${profile.filledCount})",
                )
        } else {
            val ok = kotlinx.coroutines.runBlocking {
                profiles.update(existing.id, name, summary, profile)
            }
            if (!ok) throw McpTool.McpToolException("Update rejected for profileId '${existing.id}'")
            profiles.find(existing.id) ?: existing
        }
        McpJson.obj(
            "ok" to true,
            "profileId" to saved.id,
            "created" to (existing == null),
            "name" to saved.name,
            "filledCount" to saved.filledCount,
        )
    }

    private fun spoofProfileDelete() = schemaTool(
        name = "spoof_profile_delete",
        description = "DANGER: delete one device profile by id. Requires confirm=true. Deleting a built-in " +
            "profile writes a tombstone, so upgrades will not re-add it. Packages still bound to the " +
            "id then resolve to MISSING (no silent fallback) - re-bind them via spoof_profile_apply.",
        schema = {
            required(listOf("profileId", "confirm"))
            str("profileId", "Profile id to delete (see spoof_profiles_list)")
            bool("confirm", "Must be true to execute this destructive action")
        },
    ) { args ->
        if (McpJson.bool(args, "confirm") != true) {
            throw McpTool.McpToolException("confirm=true is required for destructive delete")
        }
        val id = McpJson.str(args, "profileId")?.trim().orEmpty()
        if (id.isEmpty()) throw McpTool.McpToolException("profileId must not be blank")
        val deleted = kotlinx.coroutines.runBlocking { profiles.delete(id) }
        if (!deleted) throw McpTool.McpToolException("Unknown profileId '$id' (see spoof_profiles_list)")
        McpJson.obj("ok" to true, "profileId" to id)
    }

    private fun spoofProfileApply() = schemaTool(
        name = "spoof_profile_apply",
        description = "Bind a package to a device profile, or unbind it. Absent or empty profileId removes " +
            "the per-app binding so the package follows the default again. Written as the FEATURE-domain " +
            "per-app deviation through the same ListenConfigService path as feature_settings_set " +
            "(persist + targeted push in one step); this tool never touches preferences directly. An " +
            "unknown profileId is refused instead of silently falling back to another profile.",
        schema = {
            required(listOf("packageName"))
            str("packageName", "Target package name")
            str("profileId", "Library profile id to bind (optional; absent or empty = unbind)")
        },
    ) { args ->
        val pkg = McpJson.str(args, "packageName")?.trim().orEmpty()
        if (pkg.isEmpty()) throw McpTool.McpToolException("packageName must not be blank")
        val id = McpJson.str(args, "profileId")?.trim().orEmpty()
        if (id.isNotEmpty() && profiles.find(id) == null) {
            throw McpTool.McpToolException("Unknown profileId '$id' (see spoof_profiles_list)")
        }
        val next = settings.overrideFor(pkg).copy(spoofProfileId = id.ifEmpty { null })
        kotlinx.coroutines.runBlocking { settings.setOverride(pkg, next) }
        val stored = settings.overrideFor(pkg)
        val resolution = profiles.resolve(stored.spoofProfileId)
        McpJson.obj(
            "packageName" to pkg,
            "profileId" to stored.spoofProfileId,
            "resolution" to resolutionName(resolution),
            "boundName" to (resolution as? ProfileResolution.Bound)?.entry?.name,
            "filledCount" to (resolution as? ProfileResolution.Bound)?.entry?.filledCount,
            "deviationCount" to stored.deviationCount,
        )
    }

    private fun spoofProfileCatalog() = schemaTool(
        name = "spoof_profile_catalog",
        description = "Device profile field catalog - the discovery surface for spoof_profile_save payloads. " +
            "Returns every ${ProfileField.entries.size} field with its json key, intervention kind, " +
            "editor section, value type, whether one-click randomize touches it, its declared numeric " +
            "range (null = unbounded), the accepted slots of enum-typed fields (derived by running the " +
            "catalog validator, so it is exactly what save accepts) and the sentinel value meaning " +
            "'this field does nothing', plus the section/kind groupings and the per-type sentinel table. " +
            "Slot meanings: networkType -1 hides the active network, 1 is Wi-Fi, 2-5 are 2G-5G; " +
            "screenshotsFlag 0 forces screenshots off (FLAG_SECURE added), 1 forces them on " +
            "(FLAG_SECURE cleared). Derived at runtime from the single field catalog, so it can never " +
            "disagree with what spoof_profile_save accepts.",
        schema = emptySchema(),
    ) { _ ->
        val sections = ProfileFieldCatalog.bySection.entries.map { (section, fields) ->
            McpJson.obj(
                "section" to section.name,
                "keys" to fields.map { ProfileFieldCatalog.jsonKeyOf(it) },
            )
        }
        val kinds = ProfileFieldCatalog.byKind.entries
            .filter { it.value.isNotEmpty() }
            .map { (kind, fields) ->
                McpJson.obj(
                    "kind" to kind.name,
                    "keys" to fields.map { ProfileFieldCatalog.jsonKeyOf(it) },
                )
            }
        McpJson.obj(
            "total" to ProfileField.entries.size,
            "fields" to McpJson.arr(ProfileField.entries.map { fieldJson(it) }),
            "sections" to McpJson.arr(sections),
            "kinds" to McpJson.arr(kinds),
            "sentinels" to ProfileValueType.entries.associate { it.name to sentinelOf(it) },
        )
    }

    private fun spoofProfileImport() = schemaTool(
        name = "spoof_profile_import",
        description = "Import profiles from a transfer envelope JSON string: " +
            "{schemaVersion, exportedAt, items:[{name, summary, profile}]}. A version mismatch rejects " +
            "the whole envelope (schemaRejected=true, nothing written) - there is no cross-version " +
            "guessing. Per item the result reports IMPORTED / DUPLICATE / IMPORTED_NAME_FALLBACK / " +
            "REJECTED_EMPTY / REJECTED_INVALID / REJECTED_LIMIT / DRY_RUN_OK; blank names fall back to " +
            "\"$IMPORT_DEFAULT_NAME\". dryRun=true validates without persisting. Existing profiles are " +
            "never overwritten - import creates new entries.",
        schema = {
            required(listOf("text"))
            str("text", "Profile envelope JSON text")
            bool("dryRun", "Validate only, do not persist (default false)")
        },
    ) { args ->
        val text = McpJson.str(args, "text") ?: throw McpTool.McpToolException("text is required")
        val dryRun = McpJson.bool(args, "dryRun") ?: false
        val outcome = kotlinx.coroutines.runBlocking {
            profiles.importJson(text, dryRun, IMPORT_DEFAULT_NAME)
        }
        outcomeJson(outcome)
    }

    private companion object {

        /** 目录键集(硬拒未知键时回给 Agent;派生自目录,零手抄)。 */
        val FIELD_KEYS: List<String> = ProfileField.entries.map { ProfileFieldCatalog.jsonKeyOf(it) }

        /** 导入无名列的回落名(MCP 面恒英文,与 UI 侧四语资源同一参数位;AGENTS #1 D-3 落点)。 */
        const val IMPORT_DEFAULT_NAME = "Imported profile"

        /** [acceptedSlots] 的取值探测窗(仅枚举槽字段进入;合法集由目录 `validate` 裁决)。 */
        val SLOT_PROBE_WINDOW: IntRange = -10..40
    }
}
