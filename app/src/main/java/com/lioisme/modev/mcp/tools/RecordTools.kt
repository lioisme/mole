package com.lioisme.modev.mcp.tools

import com.google.gson.JsonObject
import com.lioisme.modev.core.model.RecordType
import com.lioisme.modev.domain.record.RecordDraft
import com.lioisme.modev.domain.record.RecordFilter
import com.lioisme.modev.domain.record.RecordService
import com.lioisme.modev.domain.record.SortOrder
import com.lioisme.modev.domain.record.hasConstraint
import kotlinx.coroutines.flow.first

/**
 * 记录组工具(V38,14 个):查询/统计/详情/邻接/写路径/清理/存储。
 * 全部仅经 [RecordService] 上游(与 UI ViewModels 同源,R4/D5)。
 */
class RecordTools(private val logs: RecordService) {

    val tools: List<McpTool> = listOf(
        recordsQuery(), recordsCount(), recordsTypeCounts(), recordsCategoryCounts(),
        recordGet(), recordNeighbor(), recordCreate(), recordUpdate(), recordDelete(),
        recordsDeleteByFilter(), recordsClearPackage(), recordsClearType(), recordsClearAll(),
        storageUsage(),
    )

    // ---- 过滤参数解析(records_query / records_count / records_delete_by_filter 共享) ----

    private fun parseRecordType(args: JsonObject): RecordType? {
        val wire = McpJson.str(args, "recordType") ?: return null
        return RecordType.fromWire(wire) ?: throw McpTool.McpToolException(
            "Unknown recordType '$wire'; valid: ${RecordType.entries.joinToString(",") { it.wire }}",
        )
    }

    private fun parseFilter(args: JsonObject): RecordFilter = RecordFilter(
        recordTypes = parseRecordType(args)?.let { setOf(it.wire) },
        packages = McpJson.str(args, "packageName")?.let { setOf(it) },
        kinds = McpJson.str(args, "kind")?.let { setOf(it) },
        operations = McpJson.str(args, "operation")?.let { setOf(it) },
        query = McpJson.str(args, "query")?.takeIf { it.isNotBlank() },
        timeRange = run {
            val start = McpJson.long(args, "timeStart")
            val end = McpJson.long(args, "timeEnd")
            if (start != null || end != null) LongRange(start ?: 0L, end ?: Long.MAX_VALUE) else null
        },
        sort = if (McpJson.str(args, "sort")?.equals("asc", ignoreCase = true) == true) SortOrder.ASC else SortOrder.DESC,
    )

    // ---- 工具 ----

    private fun recordsQuery() = schemaTool(
        name = "records_query",
        description = "Query captured records with filters (paged). Returns items, total, page, pageSize. " +
            "recordType is one of: crypto, db, behavior, prefs, webview, network, device_env, ui, system, process. " +
            "kind is a category id within that type (see taxonomy_listen). query does SQL-LIKE substring match.",
        schema = filterSchema(pageable = true),
    ) { args ->
        val page = (McpJson.int(args, "page") ?: 0).coerceAtLeast(0)
        val pageSize = (McpJson.int(args, "pageSize") ?: 50).coerceIn(1, 200)
        val result = logs.queryPage(parseFilter(args), page, pageSize)
        McpJson.obj(
            "items" to McpJson.arr(result.items.map { McpDtos.row(it) }),
            "total" to com.google.gson.JsonPrimitive(result.total),
            "page" to com.google.gson.JsonPrimitive(result.page),
            "pageSize" to com.google.gson.JsonPrimitive(result.pageSize),
        )
    }

    private fun recordsCount() = schemaTool(
        name = "records_count",
        description = "Count records matching the filter (same filter parameters as records_query).",
        schema = filterSchema(pageable = false),
    ) { args ->
        McpJson.obj("count" to com.google.gson.JsonPrimitive(logs.count(parseFilter(args)).first()))
    }

    private fun recordsTypeCounts() = schemaTool(
        name = "records_type_counts",
        description = "Per-record-type counts across all packages (data management overview).",
        schema = emptySchema(),
    ) { _ ->
        val counts = logs.observeTypeCounts().first()
        McpJson.obj("counts" to McpJson.arr(counts.map { McpJson.tree(it) }))
    }

    private fun recordsCategoryCounts() = schemaTool(
        name = "records_category_counts",
        description = "Per-category (kind) counts for one record type (filter menu badges).",
        schema = objSchema(required = listOf("recordType")) {
            str("recordType", "Record type wire name, e.g. crypto")
        },
    ) { args ->
        val type = parseRecordType(args) ?: throw McpTool.McpToolException("recordType is required")
        McpJson.obj("counts" to McpJson.tree(logs.categoryCounts(type)))
    }

