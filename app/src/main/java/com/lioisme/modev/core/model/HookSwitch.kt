package com.lioisme.modev.core.model

import com.google.gson.Gson
import com.google.gson.JsonObject

/**
 * 全局捕获配置(V10:全应用共享一份,与包名解耦;V17 数据库监听扩展;
 * V18 算法/数据库监听全局总开关)。
 * 宿主 App 持久化于 `switch_global` 并经通道推送,注入进程实时读取更新。
 */
data class HookSwitch(
    /** 算法监听全局总开关:false = 全部算法类别停用(分开关保留原态,重开即恢复)。 */
    val cryptoListen: Boolean = true,
    /** 数据库监听全局总开关:false = 全部数据库类别停用(分开关保留原态,重开即恢复)。 */
    val dbListen: Boolean = true,
    val messageDigest: Boolean = true,
    val cipher: Boolean = true,
    val mac: Boolean = true,
    val signature: Boolean = true,
    /** 密钥参数构造:高频、逆向价值低,默认关(噪点类别,见 [CategoryTaxonomy])。 */
    val keyParam: Boolean = false,
    /** 密钥生成:高频、逆向价值低,默认关(噪点类别)。 */
    val keyGeneration: Boolean = false,
    /** 密钥还原:高频、逆向价值低,默认关(噪点类别)。 */
    val keyFactory: Boolean = false,
    /** 密钥库:高频、逆向价值低,默认关(噪点类别)。 */
    val keyStore: Boolean = false,
    /** 密钥协商:高频、逆向价值低,默认关(噪点类别)。 */
    val keyAgreement: Boolean = false,
    /** 随机数:极高频、逆向价值低,默认关(噪点类别)。 */
    val random: Boolean = false,
    /** Base64 编码:纯编码噪声,默认关(噪点类别,与 webView 同先例)。 */
    val base64: Boolean = false,
    /** WebView JS 加解密监听(WebCrypto / CryptoJS / JSEncrypt,V12 起;默认关,同 base64 先例)。 */
    val webView: Boolean = false,
    /**
     * 数据库监听:打开/关闭(openDatabase/openOrCreateDatabase/SQLiteOpenHelper)。
     * 高频、信号弱(仅生命周期),默认关(噪点类别,见 [CategoryTaxonomy])。
     */
    val dbOpen: Boolean = false,
    /** 数据库监听:查询(query/rawQuery,含结果集被动采集)。高信号,默认启用。 */
    val dbQuery: Boolean = true,
    /** 数据库监听:增删改便捷方法(insert/replace/update/delete)。高信号,默认启用。 */
    val dbDml: Boolean = true,
    /** 数据库监听:原生 SQL 执行(execSQL / SQLiteStatement.execute* / attach)。高信号,默认启用。 */
    val dbExec: Boolean = true,
    /**
     * 数据库监听:事务(begin/end/setTransactionSuccessful)。
     * 高频、信号弱,默认关(噪点类别)。
     */
    val dbTransaction: Boolean = false,
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

    /** 算法捕获相关类别是否任一启用(含全局总开关投影,供算法监听能力判定)。 */
    val anyCryptoEnabled: Boolean
        get() = cryptoListen && (messageDigest || cipher || mac || signature || keyParam || keyGeneration ||
            keyFactory || keyStore || keyAgreement || random || base64 || webView)

    /** 数据库监听相关类别是否任一启用(含全局总开关投影,供数据库监听能力判定)。 */
    val anyDbEnabled: Boolean
        get() = dbListen && (dbOpen || dbQuery || dbDml || dbExec || dbTransaction)

    fun enabled(kind: CryptoKind): Boolean = cryptoListen && when (kind) {
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

    fun dbEnabled(kind: SqlKind): Boolean = dbListen && when (kind) {
        SqlKind.OPEN -> dbOpen
        SqlKind.QUERY -> dbQuery
        SqlKind.DML -> dbDml
        SqlKind.EXEC -> dbExec
        SqlKind.TRANSACTION -> dbTransaction
    }

    companion object {
        val DEFAULT = HookSwitch()

        /**
         * Gson 反序列化 + 缺字段默认值补齐:新增字段(cryptoListen/dbListen 等)
         * 在旧配置 JSON 中缺失时,Gson 的 Unsafe 实例化会给 JVM 默认值(false),
         * 会误把历史配置判为「全部停用」。此处按 JsonObject 键存在性补齐默认值。
         */
        fun fromJson(gson: Gson, json: String): HookSwitch? = runCatching {
            val base = gson.fromJson(json, HookSwitch::class.java) ?: return null
            val obj = gson.fromJson(json, JsonObject::class.java)
            base.copy(
                cryptoListen = obj?.get("cryptoListen")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean ?: true,
                dbListen = obj?.get("dbListen")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean ?: true,
            )
        }.getOrNull()
    }
}