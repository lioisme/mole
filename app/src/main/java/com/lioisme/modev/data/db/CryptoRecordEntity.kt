package com.lioisme.modev.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.lioisme.modev.core.model.RecordTypes

/**
 * 算法记录实体。BytesView 三态视图(V19:hex/Base64/文本)展开为独立列,
 * 枚举以名称字符串存储,堆栈以 JSON 存储。
 */
@Entity(
    tableName = "crypto_records",
    indices = [
        Index(value = ["packageName", "timestamp"]),
        Index(value = ["packageName", "kind"]),
        // v8:类型×类别 / 类型×包×时间 复合索引(多维筛选精准 + 高效,见 MIGRATION_7_8)
        Index(value = ["recordType", "kind"]),
        Index(value = ["recordType", "packageName", "timestamp"]),
        // v5:timestamp 单列索引(全局/裁剪/邻接查询的时间倒序热路径,含 M13 修复)
        Index(value = ["timestamp"]),
        // v3:注入侧进程级单调序号,唯一约束 = 断线重放幂等(REPLACE 吸收重复)
        Index(value = ["packageName", "processName", "seq"], unique = true),
    ],
)
data class CryptoRecordEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id") val id: Long = 0L,

    /** 记录类型(V10,默认 crypto;未来多能力记录按此分流)。 */
    @ColumnInfo(name = "recordType") val recordType: String = RecordTypes.CRYPTO,
    @ColumnInfo(name = "packageName") val packageName: String,
    @ColumnInfo(name = "kind") val kind: String,
    @ColumnInfo(name = "operation") val operation: String,
    @ColumnInfo(name = "algorithm") val algorithm: String,

    @ColumnInfo(name = "provider") val provider: String? = null,
    @ColumnInfo(name = "direction") val direction: Int? = null,

    @ColumnInfo(name = "keyHex") val keyHex: String? = null,
    @ColumnInfo(name = "keyB64") val keyB64: String? = null,
    @ColumnInfo(name = "keyText") val keyText: String? = null,
    @ColumnInfo(name = "keyLength") val keyLength: Int? = null,
    @ColumnInfo(name = "keyTruncated") val keyTruncated: Boolean = false,

    @ColumnInfo(name = "ivHex") val ivHex: String? = null,
    @ColumnInfo(name = "ivB64") val ivB64: String? = null,
    @ColumnInfo(name = "ivText") val ivText: String? = null,
    @ColumnInfo(name = "ivLength") val ivLength: Int? = null,
    @ColumnInfo(name = "ivTruncated") val ivTruncated: Boolean = false,

    @ColumnInfo(name = "keyAlias") val keyAlias: String? = null,
    @ColumnInfo(name = "keyExportable") val keyExportable: Boolean = true,

    @ColumnInfo(name = "inputHex") val inputHex: String? = null,
    @ColumnInfo(name = "inputB64") val inputB64: String? = null,
    @ColumnInfo(name = "inputText") val inputText: String? = null,
    @ColumnInfo(name = "inputLength") val inputLength: Int? = null,
    @ColumnInfo(name = "inputTruncated") val inputTruncated: Boolean = false,

    @ColumnInfo(name = "outputHex") val outputHex: String? = null,
    @ColumnInfo(name = "outputB64") val outputB64: String? = null,
    @ColumnInfo(name = "outputText") val outputText: String? = null,
    @ColumnInfo(name = "outputLength") val outputLength: Int? = null,
    @ColumnInfo(name = "outputTruncated") val outputTruncated: Boolean = false,

    @ColumnInfo(name = "saltHex") val saltHex: String? = null,
    @ColumnInfo(name = "saltB64") val saltB64: String? = null,
    @ColumnInfo(name = "saltText") val saltText: String? = null,
    @ColumnInfo(name = "saltLength") val saltLength: Int? = null,
    @ColumnInfo(name = "saltTruncated") val saltTruncated: Boolean = false,

    @ColumnInfo(name = "aadHex") val aadHex: String? = null,
    @ColumnInfo(name = "aadB64") val aadB64: String? = null,
    @ColumnInfo(name = "aadText") val aadText: String? = null,
    @ColumnInfo(name = "aadLength") val aadLength: Int? = null,
    @ColumnInfo(name = "aadTruncated") val aadTruncated: Boolean = false,

    @ColumnInfo(name = "publicKeyHex") val publicKeyHex: String? = null,
    @ColumnInfo(name = "publicKeyB64") val publicKeyB64: String? = null,
    @ColumnInfo(name = "publicKeyText") val publicKeyText: String? = null,
    @ColumnInfo(name = "publicKeyLength") val publicKeyLength: Int? = null,
    @ColumnInfo(name = "publicKeyTruncated") val publicKeyTruncated: Boolean = false,

    @ColumnInfo(name = "paramsJson") val paramsJson: String? = null,
    @ColumnInfo(name = "verifyResult") val verifyResult: Boolean? = null,
    @ColumnInfo(name = "processName") val processName: String? = null,

    // ---- 数据库监听列(v5→v6 迁移新增,recordType='db' 行承载;crypto 行为 NULL) ----

    /** SQL 语句原文(上限 RecordLimits.SQL_MAX_CHARS,超限置 paramsJson["sqlTruncated"])。 */
    @ColumnInfo(name = "sql") val sql: String? = null,

    /** 数据库完整路径(打开/关联事件时写入)。 */
    @ColumnInfo(name = "dbPath") val dbPath: String? = null,

    /** 绑定参数/ContentValues 序列化 JSON(上限 RecordLimits.BIND_ARGS_MAX_CHARS)。 */
    @ColumnInfo(name = "bindArgsJson") val bindArgsJson: String? = null,

    /** 查询结果行序列化 JSON(上限 HookSwitch.maxResultRows/maxResultChars 双闸门)。 */
    @ColumnInfo(name = "rowsJson") val rowsJson: String? = null,

    /** 查询结果行数(被动获取:游标未物化时为 null)。 */
    @ColumnInfo(name = "rowCount") val rowCount: Int? = null,

    /** DML 受影响行数(update/delete/executeUpdateDelete 返回)。 */
    @ColumnInfo(name = "affectedCount") val affectedCount: Int? = null,

    /** 插入 rowId(insert/replace/executeInsert 返回)。 */
    @ColumnInfo(name = "rowId") val rowId: Long? = null,

    /** 标量查询结果(simpleQueryForLong/String)。 */
    @ColumnInfo(name = "resultText") val resultText: String? = null,

    @ColumnInfo(name = "contextId") val contextId: Long = 0L,
    @ColumnInfo(name = "threadName") val threadName: String,
    @ColumnInfo(name = "stack") val stackJson: String? = null,
    @ColumnInfo(name = "timestamp") val timestamp: Long = System.currentTimeMillis(),

    /** 注入侧进程级单调序号(重放幂等键的一部分);v2 及更早的行无此列,迁移后为 NULL。 */
    @ColumnInfo(name = "seq") val seq: Long? = null,
)