    private fun recordGet() = schemaTool(
        name = "record_get",
        description = "Full record detail by id (all fields incl. content; strings over 1MiB are truncated " +
            "with a top-level truncated flag). Use records_query first to discover ids.",
        schema = objSchema(required = listOf("id")) { long("id", "Record id (from records_query)") },
    ) { args ->
        val id = McpJson.long(args, "id") ?: throw McpTool.McpToolException("id is required")
        val detail = logs.get(id).first() ?: throw McpTool.McpToolException("Record $id not found")
        McpDtos.detail(detail)
    }

    private fun recordNeighbor() = schemaTool(
        name = "record_neighbor",
        description = "Neighbor record id in the same type+package timeline (newer=true = newer record).",
        schema = objSchema(required = listOf("id", "newer")) {
            long("id", "Record id")
            bool("newer", "true = next newer record, false = next older")
        },
    ) { args ->
        val id = McpJson.long(args, "id") ?: throw McpTool.McpToolException("id is required")
        val newer = McpJson.bool(args, "newer") ?: throw McpTool.McpToolException("newer is required")
        McpJson.obj("id" to McpJson.tree(logs.neighbor(id, newer)))
    }

    private fun recordCreate() = schemaTool(
        name = "record_create",
        description = "Create (import) a record. Requires packageName, kind, operation (valid enum names " +
            "per taxonomy; e.g. crypto CIPHER/ENCRYPT). Unknown fields are rejected; see RecordDraft contract.",
        schema = draftSchema(required = listOf("packageName", "kind", "operation")),
    ) { args ->
        val draft = draftFromArgs(args)
        McpJson.obj("id" to com.google.gson.JsonPrimitive(logs.create(draft)))
    }

    private fun recordUpdate() = schemaTool(
        name = "record_update",
        description = "Full replacement update by id (upsert semantics). Fetch with record_get first and " +
            "send the complete modified draft; missing optional fields fall back to defaults.",
        schema = objSchema(required = listOf("id")) {
            long("id", "Record id to overwrite")
            draft("draft", "Full record draft (same fields as record_create)")
        },
    ) { args ->
        val id = McpJson.long(args, "id") ?: throw McpTool.McpToolException("id is required")
        val delta = args.getAsJsonObject("draft") ?: throw McpTool.McpToolException("draft object is required")
        val draft = draftFromArgs(delta)
        if (!logs.update(id, draft)) throw McpTool.McpToolException("Record $id not found")
        McpJson.obj("ok" to com.google.gson.JsonPrimitive(true), "id" to com.google.gson.JsonPrimitive(id))
    }

    private fun recordDelete() = schemaTool(
        name = "record_delete",
        description = "Delete one record by id.",
        schema = objSchema(required = listOf("id")) { long("id", "Record id") },
    ) { args ->
        val id = McpJson.long(args, "id") ?: throw McpTool.McpToolException("id is required")
        if (!logs.delete(id)) throw McpTool.McpToolException("Record $id not found")
        McpJson.obj("ok" to com.google.gson.JsonPrimitive(true))
    }

    private fun recordsDeleteByFilter() = schemaTool(
        name = "records_delete_by_filter",
        description = "Batch delete records matching the filter. Returns deleted count. " +
            "A filter without any constraint is rejected (guard); use records_clear_all to wipe everything.",
        schema = filterSchema(pageable = false),
    ) { args ->
        val filter = parseFilter(args)
        if (!filter.hasConstraint) {
            throw McpTool.McpToolException("Filter must have at least one constraint (type/package/kind/operation/query/time)")
        }
        McpJson.obj("deleted" to com.google.gson.JsonPrimitive(logs.deleteByFilter(filter)))
    }

    private fun recordsClearPackage() = schemaTool(
        name = "records_clear_package",
        description = "Clear all records of one package (AppMonitor clear action).",
        schema = objSchema(required = listOf("packageName")) { str("packageName", "Target package name") },
    ) { args ->
        val pkg = McpJson.str(args, "packageName") ?: throw McpTool.McpToolException("packageName is required")
        logs.clearPackage(pkg)
        McpJson.obj("ok" to com.google.gson.JsonPrimitive(true))
    }

