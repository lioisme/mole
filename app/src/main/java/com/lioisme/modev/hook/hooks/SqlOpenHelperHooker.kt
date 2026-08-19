package com.lioisme.modev.hook.hooks

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.lioisme.modev.core.model.SqlOperation
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.ProbeContext
import com.lioisme.modev.hook.SqlContextTracker

/**
 * SQLiteOpenHelper 门面 Hooker(T7):
 * getWritableDatabase/getReadableDatabase 为隐式打开(after 注册返回 DB 实例上下文,
 * emit OPEN_HELPER,dbName=getDatabaseName()、dbPath=返回库路径、helper 类名入 params);
 * close 为显式关闭(emit CLOSE_HELPER)。
 */
class SqlOpenHelperHooker(
    context: ProbeContext,
) : SqlBaseHooker(context) {

    override fun install() {
        val helper = DatabaseMethodRegistry.forName("android.database.sqlite.SQLiteOpenHelper") ?: return
        hookAfter(helper.namedAny("getWritableDatabase", "getReadableDatabase")) {
            inv, result -> afterOpen(inv, result)
        }
        hookAfter(helper.named("close")) { inv, _ -> afterClose(inv) }
    }

    private fun afterOpen(inv: Invocation, result: Any?) {
        val helper = inv.thisObject as? SQLiteOpenHelper ?: return
        val db = result as? SQLiteDatabase ?: return
        val ctx = SqlContextTracker.contextFor(db)
        if (ctx.dbPath == null) {
            ctx.dbPath = runCatching { db.path }.getOrNull()
        }
        if (ctx.databaseName == null) {
            ctx.databaseName = runCatching { helper.databaseName }.getOrNull()
                ?: dbNameOf(ctx.dbPath)
        }
        emit(
            operation = SqlOperation.OPEN_HELPER,
            databaseName = ctx.databaseName ?: "",
            contextId = SqlContextTracker.identity(db),
            provider = "SQLiteOpenHelper",
            dbPath = ctx.dbPath,
            params = mapOf("class" to helper.javaClass.name),
        )
    }

    private fun afterClose(inv: Invocation) {
        val helper = inv.thisObject as? SQLiteOpenHelper ?: return
        emit(
            operation = SqlOperation.CLOSE_HELPER,
            databaseName = runCatching { helper.databaseName }.getOrNull() ?: "",
            contextId = 0L,
            provider = "SQLiteOpenHelper",
            params = mapOf("class" to helper.javaClass.name),
        )
    }
}