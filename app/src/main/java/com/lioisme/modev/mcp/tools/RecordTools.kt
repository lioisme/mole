package com.lioisme.modev.mcp.tools

import com.google.gson.JsonObject
import com.lioisme.modev.core.model.CategoryTaxonomy
import com.lioisme.modev.core.model.MonitoredRecord
import com.lioisme.modev.core.model.RecordType
import com.lioisme.modev.domain.record.RecordFilter
import com.lioisme.modev.domain.record.RecordService
import com.lioisme.modev.domain.record.SortOrder
import com.lioisme.modev.domain.record.hasConstraint
import kotlinx.coroutines.flow.first

/**
 * 记录组工具(V38,14 个):查询/统计/详情/邻接/写路径/清理/存储。
 * 全部仅经 [RecordService] 上游(与 UI ViewModels 同源,R4/D5)。
 * 写入面载荷 = 类型化记录族 [MonitoredRecord]:与 `record_get` 输出的
 * `record` 对象同形,故「读 → 改 → 写回」闭环成立(旧平铺列名形态与读侧不同形,
 * 往返必失真)。
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
        sort = if (McpJson.str(args, "sort") == SORT_ASC) SortOrder.ASC else SortOrder.DESC,
    )

    // ---- 工具 ----

    private fun recordsQuery() = schemaTool(
        name = "records_query",
        description = "Query captured records with filters (paged). Returns items, total, page, pageSize. " +
            "recordType is one of: ${RecordType.entries.joinToString(", ") { it.wire }}. " +
            "kind is a category id within that type (see taxonomy_listen). query does SQL-LIKE substring match.",
        schema = filterSchema(pageable = true),
    ) { args ->
        val page = (McpJson.int(args, "page") ?: 0).coerceAtLeast(0)
        // V44 Q4:默认页大小双端单源 = RecordService.DEFAULT_PAGE_SIZE(旧 50 账面漂移已归一)
        val pageSize = (McpJson.int(args, "pageSize") ?: RecordService.DEFAULT_PAGE_SIZE).coerceIn(1, 200)
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
        description = "Full record detail by id (all fields incl. content; strings over " +
            "${McpDtos.MAX_FIELD_CHARS} chars are truncated and the response carries truncated=true). " +
            "Use records_query first to discover ids.",
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
        description = "Create (import) a record. Payload keys are the typed record fields " +
            "(identical shape to record_get's `record` object); keys outside the chosen recordType are " +
            "rejected. Omitted optional keys take the model defaults (timestamp = now). " +
            "kind/operation must be registered ids (see taxonomy_listen / taxonomy_feature).",
        schema = recordSchema(required = WRITE_REQUIRED_COMMON),
    ) { args ->
        val record = recordFromArgs(args)
        McpJson.obj("id" to logs.create(record))
    }

    private fun recordUpdate() = schemaTool(
        name = "record_update",
        description = "Full replacement update by id (upsert semantics). Fetch with record_get first and " +
            "send the complete modified record object (its `record` payload); missing optional fields " +
            "fall back to defaults. The top-level id argument wins over any id inside the payload.",
        schema = objSchema(required = listOf("id")) {
            long("id", "Record id to overwrite")
            recordObject("record", "Full typed record (same shape as record_create / record_get record)")
        },
    ) { args ->
        val id = McpJson.long(args, "id") ?: throw McpTool.McpToolException("id is required")
        val payload = args.getAsJsonObject("record") ?: throw McpTool.McpToolException("record object is required")
        if (!logs.update(id, recordFromArgs(payload))) {
            throw McpTool.McpToolException("Record $id not found")
        }
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
        description = "DANGER: clear all records of one package (AppMonitor clear action). Requires confirm=true.",
        schema = objSchema(required = listOf("packageName", "confirm")) {
            str("packageName", "Target package name")
            bool("confirm", "Must be true to execute this destructive action")
        },
    ) { args ->
        val pkg = McpJson.str(args, "packageName") ?: throw McpTool.McpToolException("packageName is required")
        // V44 Q4 门槛对称:UI「清空本类」恒有确认框,MCP 破坏性清理同需 confirm
        if (McpJson.bool(args, "confirm") != true) {
            throw McpTool.McpToolException("confirm=true is required for destructive clear")
        }
        logs.clearPackage(pkg)
        McpJson.obj("ok" to com.google.gson.JsonPrimitive(true))
    }

    private fun recordsClearType() = schemaTool(
        name = "records_clear_type",
        description = "DANGER: clear all records of one record type across packages (data management). " +
            "Requires confirm=true.",
        schema = objSchema(required = listOf("recordType", "confirm")) {
            str("recordType", "Record type wire name")
            bool("confirm", "Must be true to execute this destructive action")
        },
    ) { args ->
        val type = parseRecordType(args) ?: throw McpTool.McpToolException("recordType is required")
        if (McpJson.bool(args, "confirm") != true) {
            throw McpTool.McpToolException("confirm=true is required for destructive clear")
        }
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

    // ---- 类型化写入载荷解析与校验(V45 T2.5 / ISO-1;T3.2 起 schema 由模型派生) ----

    /**
     * 该记录类型的合法载荷键集(以空对象过一遍 Gson 的字段集为单一事实源,零手写清单;
     * 与 [RecordPayloadSchema.keysOf] 由 [McpToolParityTest] 做镜像对账)。
     */
    private fun payloadFields(type: RecordType): Set<String> {
        val clazz = RecordPayloadSchema.modelClassOf(type)
        val empty = McpJson.gson.fromJson<MonitoredRecord>("{}", clazz)
            ?: throw McpTool.McpToolException("Cannot probe payload schema for ${type.wire}")
        return McpJson.gson.toJsonTree(empty).asJsonObject.keySet() + "recordType"
    }

    /**
     * 参数 → 类型化记录。三道闸:
     * ① 未知键拒(该类型独有字段出现在他型载荷里即错,不再「整表一份平铺列」全收);
     * ② kind/operation 原始串先验(Gson 对非法枚举名静默置 null,会绕过 Kotlin 非空约束);
     * ③ taxonomy 登记校验(V44 D7 写侧第一性,禁幽灵记录入库后被读侧永久隐藏)。
     */
    private fun recordFromArgs(args: JsonObject): MonitoredRecord {
        val wire = McpJson.str(args, "recordType")
        val type = RecordType.fromWire(wire ?: "") ?: throw McpTool.McpToolException(
            "Unknown recordType '${wire ?: "missing"}'; valid: ${RecordType.entries.joinToString(",") { it.wire }}",
        )
        val unknown = args.keySet().filterNot { it in payloadFields(type) }
        if (unknown.isNotEmpty()) {
            throw McpTool.McpToolException("Unknown fields for recordType '${type.wire}': $unknown")
        }
        // 上方缺值检查已保证非空;此处 orEmpty 只为把可空形态收敛到类型化路径
        val kind = McpJson.str(args, "kind").orEmpty()
        val operation = McpJson.str(args, "operation").orEmpty()
        // V45 T3.2:类型面的「无默认值非空字段」必须显式给值。Kotlin 默认值优先实例化
        // (见 McpJson)已能吃掉可选字段,剩下这几个是**存储 NOT NULL 列**的载体:
        // 缺席时与其让 Room 抛 SQLITE_CONSTRAINT,不如在此点名缺哪个键。
        val requiredKeys = WRITE_REQUIRED_COMMON + WRITE_REQUIRED_BY_TYPE[type].orEmpty()
        val missing = requiredKeys.filter {
            args.get(it)?.takeIf { e -> e.isJsonPrimitive }?.asString.isNullOrBlank()
        }
        if (missing.isNotEmpty()) {
            throw McpTool.McpToolException(
                "recordType '${type.wire}' requires non-blank: ${missing.sorted().joinToString(",")}",
            )
        }
        if (CategoryTaxonomy.byId(type, kind) == null) {
            throw McpTool.McpToolException(
                "Unknown kind '$kind' for recordType '${type.wire}' " +
                    "(see taxonomy_listen / taxonomy_feature)",
            )
        }
        if (CategoryTaxonomy.validateOperations(type.wire, setOf(operation))
                ?.contains(operation) != true
        ) {
            throw McpTool.McpToolException(
                "Unknown operation '$operation' for recordType '${type.wire}'",
            )
        }
        return McpJson.gson.fromJson(args, RecordPayloadSchema.modelClassOf(type))
            ?: throw McpTool.McpToolException("Malformed ${type.wire} record payload")
    }

    /**
     * records_query / records_count / records_delete_by_filter 的过滤参数面
     * (V45 T3.2:分页参数**只**随 records_query 出现 —— 旧形态三张表共用同一份
     * 60 键平铺清单,于是 `records_count` 广告 page/pageSize 却不生效、
     * `record_create` 广告 query/sort 却把它们当未知字段拒绝,声明面与生效面分叉)。
     */
    private fun filterSchema(pageable: Boolean): SchemaBuilder.() -> Unit = {
        filterProperties()
        if (pageable) {
            int("page", "Zero-based page number")
            int("pageSize", "Page size 1..200, default ${RecordService.DEFAULT_PAGE_SIZE}")
        }
        required(emptyList())
    }

    /** recordSchema:写入载荷面(键/类型由模型反射派生,见 [RecordPayloadSchema])。 */
    private fun recordSchema(required: List<String>): SchemaBuilder.() -> Unit = {
        payloadProperties()
        required(required)
    }

}