    private fun recordsClearType() = schemaTool(
        name = "records_clear_type",
        description = "Clear all records of one record type across packages (data management).",
        schema = objSchema(required = listOf("recordType")) { str("recordType", "Record type wire name") },
    ) { args ->
        val type = parseRecordType(args) ?: throw McpTool.McpToolException("recordType is required")
        logs.clearByType(type.wire)
        McpJson.obj("ok" to com.google.gson.JsonPrimitive(true))
    }

    private fun recordsClearAll() = schemaTool(
        name = "records_clear_all",
        description = "DANGER: delete ALL records in the database. Requires confirm=true.",
        schema = objSchema(required = listOf("confirm")) {
            bool("confirm", "Must be true to execute this destructive action")
        },
    ) { args ->
        if (McpJson.bool(args, "confirm") != true) {
            throw McpTool.McpToolException("confirm=true is required for destructive clear-all")
        }
        logs.clearAll()
        McpJson.obj("ok" to com.google.gson.JsonPrimitive(true))
    }

    private fun storageUsage() = schemaTool(
        name = "storage_usage",
        description = "Database storage usage in bytes (main db + wal + shm).",
        schema = emptySchema(),
    ) { _ ->
        McpJson.obj("bytes" to com.google.gson.JsonPrimitive(logs.storageUsage()))
    }

    // ---- draft 构造与校验 ----

    /** RecordDraft 全字段名(以空 draft 的 serializeNulls 序列化键集为单一事实源,零双维护)。 */
    private val draftFields: Set<String> by lazy {
        McpJson.gson.toJsonTree(RecordDraft(packageName = "", kind = "", operation = "")).asJsonObject.keySet()
    }

    private fun draftFromArgs(args: JsonObject): RecordDraft {
        val unknown = args.keySet().filterNot { it in draftFields }
        if (unknown.isNotEmpty()) {
            throw McpTool.McpToolException("Unknown draft fields: $unknown")
        }
        val draft = McpJson.gson.fromJson(args, RecordDraft::class.java)
        if (draft.packageName.isBlank() || draft.kind.isBlank() || draft.operation.isBlank()) {
            throw McpTool.McpToolException("packageName, kind and operation must be non-blank")
        }
        return draft
    }

    /** filterSchema:过滤参数全集(pageable 追加分页参数)。 */
    private fun filterSchema(pageable: Boolean): SchemaBuilder.() -> Unit = {
        if (pageable) required(emptyList())
        props()
    }

    /** draftSchema:RecordDraft 全字段(经 draftFields 反射式单一事实源,零手写枚举)。 */
    private fun draftSchema(required: List<String>): SchemaBuilder.() -> Unit = {
        required(required)
        props()
    }

    private fun SchemaBuilder.props() {
        str("recordType", "Record type wire name", RecordType.entries.map { it.wire })
        str("packageName", "Source package name")
        str("kind", "Category id (see taxonomy_listen)")
        str("operation", "Operation id (see taxonomy_listen)")
        str("algorithm", "Algorithm description, e.g. AES/CBC/PKCS5Padding")
        str("processName", "Target process name (multi-process apps)")
        str("threadName", "Calling thread name")
        long("timestamp", "Epoch millis (default now)")
        str("sql", "SQL text (db records)")
        str("dbPath", "Database file path (db records)")
        str("bindArgsJson", "Bind args JSON (db records)")
        str("rowsJson", "Query result rows JSON (db records)")
        int("rowCount", "Result row count (db records)")
        int("affectedCount", "DML affected count (db records)")
        long("rowId", "Inserted rowId (db records)")
        str("resultText", "Scalar query result (db records)")
        str("path", "Subject path (behavior records)")
        str("destPath", "Rename/move destination (behavior records)")
        str("command", "Shell argv JSON array (behavior records)")
        long("fileSize", "File size bytes (behavior records)")
        str("contentHex", "Captured content hex (behavior records)")
        str("contentB64", "Captured content base64 (behavior records)")
        str("contentText", "Captured content text (behavior records)")
        str("prefKey", "Preference key (prefs records)")
        str("url", "Target URL (webview/network records)")
        str("httpMethod", "HTTP method (network records)")
        int("statusCode", "HTTP status code (network records)")
        str("proxyHost", "Proxy host (network PROXY records)")
        int("proxyPort", "Proxy port (network PROXY records)")
        str("dialogTitle", "Dialog title (ui records)")
        str("dialogMessage", "Dialog message (ui records)")
        str("widgetText", "Widget text (ui records)")
        str("activityName", "Activity class name (ui records)")
        str("logcatTag", "Logcat tag (system records)")
        str("logcatLevel", "Logcat level (system records)")
        str("logcatMessage", "Logcat message (system records)")
        str("signatureHash", "Signature SHA-256 (system records)")
        int("exitCode", "System.exit argument (process records)")
        int("processPid", "killProcess pid (process records)")
        str("crashType", "Crash exception class (process records)")
        str("interventionAction", "Intervention marker BLOCKED/DETECTED (feature records)")
        str("keyHex", "Key material hex (crypto records)")
        str("keyB64", "Key material base64 (crypto records)")
        str("keyText", "Key material text (crypto records)")
        int("keyLength", "Key length bits (crypto records)")
        str("ivHex", "IV hex (crypto records)")
        str("inputHex", "Input hex (crypto records)")
        str("outputHex", "Output hex (crypto records)")
        str("saltHex", "Salt hex (crypto records)")
        str("aadHex", "AAD hex (crypto records)")
        str("publicKeyHex", "Public key hex (crypto records)")
        str("paramsJson", "Scalar params JSON (crypto records)")
        bool("verifyResult", "Signature verify result (crypto records)")
        str("query", "SQL-LIKE substring query")
        str("sort", "Sort order: desc (default) or asc")
        long("timeStart", "Timestamp range start (inclusive, epoch millis)")
        long("timeEnd", "Timestamp range end (inclusive, epoch millis)")
        int("page", "Zero-based page number (records_query)")
        int("pageSize", "Page size 1..200, default 50 (records_query)")
    }
}

