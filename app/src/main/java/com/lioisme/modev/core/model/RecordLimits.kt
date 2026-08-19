package com.lioisme.modev.core.model

/**
 * 记录采集/传输/存储的字节上限单一事实源(ADR-5):
 * 全工程以本对象为准,任何一处调整其余自动跟随。
 *
 * - [DEFAULT_MAX_BYTES_PER_FIELD]:单字段捕获保险丝(默认 1MB,0=无限);
 * - [DEFAULT_MAX_HEX_CHARS]/[DEFAULT_MAX_TEXT_CHARS]:BytesView 双态视图默认上限;
 * - [WIRE_MAX_LINE_CHARS]:注入侧 WAL 单行 JSON 上限(依赖 2×hex 上限的推算);
 * - [SQL_MAX_CHARS]/[BIND_ARGS_MAX_CHARS]:数据库监听 SQL 文本与绑定参数上限;
 * - [DEFAULT_MAX_RESULT_ROWS]/[DEFAULT_MAX_RESULT_CHARS]:查询结果集双闸门默认值。
 */
object RecordLimits {

    /** 单字段字节上限默认值(HookSwitch.maxBytesPerField)。 */
    const val DEFAULT_MAX_BYTES_PER_FIELD = 1_048_576

    /** 默认 hex 字符上限(2×1MB,支持 1MB 字段全量输出)。 */
    const val DEFAULT_MAX_HEX_CHARS = DEFAULT_MAX_BYTES_PER_FIELD * 2

    /** 默认文本字节上限(1MB)。 */
    const val DEFAULT_MAX_TEXT_CHARS = DEFAULT_MAX_BYTES_PER_FIELD

    /** 注入侧 WAL 单行 JSON 字符上限(超限按损坏行处理,防单行膨胀击穿内存)。 */
    const val WIRE_MAX_LINE_CHARS = 4 * 1024 * 1024

    /** 数据库监听:SQL 语句文本捕获上限(纯文本直接截断,超限置 params["sqlTruncated"])。 */
    const val SQL_MAX_CHARS = 64 * 1024

    /** 数据库监听:绑定参数序列化字符上限(ContentValues 同构)。 */
    const val BIND_ARGS_MAX_CHARS = 64 * 1024

    /** 数据库监听:单条查询结果行数默认上限(HookSwitch.maxResultRows)。 */
    const val DEFAULT_MAX_RESULT_ROWS = 100

    /** 数据库监听:单条查询结果序列化字符默认上限(HookSwitch.maxResultChars)。 */
    const val DEFAULT_MAX_RESULT_CHARS = 256 * 1024
}