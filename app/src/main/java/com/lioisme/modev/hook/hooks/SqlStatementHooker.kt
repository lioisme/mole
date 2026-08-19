package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.SqlBindValue
import com.lioisme.modev.core.model.SqlOperation
import com.lioisme.modev.core.model.SqlValueType
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.ProbeContext
import com.lioisme.modev.hook.SqlContextTracker

/**
 * SQLiteStatement 预编译语句 Hooker(T7,ADR-7):
 * bind* 写入实例键控暂存(bindAllArgsAsStrings 批量展开为 1-based 序号);
 * execute 族 emit 时快照绑定额(executeInsert → rowId、executeUpdateDelete → affected、
 * simpleQueryFor* → resultText)后清空;clearBindings 清空;
 * sql 取 compileStatement 时的登记(SqlContextTracker.linkSql)。
 * 语句实例不注册独立上下文,记录关联所属 DB 实例 contextId。
 */
class SqlStatementHooker(
    context: ProbeContext,
) : SqlBaseHooker(context) {

    override fun install() {
        val stmt = DatabaseMethodRegistry.forName("android.database.sqlite.SQLiteStatement") ?: return
        hookAfter(stmt.namedAny("bindLong", "bindDouble", "bindString", "bindBlob", "bindNull")) {
            inv, _ -> afterBind(inv)
        }
        hookAfter(stmt.named("bindAllArgsAsStrings")) { inv, _ -> afterBindAll(inv) }
        hookAfter(stmt.named("clearBindings")) { inv, _ ->
            inv.thisObject?.let { SqlContextTracker.clearBinds(it) }
        }
        hookAfter(
            stmt.namedAny(
                "execute", "executeInsert", "executeUpdateDelete",
                "simpleQueryForLong", "simpleQueryForString",
            ),
        ) { inv, result -> afterExecute(inv, result) }
    }

    private fun afterBind(inv: Invocation) {
        val statement = inv.thisObject ?: return
        val index = (inv.getArg(0) as? Int) ?: return
        val bind = when (inv.executable?.name) {
            "bindLong" -> SqlBindValue(index, SqlValueType.L, v = inv.getArg(1)?.toString())
            "bindDouble" -> SqlBindValue(index, SqlValueType.D, v = inv.getArg(1)?.toString())
            "bindString" -> SqlBindValue(index, SqlValueType.S, v = inv.getArg(1) as? String)
            "bindBlob" -> (inv.getArg(1) as? ByteArray)?.let { b ->
                val view = viewOf(b)
                SqlBindValue(index, SqlValueType.B, hex = view?.hex, len = view?.totalLength, truncated = view?.truncated ?: false)
            }
            "bindNull" -> SqlBindValue(index, SqlValueType.N)
            else -> return
        } ?: return
        SqlContextTracker.bindsOf(statement)[index] = bind
    }

    private fun afterBindAll(inv: Invocation) {
        val statement = inv.thisObject ?: return
        val args = inv.getArg(0) as? Array<*> ?: return
        val binds = SqlContextTracker.bindsOf(statement)
        args.forEachIndexed { i, v -> valueToBind(i + 1, v)?.let { binds[i + 1] = it } }
    }

    private fun afterExecute(inv: Invocation, result: Any?) {
        val statement = inv.thisObject ?: return
        val op = when (inv.executable?.name) {
            "execute" -> SqlOperation.STATEMENT_EXECUTE
            "executeInsert" -> SqlOperation.STATEMENT_EXECUTE_INSERT
            "executeUpdateDelete" -> SqlOperation.STATEMENT_EXECUTE_UPDATE_DELETE
            "simpleQueryForLong" -> SqlOperation.STATEMENT_QUERY_LONG
            "simpleQueryForString" -> SqlOperation.STATEMENT_QUERY_STRING
            else -> return
        }
        val db = SqlContextTracker.dbOf(statement)
        val ctx = db?.let { SqlContextTracker.contextFor(it) }
        val binds = SqlContextTracker.bindsOf(statement).values.sortedBy { it.i }
        emit(
            operation = op,
            databaseName = ctx?.databaseName ?: "",
            contextId = db?.let { SqlContextTracker.identity(it) } ?: 0L,
            provider = "SQLiteStatement",
            dbPath = ctx?.dbPath,
            sql = SqlContextTracker.sqlOf(statement),
            bindArgs = binds.takeIf { it.isNotEmpty() },
            rowId = if (op == SqlOperation.STATEMENT_EXECUTE_INSERT) (result as? Long) else null,
            affectedCount = if (op == SqlOperation.STATEMENT_EXECUTE_UPDATE_DELETE) {
                (result as? Number)?.toInt()
            } else {
                null
            },
            resultText = when (op) {
                SqlOperation.STATEMENT_QUERY_LONG ->
                    (result as? Long)?.toString() ?: result?.toString()
                SqlOperation.STATEMENT_QUERY_STRING -> result as? String
                else -> null
            },
        )
        SqlContextTracker.clearBinds(statement)
    }
}