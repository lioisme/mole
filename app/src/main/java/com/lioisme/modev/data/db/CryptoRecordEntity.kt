package com.lioisme.modev.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 算法记录实体。BytesView 双态视图展开为独立列,枚举以名称字符串存储,堆栈以 JSON 存储。
 */
@Entity(
    tableName = "crypto_records",
    indices = [
        Index(value = ["packageName", "timestamp"]),
        Index(value = ["packageName", "kind"]),
    ],
)
data class CryptoRecordEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id") val id: Long = 0L,

    @ColumnInfo(name = "packageName") val packageName: String,
    @ColumnInfo(name = "kind") val kind: String,
    @ColumnInfo(name = "operation") val operation: String,
    @ColumnInfo(name = "algorithm") val algorithm: String,

    @ColumnInfo(name = "provider") val provider: String? = null,
    @ColumnInfo(name = "direction") val direction: Int? = null,

    @ColumnInfo(name = "keyHex") val keyHex: String? = null,
    @ColumnInfo(name = "keyText") val keyText: String? = null,
    @ColumnInfo(name = "keyLength") val keyLength: Int? = null,
    @ColumnInfo(name = "keyTruncated") val keyTruncated: Boolean = false,

    @ColumnInfo(name = "ivHex") val ivHex: String? = null,
    @ColumnInfo(name = "ivText") val ivText: String? = null,
    @ColumnInfo(name = "ivLength") val ivLength: Int? = null,
    @ColumnInfo(name = "ivTruncated") val ivTruncated: Boolean = false,

    @ColumnInfo(name = "keyAlias") val keyAlias: String? = null,
    @ColumnInfo(name = "keyExportable") val keyExportable: Boolean = true,

    @ColumnInfo(name = "inputHex") val inputHex: String? = null,
    @ColumnInfo(name = "inputText") val inputText: String? = null,
    @ColumnInfo(name = "inputLength") val inputLength: Int? = null,
    @ColumnInfo(name = "inputTruncated") val inputTruncated: Boolean = false,

    @ColumnInfo(name = "outputHex") val outputHex: String? = null,
    @ColumnInfo(name = "outputText") val outputText: String? = null,
    @ColumnInfo(name = "outputLength") val outputLength: Int? = null,
    @ColumnInfo(name = "outputTruncated") val outputTruncated: Boolean = false,

    @ColumnInfo(name = "contextId") val contextId: Long = 0L,
    @ColumnInfo(name = "threadName") val threadName: String,
    @ColumnInfo(name = "stack") val stackJson: String? = null,
    @ColumnInfo(name = "timestamp") val timestamp: Long = System.currentTimeMillis(),
)