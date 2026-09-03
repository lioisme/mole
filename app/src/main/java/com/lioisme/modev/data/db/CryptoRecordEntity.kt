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

    // ---- 行为监听列(v8→v9 迁移新增,recordType='behavior' 行承载;其余行为 NULL) ----

    /** 主体绝对路径(文件/目录;asset 名;shell 行为 NULL)。 */
    @ColumnInfo(name = "path") val path: String? = null,

    /** 重命名/移动目标路径(RENAME 操作)。 */
    @ColumnInfo(name = "destPath") val destPath: String? = null,

    /** Shell argv JSON 字符串数组(EXEC 操作)。 */
    @ColumnInfo(name = "command") val command: String? = null,

    /** 文件字节数(仅内容捕获或调用即可得时写入;禁止额外探测)。 */
    @ColumnInfo(name = "fileSize") val fileSize: Long? = null,

    /** 捕获内容三态视图(V27 内容捕获,五元组复制既有字节列约定)。 */
    @ColumnInfo(name = "contentHex") val contentHex: String? = null,
    @ColumnInfo(name = "contentB64") val contentB64: String? = null,
    @ColumnInfo(name = "contentText") val contentText: String? = null,
    @ColumnInfo(name = "contentLength") val contentLength: Int? = null,
    @ColumnInfo(name = "contentTruncated") val contentTruncated: Boolean = false,

    // ---- 偏好监听列(v9→v10 迁移新增,recordType='prefs' 行承载;其余行为 NULL) ----

    /** 配置键(get/put/remove 系列首参;getAll/clear 为 NULL)。 */
    @ColumnInfo(name = "prefKey") val prefKey: String? = null,

    // ---- 网络环境监听列(v10→v11 迁移新增,recordType='network' 行承载;其余行为 NULL) ----

    /** 请求 URL(REQUEST 类别)。 */
    @ColumnInfo(name = "url") val url: String? = null,

    /** HTTP 方法(GET/POST 等,REQUEST 类别)。 */
    @ColumnInfo(name = "method") val httpMethod: String? = null,

    /** HTTP 状态码(REQUEST 响应)。 */
    @ColumnInfo(name = "statusCode") val statusCode: Int? = null,

    /** 请求头 JSON(REQUEST 类别)。 */
    @ColumnInfo(name = "requestHeadersJson") val requestHeadersJson: String? = null,

    /** 响应头 JSON(REQUEST 类别)。 */
    @ColumnInfo(name = "responseHeadersJson") val responseHeadersJson: String? = null,

    /** TLS 协议版本(REQUEST 类别,如 TLSv1.3)。 */
    @ColumnInfo(name = "sslProtocol") val sslProtocol: String? = null,

    /** 密码套件(REQUEST 类别)。 */
    @ColumnInfo(name = "cipherSuite") val cipherSuite: String? = null,

    /** 对端证书链 JSON(REQUEST 类别,SHA-256 哈希列表)。 */
    @ColumnInfo(name = "peerCertificatesJson") val peerCertificatesJson: String? = null,

    /** 代理主机(PROXY 类别)。 */
    @ColumnInfo(name = "proxyHost") val proxyHost: String? = null,

    /** 代理端口(PROXY 类别)。 */
    @ColumnInfo(name = "proxyPort") val proxyPort: Int? = null,

    /** VPN 是否活跃(VPN 类别;1=活跃,0=非活跃,NULL=未知)。 */
    @ColumnInfo(name = "vpnActive") val vpnActive: Int? = null,

    // ---- UI 交互监听列(v11→v12 迁移新增,recordType='ui' 行承载;其余行为 NULL) ----

    /** 弹窗标题(DIALOG_SHOW/DIALOG_KEYWORD)。 */
    @ColumnInfo(name = "dialogTitle") val dialogTitle: String? = null,
    /** 弹窗消息(DIALOG_SHOW/DIALOG_KEYWORD)。 */
    @ColumnInfo(name = "dialogMessage") val dialogMessage: String? = null,
    /** 命中关键字列表 JSON 数组(DIALOG_KEYWORD)。 */
    @ColumnInfo(name = "dialogKeywords") val dialogKeywords: String? = null,
    /** 弹窗拦截标记(1=拦截,0=放行;DIALOG_KEYWORD)。 */
    @ColumnInfo(name = "dialogIntercepted") val dialogIntercepted: Int? = null,
    /** 控件文本内容(WIDGET_TEXT)。 */
    @ColumnInfo(name = "widgetText") val widgetText: String? = null,
    /** 控件所在 Activity 类名(WIDGET_TEXT)。 */
    @ColumnInfo(name = "widgetClassName") val widgetClassName: String? = null,
    /** 目标 Activity 类名(ACTIVITY_*)。 */
    @ColumnInfo(name = "activityName") val activityName: String? = null,
    /** Intent action(ACTIVITY_*)。 */
    @ColumnInfo(name = "intentAction") val intentAction: String? = null,
    /** Intent data URI(ACTIVITY_*)。 */
    @ColumnInfo(name = "intentData") val intentData: String? = null,
    /** Intent extras 序列化 JSON(ACTIVITY_*)。 */
    @ColumnInfo(name = "intentExtrasJson") val intentExtrasJson: String? = null,
    /** Intent flags(ACTIVITY_*)。 */
    @ColumnInfo(name = "intentFlags") val intentFlags: Int? = null,

    // ---- 系统信息监听列(v11→v12 迁移新增,recordType='system' 行承载;其余行为 NULL) ----

    /** Logcat tag(LOGCAT)。 */
    @ColumnInfo(name = "logcatTag") val logcatTag: String? = null,
    /** Logcat level D/I/W/E/V/F(LOGCAT)。 */
    @ColumnInfo(name = "logcatLevel") val logcatLevel: String? = null,
    /** Logcat 消息(LOGCAT)。 */
    @ColumnInfo(name = "logcatMessage") val logcatMessage: String? = null,
    /** 应用版本名(APP_INFO)。 */
    @ColumnInfo(name = "appVersion") val appVersion: String? = null,
    /** 应用版本号(APP_INFO)。 */
    @ColumnInfo(name = "appVersionCode") val appVersionCode: Int? = null,
    /** 最低 SDK(APP_INFO)。 */
    @ColumnInfo(name = "appMinSdk") val appMinSdk: Int? = null,
    /** 目标 SDK(APP_INFO)。 */
    @ColumnInfo(name = "appTargetSdk") val appTargetSdk: Int? = null,
    /** 权限列表 JSON 数组(APP_INFO)。 */
    @ColumnInfo(name = "appPermissions") val appPermissions: String? = null,
    /** 签名 SHA-256 哈希(SIGNATURE)。 */
    @ColumnInfo(name = "signatureHash") val signatureHash: String? = null,
    /** 签名 Base64(SIGNATURE)。 */
    @ColumnInfo(name = "signatureBase64") val signatureBase64: String? = null,

    // ---- 进程控制列(v11→v12 迁移新增,recordType='process' 行承载;其余行为 NULL) ----

    /** System.exit 参数(EXIT_INTERCEPT)。 */
    @ColumnInfo(name = "exitCode") val exitCode: Int? = null,
    /** killProcess 目标 pid(EXIT_INTERCEPT)。 */
    @ColumnInfo(name = "processPid") val processPid: Int? = null,
    /** 异常类名(CRASH_PREVENT)。 */
    @ColumnInfo(name = "crashType") val crashType: String? = null,
    /** 异常消息(CRASH_PREVENT)。 */
    @ColumnInfo(name = "crashMessage") val crashMessage: String? = null,
    /** 崩溃拦截标记(1=已拦截;CRASH_PREVENT)。 */
    @ColumnInfo(name = "crashBlocked") val crashBlocked: Int? = null,
    /** 录屏拦截标记(1=已拦截;SCREEN_RECORD)。 */
    @ColumnInfo(name = "screenRecordBlocked") val screenRecordBlocked: Int? = null,
    /** 干预动作标记("BLOCKED"/"DETECTED"/null;V34 审计增强)。 */
    @ColumnInfo(name = "interventionAction") val interventionAction: String? = null,

    @ColumnInfo(name = "contextId") val contextId: Long = 0L,
    @ColumnInfo(name = "threadName") val threadName: String,
    @ColumnInfo(name = "stack") val stackJson: String? = null,
    @ColumnInfo(name = "timestamp") val timestamp: Long = System.currentTimeMillis(),

    /** 注入侧进程级单调序号(重放幂等键的一部分);v2 及更早的行无此列,迁移后为 NULL。 */
    @ColumnInfo(name = "seq") val seq: Long? = null,
)