package com.lioisme.modev.core.model

/**
 * 全局捕获配置(V10:全应用共享一份,与包名解耦;V17 数据库监听扩展)。
 * 宿主 App 持久化于 `switch_global` 并经通道推送,注入进程实时读取更新。
 */
data class HookSwitch(
    val messageDigest: Boolean = true,
    val cipher: Boolean = true,
    val mac: Boolean = true,
    val signature: Boolean = true,
    val keyParam: Boolean = true,
    val keyGeneration: Boolean = true,
    val keyFactory: Boolean = true,
    val keyStore: Boolean = true,
    val keyAgreement: Boolean = true,
    val random: Boolean = true,
    val base64: Boolean = false,
    /** WebView JS 加解密监听(WebCrypto / CryptoJS / JSEncrypt,V12 起;默认关,同 base64 先例)。 */
    val webView: Boolean = false,
    /** 数据库监听:打开/关闭(openDatabase/openOrCreateDatabase/SQLiteOpenHelper;「常用类别默认启用」)。 */
    val dbOpen: Boolean = true,
    /** 数据库监听:查询(query/rawQuery,含结果集被动采集)。 */
    val dbQuery: Boolean = true,
    /** 数据库监听:增删改便捷方法(insert/replace/update/delete)。 */
    val dbDml: Boolean = true,
    /** 数据库监听:原生 SQL 执行(execSQL / SQLiteStatement.execute* / attach)。 */
    val dbExec: Boolean = true,
    /** 数据库监听:事务(begin/end/setTransactionSuccessful)。 */
    val dbTransaction: Boolean = true,
    /** 数据库监听:单条查询结果行数上限;0 = 不限制(不推荐,结果闸门双保险)。 */
    val maxResultRows: Int = RecordLimits.DEFAULT_MAX_RESULT_ROWS,
    /** 数据库监听:单条查询结果序列化字符上限;0 = 不限制(不推荐)。 */
    val maxResultChars: Int = RecordLimits.DEFAULT_MAX_RESULT_CHARS,
    val logStack: Boolean = true,
    val maxStackDepth: Int = 24,
    /** 单字段字节上限(全量捕获保险丝);0 表示无限。 */
    val maxBytesPerField: Int = RecordLimits.DEFAULT_MAX_BYTES_PER_FIELD,
    /** 调用栈前缀 denylist:任一帧命中即跳过该记录;默认空 = 全捕获。 */
    val ignoreStackPrefixes: List<String> = emptyList(),
    /** @deprecated 由 [maxBytesPerField] 表达;保留字段兼容旧配置 JSON。 */
    val maxHexChars: Int = RecordLimits.DEFAULT_MAX_HEX_CHARS,
    /** @deprecated 由 [maxBytesPerField] 表达;保留字段兼容旧配置 JSON。 */
    val maxTextChars: Int = RecordLimits.DEFAULT_MAX_TEXT_CHARS,
) {
    /** 是否存在任何需要 Hook 的类别(算法 + 数据库任一能力启用)。 */
    val anyEnabled: Boolean
        get() = anyCryptoEnabled || anyDbEnabled

    /** 算法捕获相关类别是否任一启用(能力投影,供算法监听能力判定)。 */
    val anyCryptoEnabled: Boolean
        get() = messageDigest || cipher || mac || signature || keyParam || keyGeneration ||
            keyFactory || keyStore || keyAgreement || random || base64 || webView

    /** 数据库监听相关类别是否任一启用(能力投影,供数据库监听能力判定)。 */
    val anyDbEnabled: Boolean
        get() = dbOpen || dbQuery || dbDml || dbExec || dbTransaction

    fun enabled(kind: CryptoKind): Boolean = when (kind) {
        CryptoKind.MESSAGE_DIGEST -> messageDigest
        CryptoKind.CIPHER -> cipher
        CryptoKind.MAC -> mac
        CryptoKind.SIGNATURE -> signature
        CryptoKind.KEY_PARAM -> keyParam
        CryptoKind.KEY_GENERATION -> keyGeneration
        CryptoKind.KEY_FACTORY -> keyFactory
        CryptoKind.KEY_STORE -> keyStore
        CryptoKind.KEY_AGREEMENT -> keyAgreement
        CryptoKind.RANDOM -> random
        CryptoKind.BASE64 -> base64
        CryptoKind.WEB_CRYPTO -> webView
    }

    fun dbEnabled(kind: SqlKind): Boolean = when (kind) {
        SqlKind.OPEN -> dbOpen
        SqlKind.QUERY -> dbQuery
        SqlKind.DML -> dbDml
        SqlKind.EXEC -> dbExec
        SqlKind.TRANSACTION -> dbTransaction
    }

    companion object {
        val DEFAULT = HookSwitch()
    }
}