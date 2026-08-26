package com.lioisme.modev.core.model

/**
 * 记录类型领域枚举(V17 R2 记录底层一体化):
 * 承载 wire/存储字符串语义([RecordTypes] 为协议/存储层字符串常量库,本枚举是其语义化视图);
 * [fromWire] 未知类型返回 null(协议/旧数据漂移安全兜底,绝不误标)。
 * REST/MCP 层按本枚举分发记录类型。
 */
enum class RecordType(val wire: String) {
    /** 算法捕获记录(JCA/WebView JS 调用)。 */
    CRYPTO(RecordTypes.CRYPTO),

    /** 数据库操作记录(SQLiteDatabase / SQLiteOpenHelper / SQLiteStatement / SQLiteCursor)。 */
    DATABASE(RecordTypes.DATABASE),

    /** 行为监听记录(文件系统 / Assets / Shell,V27 一等公民)。 */
    BEHAVIOR(RecordTypes.BEHAVIOR),

    /** SharedPreferences 监听记录(读入配置文件 + Get/Put 全家桶,V28 一等公民)。 */
    PREFS(RecordTypes.PREFS),

    /** WebView 监听记录(页面加载/JS 执行/桥注册/调试/JS 密码学,V29 一等公民)。 */
    WEBVIEW(RecordTypes.WEBVIEW);

    companion object {
        fun fromWire(value: String?): RecordType? = entries.firstOrNull { it.wire == value }
    }
}