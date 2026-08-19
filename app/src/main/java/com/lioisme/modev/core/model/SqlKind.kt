package com.lioisme.modev.core.model

/**
 * 数据库操作类别。每个类别对应一类 Hook 目标与一个独立功能开关([HookSwitch.dbEnabled])。
 */
enum class SqlKind {
    /** 数据库生命周期:打开 / 关闭(SQLiteDatabase.openDatabase/openOrCreateDatabase/create/close、SQLiteOpenHelper) */
    OPEN,

    /** 查询:query / rawQuery / 预编译标量查询,含结果集采集 */
    QUERY,

    /** 增删改:insert / replace / update / delete 便捷方法 */
    DML,

    /** 原生 SQL 执行:execSQL / SQLiteStatement.execute* / attach */
    EXEC,

    /** 事务:beginTransaction / endTransaction / setTransactionSuccessful */
    TRANSACTION,
}

/**
 * 操作 → 类别映射(单点维护;注入侧 emit 开关判断与 UI 标签共用)。
 */
val SqlOperation.kind: SqlKind
    get() = when (this) {
        SqlOperation.OPEN_DATABASE, SqlOperation.CLOSE_DATABASE,
        SqlOperation.OPEN_HELPER, SqlOperation.CLOSE_HELPER -> SqlKind.OPEN

        SqlOperation.QUERY, SqlOperation.RAW_QUERY, SqlOperation.QUERY_RESULT,
        SqlOperation.STATEMENT_QUERY_LONG, SqlOperation.STATEMENT_QUERY_STRING -> SqlKind.QUERY

        SqlOperation.INSERT, SqlOperation.REPLACE,
        SqlOperation.UPDATE, SqlOperation.DELETE -> SqlKind.DML

        SqlOperation.EXEC_SQL, SqlOperation.STATEMENT_EXECUTE,
        SqlOperation.STATEMENT_EXECUTE_INSERT, SqlOperation.STATEMENT_EXECUTE_UPDATE_DELETE,
        SqlOperation.ATTACH -> SqlKind.EXEC

        SqlOperation.BEGIN_TRANSACTION, SqlOperation.END_TRANSACTION,
        SqlOperation.SET_TRANSACTION_SUCCESSFUL -> SqlKind.TRANSACTION
    }