package com.lioisme.modev.hook

import com.lioisme.modev.core.model.SqlBindValue
import com.lioisme.modev.core.model.SqlResultRow
import java.util.Collections
import java.util.HashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 数据库监听上下文跟踪器(T5,ADR-5/6/7):
 * 以对象实例为键关联数据库操作上下文,把 open → query/exec → close 串成同一
 * contextId 链路;游标/语句实例与所属 DB 实例关联。
 *
 * 五个弱引用映射(V44 T2.5 改条带化 [StripedWeakMap]):旧形态五张
 * `synchronizedMap(WeakHashMap)` 各为全局单锁,全 SQL 事件串行化;条带化后
 * 锁竞争降为 1/16,弱引用语义与防泄漏特征不变:
 * - DB 实例 → [SqlContext](path/name、事务深度与成功标记、journalMode);
 * - 游标实例 → [CursorBuffer](结果集被动采集缓冲 + 双闸门);
 * - 语句实例 → 绑定暂存表(实例键控,允许跨线程 bind → execute,ADR-7);
 * - 语句实例 → 所属 DB 实例(记录关联 DB contextId 与库名,ADR-7);
 * - 语句实例 → SQL 文本。
 */
object SqlContextTracker {

    private class Entry(val id: Long, val context: SqlContext)

    private val dbEntries = StripedWeakMap<Entry>()
    private val cursorBuffers = StripedWeakMap<CursorBuffer>()
    private val statementBinds = StripedWeakMap<MutableMap<Int, SqlBindValue>>()
    private val statementDb = StripedWeakMap<Any>()
    private val statementSql = StripedWeakMap<String>()
    private val idCounter = AtomicLong(1)

    /** DB 实例 → 上下文(自动注册并分配存活期唯一 ID)。 */
    fun contextFor(instance: Any): SqlContext =
        dbEntries.getOrPut(instance) { Entry(idCounter.getAndIncrement(), SqlContext()) }.context

    /** DB 实例存活期唯一标识,用于跨记录关联(open/query/close 同链)。 */
    fun identity(instance: Any): Long =
        dbEntries.getOrPut(instance) { Entry(idCounter.getAndIncrement(), SqlContext()) }.id

    fun removeDb(instance: Any) {
        dbEntries.remove(instance)
    }

    /** 游标实例 → 采集缓冲(惰性创建;随游标 GC 自动回收)。 */
    fun cursorBuffer(cursor: Any): CursorBuffer =
        cursorBuffers.getOrPut(cursor) { CursorBuffer() }

    fun removeCursor(cursor: Any) {
        cursorBuffers.remove(cursor)
    }

    /** 语句实例 → 绑定暂存表(同步 Map;随语句 GC 自动回收)。 */
    fun bindsOf(statement: Any): MutableMap<Int, SqlBindValue> =
        statementBinds.getOrPut(statement) { Collections.synchronizedMap(HashMap()) }

    /** clearBindings:清空暂存(语句已执行完亦可移除,防长期驻留)。 */
    fun clearBinds(statement: Any) {
        statementBinds.remove(statement)
    }

    /** compileStatement 时建立语句 → 所属 DB 实例关联。 */
    fun linkStatement(statement: Any, db: Any) {
        statementDb[statement] = db
    }

    fun dbOf(statement: Any): Any? = statementDb[statement]

    /** compileStatement 时登记语句 SQL(执行记录承载)。 */
    fun linkSql(statement: Any, sql: String) {
        statementSql[statement] = sql
    }

    fun sqlOf(statement: Any): String? = statementSql[statement]
}

/**
 * 单个 DB 实例的累计上下文(存活期 = 实例生命周期,弱引用无泄漏)。
 */
class SqlContext {
    /** 数据库完整路径(打开事件时写入)。 */
    var dbPath: String? = null

    /** 数据库名(路径 basename)。 */
    var databaseName: String? = null

    /** 事务嵌套深度(begin/end 增减,ADR-5)。 */
    var transactionDepth: Int = 0

    /** 当前事务是否已标记成功(任意 setTransactionSuccessful 后置位)。 */
    var transactionSuccess: Boolean = false

    /** 最近一次 setJournalMode/enableWriteAheadLogging 结果(入 params)。 */
    var journalMode: String? = null
}

/**
 * 单个游标实例的结果集被动采集缓冲(ADR-6):
 * 随 App 自身迭代逐行收集,行数/字符双闸门任一超限即停止(置 [truncated]),
 * 泄漏游标最坏代价 = 上限字节,随弱引用 GC。
 */
class CursorBuffer {

    /** 所属 DB 实例(QUERY_RESULT 的 contextId 与库名解析)。 */
    var db: Any? = null

    /** 原始查询 SQL(QUERY_RESULT 回执承载)。 */
    var sql: String? = null

    /** 查询上下文标量(表名/投影等,随回执)。 */
    var params: Map<String, String>? = null

    /** 列名(第一次行采集时解析;游标未物化时为 null,单元格列名退化为序号)。 */
    var columnNames: Array<String>? = null

    /** 已采集行(按 App 迭代顺序)。 */
    val rows: MutableList<SqlResultRow> = mutableListOf()

    /** 行数(反射 mCount 成功时;未物化游标为 null)。 */
    var rowCount: Int? = null

    /** 双闸门截断标记(超限补发后置位,停止采集)。 */
    var truncated: Boolean = false

    /** 采集期字符预算(注册游标时按开关快照写入,期间不变)。 */
    var maxChars: Int = 0

    /** 采集期行数上限(同上)。 */
    var maxRows: Int = 0

    private var chars: Long = 0L

    /** 当前是否仍在采集(行数/字符任一超限 → false)。 */
    val collecting: Boolean
        get() = !truncated && rows.size < maxRows && chars < maxChars

    /** moveToPosition(-1)/requery:重置采集态(防重复行,ADR-6);注册信息(sql/db/上限)保留。 */
    fun reset() {
        rows.clear()
        rowCount = null
        columnNames = null
        truncated = false
        chars = 0L
    }

    /** 追加一行;返回是否仍在采集(调用方可据此立即补发截断回执)。 */
    fun addRow(row: SqlResultRow): Boolean {
        rows.add(row)
        var cost = 0L
        for (cell in row.c) {
            cost += (cell.v?.length ?: 0) + (cell.hex?.length ?: 0) + 8
        }
        chars += cost
        if (rows.size >= maxRows || chars >= maxChars) {
            truncated = true
        }
        return collecting
    }
}