// ---- schema 构建助手(全部工具共享;放在本文件便于同包复用) ----

/** 工具构建快捷器:schema 由 [buildSchema] 惰性产出。 */
internal fun schemaTool(
    name: String,
    description: String,
    schema: SchemaBuilder.() -> Unit,
    execute: suspend (JsonObject) -> JsonObject,
): McpTool {
    val builder = SchemaBuilder().apply(schema)
    return object : McpTool {
        override val name = name
        override val description = description
        override fun inputSchema(): JsonObject = builder.build()
        override suspend fun execute(args: JsonObject): JsonObject = execute(args)
    }
}

/** JSON Schema DSL builder(函数式属性注册)。 */
internal class SchemaBuilder {
    private val props = LinkedHashMap<String, JsonObject>()
    private val requiredList = mutableListOf<String>()

    fun str(name: String, desc: String, enumValues: List<String>? = null) {
        val p = JsonObject().apply {
            addProperty("type", "string")
            addProperty("description", desc)
            enumValues?.let { add("enum", McpJson.arr(it.map { v -> com.google.gson.JsonPrimitive(v) })) }
        }
        props[name] = p
    }

    fun bool(name: String, desc: String) {
        props[name] = JsonObject().apply {
            addProperty("type", "boolean")
            addProperty("description", desc)
        }
    }

    fun int(name: String, desc: String) {
        props[name] = JsonObject().apply {
            addProperty("type", "integer")
            addProperty("description", desc)
        }
    }

    fun long(name: String, desc: String) {
        int(name, desc)
    }

    fun array(name: String, desc: String, itemType: String = "string") {
        props[name] = JsonObject().apply {
            addProperty("type", "array")
            addProperty("description", desc)
            add("items", JsonObject().apply { addProperty("type", itemType) })
        }
    }

    fun obj(name: String, desc: String) {
        props[name] = JsonObject().apply {
            addProperty("type", "object")
            addProperty("description", desc)
        }
    }

    fun required(names: List<String>) {
        requiredList.addAll(names)
    }

    internal fun build(): JsonObject {
        val schema = JsonObject()
        schema.addProperty("type", "object")
        val properties = JsonObject()
        props.forEach { (k, v) -> properties.add(k, v) }
        schema.add("properties", properties)
        if (requiredList.isNotEmpty()) {
            schema.add("required", McpJson.arr(requiredList.map { com.google.gson.JsonPrimitive(it) }))
        }
        return schema
    }
}

/** DSL 入口:objSchema(required 列表, 属性注册 lambda)。 */
internal fun objSchema(
    required: List<String> = emptyList(),
    props: SchemaBuilder.() -> Unit,
): SchemaBuilder.() -> Unit = {
    props()
    required(required)
}

/** draft 对象属性注册(record_update 用)。 */
internal fun SchemaBuilder.draft(name: String, desc: String) {
    obj(name, desc)
}

internal fun emptySchema(): SchemaBuilder.() -> Unit = {}
