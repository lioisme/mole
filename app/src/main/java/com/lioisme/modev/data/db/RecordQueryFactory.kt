package com.lioisme.modev.data.db

import androidx.sqlite.db.SupportSQLiteQuery
import com.lioisme.modev.domain.record.RecordFilter
import com.lioisme.modev.domain.record.SortOrder

/**
 * 记录查询谓词唯一生成器(V45 T1.1 / ST-1、ST-2,R1「底层数据架构高效」主修)。
 *
 * 旧形态用 Room 编译期 `@Query` + `(:xCount = 0 OR col IN (:xs))` 哨兵表达「不过滤」:
 * 语义正确(删=筛/count=筛同源)但 SQL 层不可优化——SQLite 无法把参数化哨兵下推为
 * 索引约束,列表 / 计数 / 删除 / 详情邻接四类查询在 110 列宽表上恒为全表扫描,
 * 实体声明的 6 个索引(含 1 唯一)形同虚设。
 *
 * 本工厂按「**维度缺省即不出列**」生成最小谓词,使 SQLite 能选索引;
 * `rows` / `count` / `ids` / `neighbor` 四路共用同一 [predicate],继续兑现
 * 「删 = 筛 = 计数」单一事实源(旧 `WHERE_FILTER` 的设计意图不变)。
 *
 * 绑定一律位置参数 `?`([androidx.sqlite.db.SupportSQLiteQuery] 位置绑定不解析 `:name`,AGENTS 已知坑);
 * 关键字转义集中在 [likePattern],列集与原 24 列逐字一致(行为等价由
 * `RecordQueryFactoryTest` 逐维度对账锁定)。
 */
object RecordQueryFactory {

    /** 观察表名(`@RawQuery.observedTables` 与语句生成同源)。 */
    const val TABLE = CryptoRecordEntity.TABLE_NAME

    /** 列表/切片投影列中 `:previewChars` 出现次数(投影参数与绑定数零漂移)。 */
    private val PREVIEW_ARG_COUNT: Int =
        Regex(":previewChars").findAll(CryptoRecordDao.ROW_SELECT).count()

    /** 关键字检索列集(= 旧 WHERE_FILTER 的 24 列,顺序一致)。 */
    private val SEARCH_COLUMNS = listOf(
        "algorithm", "operation", "keyText", "keyHex", "inputText", "inputHex",
        "outputText", "outputHex", "threadName", "sql", "resultText", "path",
        "destPath", "command", "prefKey", "dialogTitle", "dialogMessage",
        "widgetText", "activityName", "logcatTag", "logcatMessage",
        "signatureHash", "crashType", "crashMessage",
    )

    /** 恒假谓词:显式空交集(规范化后 kinds/operations 为空)→ 零行、零删除。 */
    private const val NEVER = "0"

    /**
     * 生成结果 = SQL 文本 + 位置绑定参数,本身即 [SupportSQLiteQuery]
     * (直接交给 `@RawQuery` 执行位;`sql` / [args] 公开供真机 `EXPLAIN QUERY PLAN`
     * 门禁与等价性测试逐项对账,无需反射取绑定)。
     */
    class Built(override val sql: String, val args: List<Any?>) : SupportSQLiteQuery {
        override val argCount: Int get() = args.size

        override fun bindTo(program: androidx.sqlite.db.SupportSQLiteProgram) {
            args.forEachIndexed { index, value ->
                val slot = index + 1
                when (value) {
                    null -> program.bindNull(slot)
                    is Long -> program.bindLong(slot, value)
                    is Int -> program.bindLong(slot, value.toLong())
                    is Boolean -> program.bindLong(slot, if (value) 1L else 0L)
                    is Double -> program.bindDouble(slot, value)
                    is ByteArray -> program.bindBlob(slot, value)
                    else -> program.bindString(slot, value.toString())
                }
            }
        }
    }

    /** 谓词片段(不含前导 `WHERE`)+ 绑定参数 + 空交集标记。 */
    data class Predicate(val sql: String, val args: List<Any?>, val noMatch: Boolean) {
        fun whereClause(): String = if (sql.isEmpty()) "" else " WHERE $sql"
    }

