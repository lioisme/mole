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

    /**
     * 默认 hex 字符上限(2×1MB)——**用户意图值**;实际物化受
     * [MAX_CONTENT_MATERIALIZE_BYTES] 推导的硬保险丝截断(V45 T1.3 起该上限 = 96KiB 字节,
     * 超出部分置 truncated 标记,见 [com.lioisme.modev.core.util.BytesView.of])。
     */
    const val DEFAULT_MAX_HEX_CHARS = DEFAULT_MAX_BYTES_PER_FIELD * 2

    /** 默认文本字节上限(1MB;同受内容物化硬保险丝约束)。 */
    const val DEFAULT_MAX_TEXT_CHARS = DEFAULT_MAX_BYTES_PER_FIELD

    /**
     * 单字段**内容物化**上限(输入字节)。V44 T2.3 引入为内存护栏,V45 T1.3(ST-3)
     * 改为**传输可送达性不变量**——旧值 8MiB 与本文件自述的行上限直接矛盾:
     * 8MiB 字段仅 hex 一份就 16M 字符 > [WIRE_MAX_LINE_CHARS] 4 倍,
     * 即「合法配置必然产出被 WAL 判为损坏行、永久丢弃并触发整文件重写」的记录。
     *
     * 现由 [WIRE_MAX_LINE_CHARS] 反推:一条记录最宽 7 个三态视图片段
     * ([MAX_TRIPLE_VIEW_FIELDS]),每字节最坏 [MAX_CHARS_PER_CONTENT_BYTE] 字符 ⇒
     * 上限 ≤ 4Mi/35 = 119,837 字节,取整 96KiB 留余量。
     * 语义 = 「保元弃内容」:超限保留 totalLength + truncated + hex 标识前缀,
     * 详情页以截断标记呈现。用户配置的更大值([DEFAULT_MAX_BYTES_PER_FIELD])被本上限
     * 钳制(而非静默丢整条),`0=无限` 同样被钳制。
     * 与 [com.lioisme.modev.core.util.BytesView.HARD_MAX_HEX_CHARS] 同字节预算
     * (后者 = 本值 × 2),单一事实源在此;不变量由 `RecordLimitsInvariantTest` 锁死。
     */
    const val MAX_CONTENT_MATERIALIZE_BYTES = 96 * 1024

    /** 注入侧 WAL 单行 JSON 字符上限(超限由 `RecordSink.deliver` 统一裁决并计数)。 */
    const val WIRE_MAX_LINE_CHARS = 4 * 1024 * 1024

    /** 数据库监听:SQL 语句文本捕获上限(纯文本直接截断,超限置 params["sqlTruncated"])。 */
    const val SQL_MAX_CHARS = 64 * 1024

    /** 数据库监听:绑定参数序列化字符上限(ContentValues 同构)。 */
    const val BIND_ARGS_MAX_CHARS = 64 * 1024

    /** 数据库监听:单条查询结果行数默认上限(HookSwitch.maxResultRows)。 */
    const val DEFAULT_MAX_RESULT_ROWS = 100

    /** 数据库监听:单条查询结果序列化字符默认上限(HookSwitch.maxResultChars)。 */
    const val DEFAULT_MAX_RESULT_CHARS = 256 * 1024

    // ---- V45 T1.3(ST-3)上限三角:让「配置上限 vs 传输上限」成为可判定量 ----

    /**
     * 单个字节数组字段三态视图(hex + base64 + text)的最坏字符系数:
     * hex 2 + base64 4/3 + text 1 ≈ 4.34,取整保守 = 5。
     */
    const val MAX_CHARS_PER_CONTENT_BYTE = 5

    /**
     * 一条记录可并存的三态视图片段数上限(CRYPTO:key / iv / salt / aad /
     * publicKey / input / output = 7,全工程最宽形态)。
     */
    const val MAX_TRIPLE_VIEW_FIELDS = 7

    /** 给定单字段上限时,一条记录内容视图的最坏字符数(不变量看门)。 */
    fun worstCaseContentChars(maxBytesPerField: Int): Long =
        maxBytesPerField.toLong() * MAX_CHARS_PER_CONTENT_BYTE * MAX_TRIPLE_VIEW_FIELDS

    /** 非内容列(sql/stack/path/params + JSON 语法与转义)为一条记录预留的最坏字符数。 */
    const val NON_CONTENT_RESERVE_CHARS = 512 * 1024

    /**
     * 满足「最坏形态记录仍可送达([WIRE_MAX_LINE_CHARS] 内,留 [NON_CONTENT_RESERVE_CHARS]
     * 给非内容列)」的单字段上限上界。默认 [DEFAULT_MAX_BYTES_PER_FIELD] 远大于本值 ——
     * 故用户意图值一律被 [MAX_CONTENT_MATERIALIZE_BYTES] 钳制(决策 D-V45-8)。
     */
    const val MAX_SAFE_BYTES_PER_FIELD: Int =
        (WIRE_MAX_LINE_CHARS - NON_CONTENT_RESERVE_CHARS) /
            (MAX_CHARS_PER_CONTENT_BYTE * MAX_TRIPLE_VIEW_FIELDS)
}