package com.lioisme.modev.core.model

/**
 * 一条完整的数据库操作记录(不可变,ADR-2 数据库监听 T1)。
 *
 * 与 [CryptoRecord] 并列实现 [MonitoredRecord]:注入侧出口/宿主侧分发共用;
 * 宿主侧单表存储:复用 `crypto_records` 既有列(algorithm→databaseName、paramsJson、
 * stackJson、contextId 等),数据库专属字段落新增可空列(ADR-1,迁移 v5→v6)。
 *
 * @param id 宿主侧数据库主键;0 表示尚未持久化
 * @param seq 注入侧进程级单调序号(协议可靠性:ACK 与幂等去重依据;0 = 无)
 * @param recordType 记录类型(恒 [RecordTypes.DATABASE])
 * @param packageName 目标包名
 * @param kind 数据库操作类别
 * @param operation 操作阶段
 * @param databaseName 数据库名(路径 basename,如 app.db);语句类记录取所属 DB 实例名
 * @param provider 调用门面:SQLiteDatabase / SQLiteOpenHelper / SQLiteStatement
 * @param dbPath 数据库完整路径(打开/关联事件时写入)
 * @param sql SQL 语句原文(上限 [RecordLimits.SQL_MAX_CHARS],超限置 params["sqlTruncated"])
 * @param bindArgs 绑定参数序列表(ContentValues 同构序列化;blob 走双态视图)
 * @param rows 结果行(被动采集,上限 [HookSwitch.maxResultRows]/[maxResultChars],超限截断)
 * @param rowCount 查询结果行数(被动获取:游标未物化时为 null)
 * @param affectedCount DML 受影响行数(update/delete/executeUpdateDelete 返回)
 * @param rowId 插入 rowId(insert/replace/executeInsert 返回)
 * @param resultText 标量查询结果(simpleQueryForLong/String)
 * @param params 标量参数承载(表名/冲突算法/事务深度/成功标记/journalMode/截断标记等)
 * @param processName 目标进程名(多进程 App 区分主/子进程)
 * @param contextId 关联同一数据库实例多次调用的对象标识(实例存活期内唯一)
 * @param threadName 调用线程
 * @param stack 过滤后的应用调用栈(最内层在前)
 * @param timestamp 毫秒时间戳
 */
data class SqlRecord(
    val id: Long = 0L,
    override val seq: Long = 0L,
    override val recordType: String = RecordTypes.DATABASE,
    val packageName: String,
    val kind: SqlKind,
    val operation: SqlOperation,
    val databaseName: String,
    val provider: String? = null,
    val dbPath: String? = null,
    val sql: String? = null,
    val bindArgs: List<SqlBindValue>? = null,
    val rows: List<SqlResultRow>? = null,
    val rowCount: Int? = null,
    val affectedCount: Int? = null,
    val rowId: Long? = null,
    val resultText: String? = null,
    val params: Map<String, String>? = null,
    val processName: String? = null,
    val contextId: Long = 0L,
    val threadName: String,
    val stack: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
) : MonitoredRecord {

    override fun withSeq(seq: Long): SqlRecord = copy(seq = seq)
}

/**
 * 绑定参数/结果单元格值的类型标签(与 SQLite 存储类对齐)。
 */
enum class SqlValueType {
    /** 整数(INTEGER/Long) */
    L,

    /** 浮点(REAL/Double) */
    D,

    /** 文本(TEXT/String) */
    S,

    /** 二进制(BLOB/byte[],hex 双态视图承载) */
    B,

    /** 空值(NULL) */
    N,
}

/**
 * 一个绑定参数(bind* 调用值;参数化语句的 `?` 占位,1-based 序号)。
 */
data class SqlBindValue(
    /** 1-based 参数序号 */
    val i: Int,
    /** 值类型 */
    val t: SqlValueType,
    /** L/D/S 标量值;N 恒 null */
    val v: String? = null,
    /** B 的 hex 前缀(超限截断,truncated 标记) */
    val hex: String? = null,
    /** B 的总字节长 */
    val len: Int? = null,
    val truncated: Boolean = false,
)

/** 结果集一行(游标当前位置按列名 + 类型标签序列化)。 */
data class SqlResultRow(
    /** 单元格序列(列序 = 游标列序) */
    val c: List<SqlResultCell>,
)

/** 结果集单元格(列名 + 类型标签 + 值;blob 走 hex 双态视图)。 */
data class SqlResultCell(
    /** 列名 */
    val n: String,
    /** 值类型 */
    val t: SqlValueType,
    /** L/D/S 标量值;N 恒 null */
    val v: String? = null,
    /** B 的 hex 前缀(超限截断,truncated 标记) */
    val hex: String? = null,
    /** B 的总字节长 */
    val len: Int? = null,
    val truncated: Boolean = false,
)