    /** 生成最小谓词;[filter] 已含 sort 之外的全部维度。 */
    fun predicate(filter: RecordFilter): Predicate {
        val norm = filter.normalize()
        val kindsEmpty = filter.kinds != null && norm.kinds.isNullOrEmpty()
        val opsEmpty = filter.operations != null && norm.operations.isNullOrEmpty()
        if (kindsEmpty || opsEmpty) return Predicate(NEVER, emptyList(), noMatch = true)

        val clauses = ArrayList<String>(6)
        val args = ArrayList<Any?>()
        norm.recordTypes?.takeIf { it.isNotEmpty() }?.let { clauses += inClause("recordType", it, args) }
        norm.packages?.takeIf { it.isNotEmpty() }?.let { clauses += inClause("packageName", it, args) }
        norm.kinds?.takeIf { it.isNotEmpty() }?.let { clauses += inClause("kind", it, args) }
        norm.operations?.takeIf { it.isNotEmpty() }?.let { clauses += inClause("operation", it, args) }
        norm.timeRange?.let { range ->
            clauses += "timestamp >= ?"
            args += range.first
            clauses += "timestamp <= ?"
            args += range.last
        }
        norm.query?.takeIf { it.isNotBlank() }?.let { raw ->
            val pattern = likePattern(raw)
            val ors = SEARCH_COLUMNS.joinToString(" OR ") { "$it LIKE ? ESCAPE '\\'" }
            clauses += "($ors)"
            repeat(SEARCH_COLUMNS.size) { args += pattern }
        }
        return Predicate(clauses.joinToString(" AND "), args, noMatch = false)
    }

    /** LIKE 模式与转义(`%`/`_`/`\` 三字符;大小写由 SQLite LIKE 的 ASCII 不敏感语义承担)。 */
    fun likePattern(raw: String): String =
        "%" + raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"

    /** 记录行分页/切片查询(仅展示列 + SQL 侧预览裁剪,零大字段物化)。 */
    fun rows(
        filter: RecordFilter,
        limit: Int,
        offset: Int,
        previewChars: Int,
    ): Built {
        val p = predicate(filter)
        val sql = buildString {
            append("SELECT ")
            append(CryptoRecordDao.ROW_SELECT.replace(":previewChars", "?"))
            append(" FROM ").append(TABLE)
            append(p.whereClause())
            append(" ORDER BY timestamp ").append(direction(filter.sort))
            append(", id ").append(direction(filter.sort))
            append(" LIMIT ? OFFSET ?")
        }
        val args = ArrayList<Any?>(p.args.size + PREVIEW_ARG_COUNT + 2)
        repeat(PREVIEW_ARG_COUNT) { args.add(previewChars) }
        args.addAll(p.args)
        args.add(limit)
        args.add(offset)
        return Built(sql, args)
    }

    /** 匹配总数(与 [rows] 同谓词)。 */
    fun count(filter: RecordFilter): Built {
        val p = predicate(filter)
        return Built("SELECT COUNT(*) FROM $TABLE${p.whereClause()}", p.args)
    }

    /**
     * 按记录类型分组计数(V45 ST-6:与 [count] 同谓词,一次 `GROUP BY` 替代
     * 逐类型 N 次 COUNT —— 监控页 10 个徽标与 MCP `app_monitor` 的旧形态各发
     * 10 条独立查询并由 Room 各挂一份失效监听,表一动即 10 路重查)。
     */
    fun typeCounts(filter: RecordFilter): Built {
        val p = predicate(filter)
        return Built(
            "SELECT recordType, COUNT(*) AS count FROM $TABLE${p.whereClause()} GROUP BY recordType",
            p.args,
        )
    }

    /**
     * 匹配 id 集(删除前置:「删 = 筛」与 [count] 同谓词,`@RawQuery` 不做 DML,
     * 仓库层据本结果按主键分片删除)。命中集上限 = 库容量 `MAX_RECORDS`,恒有界。
     */
    fun ids(filter: RecordFilter): Built {
        val p = predicate(filter)
        return Built("SELECT id FROM $TABLE${p.whereClause()} ORDER BY id", p.args)
    }

    /**
     * 详情邻接 id(keyset 翻页,V45 ST-2):谓词 + `(timestamp, id)` 严格比较,
     * 排序方向与比较方向一致,配合 `index_crypto_records_timestamp` 走索引而非全表排序。
     */
    fun neighborId(filter: RecordFilter, ts: Long, id: Long, newer: Boolean): Built {
        val p = predicate(filter)
        val cmp = if (newer) ">" else "<"
        val tie = if (newer) "OR (timestamp = ? AND id > ?)" else "OR (timestamp = ? AND id < ?)"
        val order = if (newer) "ASC" else "DESC"
        val sql = buildString {
            append("SELECT id FROM ").append(TABLE)
            append(p.whereClause())
            if (p.sql.isEmpty()) append(" WHERE ") else append(" AND ")
            append("(timestamp $cmp ? $tie)")
            append(" ORDER BY timestamp ").append(order).append(", id ").append(order)
            append(" LIMIT 1")
        }
        return Built(sql, p.args + listOf(ts, ts, id))
    }

    private fun direction(sort: SortOrder): String = if (sort == SortOrder.ASC) "ASC" else "DESC"

    private fun inClause(column: String, values: Collection<String>, args: MutableList<Any?>): String {
        args.addAll(values)
        return "$column IN (" + values.joinToString(",") { "?" } + ")"
    }
}
