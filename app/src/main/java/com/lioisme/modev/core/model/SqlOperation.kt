package com.lioisme.modev.core.model

/**
 * 数据库调用中的具体操作阶段。
 */
enum class SqlOperation {
    /** 打开数据库文件(SQLiteDatabase.openDatabase/openOrCreateDatabase/create) */
    OPEN_DATABASE,

    /** 关闭数据库(SQLiteDatabase.close) */
    CLOSE_DATABASE,

    /** 经 SQLiteOpenHelper 获取可写/可读数据库(隐式打开) */
    OPEN_HELPER,

    /** 关闭 SQLiteOpenHelper */
    CLOSE_HELPER,

    /** 投影查询(SQLiteDatabase.query/queryWithFactory) */
    QUERY,

    /** 原生 SQL 查询(SQLiteDatabase.rawQuery/rawQueryWithFactory) */
    RAW_QUERY,

    /** 查询结果集回执(游标关闭时补发,同 contextId 关联 QUERY/RAW_QUERY) */
    QUERY_RESULT,

    /** 预编译标量查询:simpleQueryForLong */
    STATEMENT_QUERY_LONG,

    /** 预编译标量查询:simpleQueryForString */
    STATEMENT_QUERY_STRING,

    /** 便捷插入(insert/insertOrThrow/insertWithOnConflict) */
    INSERT,

    /** 便捷替换(replace/replaceOrThrow) */
    REPLACE,

    /** 便捷更新(update/updateWithOnConflict) */
    UPDATE,

    /** 便捷删除(delete) */
    DELETE,

    /** 原生 SQL 执行(execSQL) */
    EXEC_SQL,

    /** 预编译语句执行(SQLiteStatement.execute,无返回值) */
    STATEMENT_EXECUTE,

    /** 预编译语句插入执行(SQLiteStatement.executeInsert,返回 rowId) */
    STATEMENT_EXECUTE_INSERT,

    /** 预编译语句更新/删除执行(SQLiteStatement.executeUpdateDelete,返回受影响行数) */
    STATEMENT_EXECUTE_UPDATE_DELETE,

    /** 附加外部数据库(SQLiteDatabase.attach) */
    ATTACH,

    /** 开启事务(beginTransaction 及变体) */
    BEGIN_TRANSACTION,

    /** 结束事务(endTransaction,含成功标记) */
    END_TRANSACTION,

    /** 标记事务成功(setTransactionSuccessful) */
    SET_TRANSACTION_SUCCESSFUL,
}