// ---- schema 构建助手(全部工具共享;放在本文件便于同包复用) ----

/** 工具构建快捷器:schema 由构造期传入的 schema 构建器即时产出。 */
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

    /** 元素类型混合的数组(值类型由 handler 按上下文裁决,如黑名单 fridaPorts=整数/其余=字符串)。 */
    fun arrayRaw(name: String, desc: String) {
        props[name] = JsonObject().apply {
            addProperty("type", "array")
            addProperty("description", desc)
        }
    }

    fun obj(name: String, desc: String) {
        props[name] = JsonObject().apply {
            addProperty("type", "object")
            addProperty("description", desc)
        }
    }

    /** 带嵌套属性表的对象属性(record_update 的 `record` = 派生载荷面)。 */
    fun obj(name: String, desc: String, properties: JsonObject) {
        props[name] = JsonObject().apply {
            addProperty("type", "object")
            addProperty("description", desc)
            add("properties", properties)
        }
    }

    /**
     * 登记派生片段([RecordPayloadSchema] 产出的属性),并补描述。
     * 描述是纯注释面:键集真值恒由模型派生,说明表不得扩/缩键集(镜像对账测试锁定)。
     */
    fun addRaw(name: String, fragment: JsonObject, desc: String?) {
        if (desc != null) fragment.addProperty("description", desc)
        if (!fragment.has("description")) {
            fragment.addProperty("description", "Typed payload field (see record_get output for the shape)")
        }
        props[name] = fragment
    }

    /** 已登记属性的原始对象(供嵌套进对象 schema)。 */
    fun rawProperties(): JsonObject {
        val out = JsonObject()
        props.forEach { (k, v) -> out.add(k, v) }
        return out
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
        // V45 T3.2:未声明参数一律由注册表硬拒(-32602)。「广告了但不生效」与
        // 「生效了但没广告」两种漂移自此在同一条款上被消除:参数面 = 属性表。
        schema.addProperty("additionalProperties", false)
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

/** 类型化记录对象属性注册(record_update 用;V45 T2.5 起替代旧 `draft` 平铺形态)。 */
internal fun SchemaBuilder.recordObject(name: String, desc: String) {
    // V45 T3.2:`record` 不再是无结构的 `type: object`——嵌套属性表与 record_create
    // 同源派生,Agent 从 tools/list 就能写出合法载荷(键集仍由 handler 按 recordType 收窄)。
    val inner = SchemaBuilder().apply { payloadProperties() }
    obj(name, "$desc Unknown keys for the chosen recordType are rejected.", inner.rawProperties())
}

internal fun emptySchema(): SchemaBuilder.() -> Unit = {}

/**
 * 过滤参数面:与 [RecordTools] 的 `parseFilter` 逐参数镜像——
 * 这里声明的每一项都真被读,`parseFilter` 读的每一项都在这里声明
 * (注册表 `additionalProperties=false` ⇒ 未声明键 = -32602,不存在「静默忽略」)。
 */
internal fun SchemaBuilder.filterProperties() {
    str("recordType", "Record type wire name; omit = all types", RecordType.entries.map { it.wire })
    str("packageName", "Filter by source package name")
    str("kind", "Filter by category id (see taxonomy_listen / taxonomy_feature)")
    str("operation", "Filter by operation id (see taxonomy_listen / taxonomy_feature)")
    str("query", "SQL-LIKE substring query over captured text columns")
    str("sort", "Timestamp sort order (default desc)", listOf(SORT_DESC, SORT_ASC))
    long("timeStart", "Timestamp range start (inclusive, epoch millis)")
    long("timeEnd", "Timestamp range end (inclusive, epoch millis)")
}

/**
 * 写入载荷面:键集与类型全部由 [RecordPayloadSchema] 从模型族派生,
 * 注释性说明走 [FIELD_NOTES](说明**不能**扩键集——镜像对账测试会红)。
 */
internal fun SchemaBuilder.payloadProperties() {
    RecordPayloadSchema.mergedProperties().entrySet().forEach { (name, value) ->
        addRaw(name, (value as? JsonObject) ?: JsonObject(), FIELD_NOTES[name])
    }
}

/** sort 参数取值(schema enum 与 parseFilter 常量同源;V45 T3.2 大小写敏感)。 */
internal const val SORT_DESC = "desc"
internal const val SORT_ASC = "asc"

/**
 * 写入面**任何**记录类型都必须给的键。
 * schema `required` 与 handler 缺值检查共用本表 —— 声明面要求什么,生效面就要求什么。
 */
internal val WRITE_REQUIRED_COMMON = listOf("recordType", "packageName", "kind", "operation", "threadName")

/**
 * 分型追加项:模型里**无默认值的非空字段**(= 存储 NOT NULL 列的载体)。
 * 派生 schema 能给出键集与类型,但 Kotlin 可空性不在 JVM 反射面里(@NotNull 是 BINARY 保留),
 * 故此处显式登记;`McpToolsTest.record_write_matrix_covers_every_type` 逐类型真写一遍,
 * 少登记一项即红(Room NOT NULL 约束抛错)。
 */
internal val WRITE_REQUIRED_BY_TYPE: Map<RecordType, List<String>> = mapOf(
    RecordType.CRYPTO to listOf("algorithm"),
    RecordType.DATABASE to listOf("databaseName"),
    RecordType.WEBVIEW to listOf("processName"),
)

/**
 * 载荷键的补充说明:只解释语义,**不**承载键集真值(键集派生自模型族)。
 * `McpToolsTest.field_notes_never_advertise_unknown_keys` 断言本表每个键都存在于
 * 派生键集 —— 模型改名而说明未同步即红(旧手抄表的「contentHex/prefKey 之类的虚构键」
 * 由此不可能再现)。
 */
internal val FIELD_NOTES: Map<String, String> = mapOf(
    "recordType" to "Record type discriminator (required) - selects the payload shape",
    "packageName" to "Source package name (required)",
    "kind" to "Category id within recordType (required; see taxonomy_listen / taxonomy_feature)",
    "operation" to "Operation id within recordType (required; see taxonomy_listen / taxonomy_feature)",
    "id" to "Row id - ignored on create, preserved on update round-trip",
    "seq" to "Inject-side process sequence number - storage managed, ignored on write",
    "algorithm" to "Cipher transformation, e.g. AES/CBC/PKCS5Padding",
    "provider" to "Security provider name",
    "keyAlias" to "KeyStore alias (crypto records)",
    "databaseName" to "Database name (db records)",
    "dbPath" to "Database file path (db records)",
    "sql" to "SQL text (db records)",
    "bindArgs" to "Bound statement arguments (db records)",
    "rows" to "Captured result rows, collected passively after the app iterated (db records)",
    "rowCount" to "Result row count (db records)",
    "affectedCount" to "DML affected count (db records)",
    "rowId" to "Inserted rowId (db records)",
    "resultText" to "Scalar query result (db records)",
    "path" to "Subject path (behavior) or SharedPreferences file path (prefs/webview)",
    "destPath" to "Rename/move destination (behavior records)",
    "command" to "Shell argv (behavior records)",
    "fileSize" to "File size in bytes (behavior records)",
    "content" to "Captured content, multi-representation object {hex,base64,text,totalLength,truncated}",
    "key" to "Crypto key material as BytesView; preference key as plain string (prefs records)",
    "value" to "Preference value as BytesView (prefs records)",
    "url" to "Target URL (webview / network records)",
    "method" to "HTTP method (network records)",
    "statusCode" to "HTTP status code (network records)",
    "requestHeaders" to "Request header map (network records)",
    "responseHeaders" to "Response header map (network records)",
    "requestBody" to "Request body as BytesView (network records)",
    "responseBody" to "Response body as BytesView (network records)",
    "stack" to "Captured call stack frames",
    "params" to "Scalar parameter map (kind-specific extras)",
    "timestamp" to "Epoch millis (UTC wall clock at capture time; defaults to now on create)",
    "threadName" to "Calling thread name",
    "processName" to "Target process name (multi-process apps)",
    "logcatTag" to "Logcat tag (system records)",
    "logcatLevel" to "Logcat level letter (system records)",
    "logcatMessage" to "Logcat message text (system records)",
    "interventionAction" to "Intervention marker: BLOCKED / DETECTED",
)
