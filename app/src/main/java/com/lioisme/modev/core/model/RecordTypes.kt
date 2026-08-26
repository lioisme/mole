package com.lioisme.modev.core.model

/**
 * 记录类型标识(V10 能力化架构;V17 数据库监听扩展):
 * 存储与线协议以字符串承载,缺失时按 [CRYPTO] 解释(旧数据/旧协议向后兼容)。
 * 未来行为监听、交互监听、文件监听等记录类型按此扩展。
 */
object RecordTypes {

    /** 算法捕获记录(JCA 调用)。 */
    const val CRYPTO = "crypto"

    /** 数据库操作记录(SQLiteDatabase / SQLiteOpenHelper / SQLiteStatement / SQLiteCursor)。 */
    const val DATABASE = "db"

    /** 行为监听记录(文件系统 / Assets / Shell,V27)。 */
    const val BEHAVIOR = "behavior"

    /** SharedPreferences 监听记录(读入配置文件 + Get/Put 全家桶,V28)。 */
    const val PREFS = "prefs"
}