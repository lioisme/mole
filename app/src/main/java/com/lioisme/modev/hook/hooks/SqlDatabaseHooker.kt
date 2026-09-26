package com.lioisme.modev.hook.hooks

import android.content.ContentValues
import android.database.Cursor
import com.lioisme.modev.core.model.SqlBindValue
import com.lioisme.modev.core.model.SqlKind
import com.lioisme.modev.core.model.SqlOperation
import com.lioisme.modev.core.model.SqlResultCell
import com.lioisme.modev.core.model.SqlResultRow
import com.lioisme.modev.core.model.SqlValueType
import com.lioisme.modev.hook.CursorBuffer
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.ProbeContext
import com.lioisme.modev.hook.SqlContextTracker
import com.lioisme.modev.hook.SqlContext
import java.nio.ByteBuffer

/**
 * SQLiteDatabase 全表面 Hooker(T6):打开/关闭、增删改查、execSQL、预编译入口、
 * 事务生命周期;游标被动采集与 QUERY_RESULT 补发(ADR-6)。
 * 全部按方法名枚举注册(DatabaseMethodRegistry,ADR-8),任一重载缺失不影响其余。
 */
class SqlDatabaseHooker(
    context: ProbeContext,
) : SqlBaseHooker(context) {

    override fun install() {
        // V50 死钩修复:原形态是两处 `?: return` 串联 —— `SQLiteCursor` 此刻解析不到
        // (注入早于游标类首次加载是常态)会让**整个 SQLiteDatabase 表面一起不装**,
        // 表现就是「数据库监听按默认配置(OPEN 关、QUERY/DML/EXEC 开)整域零记录」,
        // 而设备矩阵的 db 档恰好全开 kind(含 OPEN)故永远测不到 —— 与 V49 实锤的
        // `readText`/`@hide` 同族:解析失败静默吞,文档却写着「已采集」。
        // 两个区域现在各自独立安装,任一解析失败只降级自己,并点名到日志。
        val db = DatabaseMethodRegistry.forName("android.database.sqlite.SQLiteDatabase")
        if (db == null) {
            android.util.Log.w("HookInstaller", "DB registry unresolved: android.database.sqlite.SQLiteDatabase (db surface skipped)")
        } else {
            installDatabase(db)
        }
        val cursor = DatabaseMethodRegistry.forName("android.database.sqlite.SQLiteCursor")
        if (cursor == null) {
            android.util.Log.w("HookInstaller", "DB registry unresolved: android.database.sqlite.SQLiteCursor (result-set capture deferred to first cursor)")
        } else {
            installCursor(cursor)
            cursorHooksInstalled = true
        }
    }

    /**
     * 游标钩子是否已挂(全进程一次)。
     *
     * 这里**不按游标类名各挂一次**:`moveToFirst/moveToNext/move/close` 实际声明在
     * `AbstractCursor` 上,`DatabaseMethodRegistry.methods` 取到的就是同一个 Method 对象 ——
     * 按类名去重会对同一方法重复注册,而 Vector 2.2 上「同一方法的第二个 hook 静默不执行」
     * ,重复注册等于把已有的钩子弄没。
     */
    @Volatile
    private var cursorHooksInstalled: Boolean = false

    // region SQLiteDatabase

    private fun installDatabase(db: DatabaseMethodRegistry) {
        // 打开(静态工厂;结果实例即受管 DB,注册 path/name)
        hookStaticAfter(db.namedAny("openDatabase", "openOrCreateDatabase", "create")) {
            inv, result -> afterOpen(inv, result)
        }
        hookAfter(db.named("close")) { inv, _ ->
            // V44 T2.2 门控先行:close 默认关(dbOpen=false),上下文组装/emit 全部后置。
            if (!config().dbEnabled(SqlKind.OPEN)) return@hookAfter
            val instance = inv.thisObject ?: return@hookAfter
            val ctx = SqlContextTracker.contextFor(instance)
            emitOpenish(SqlOperation.CLOSE_DATABASE, ctx, contextId = SqlContextTracker.identity(instance))
            SqlContextTracker.removeDb(instance)
        }

        // 查询(全部重载;结果游标注册被动采集缓冲)
        hookAfter(db.namedAny("query", "queryWithFactory", "rawQuery", "rawQueryWithFactory")) {
            inv, result -> afterQuery(inv, result)
        }

        // 增删改(便捷方法;返回 rowId / 受影响行数)
        hookAfter(db.namedAny("insert", "insertOrThrow", "insertWithOnConflict")) {
            inv, result -> afterDml(inv, result, SqlOperation.INSERT)
        }
        hookAfter(db.namedAny("replace", "replaceOrThrow")) { inv, result -> afterDml(inv, result, SqlOperation.REPLACE) }
        hookAfter(db.namedAny("update", "updateWithOnConflict")) { inv, result -> afterDml(inv, result, SqlOperation.UPDATE) }
        hookAfter(db.named("delete")) { inv, result -> afterDml(inv, result, SqlOperation.DELETE) }

        // 原生执行与预编译入口
        hookAfter(db.named("execSQL")) { inv, result -> afterExec(inv, result) }
        hookAfter(db.named("compileStatement")) { inv, result -> afterCompileStatement(inv, result) }
        hookAfter(db.named("attach")) { inv, result -> afterAttach(inv, result) }
        hookAfter(db.named("setJournalMode")) { inv, result -> afterJournalMode(inv, result) }
        hookAfter(db.named("enableWriteAheadLogging")) { inv, _ ->
            val instance = inv.thisObject ?: return@hookAfter
            SqlContextTracker.contextFor(instance).journalMode = "WAL"
        }

        // 事务(ADR-5)
        hookBefore(db.namedAny(
            "beginTransaction", "beginTransactionNonExclusive",
            "beginTransactionWithListener", "beginTransactionWithListenerNonExclusive",
        )) { inv, _ -> beforeBegin(inv) }
        hookAfter(db.named("setTransactionSuccessful")) { inv, _ -> afterSetSuccessful(inv) }
        hookAfter(db.named("endTransaction")) { inv, _ -> afterEndTransaction(inv) }
    }

    private fun afterOpen(inv: Invocation, result: Any?) {
        // V44 T2.2 门控先行:open 族默认关(dbOpen=false),旧形态每次开库先付
        // 反射取参 + 弱表登记,再在 emit 内部门控丢弃。
        if (!config().dbEnabled(SqlKind.OPEN)) return
        val db = result ?: return
        val path = runCatching { inv.getArg(0) as? String }.getOrNull()
        val ctx = SqlContextTracker.contextFor(db)
        if (ctx.dbPath == null) {
            ctx.dbPath = path
            ctx.databaseName = dbNameOf(path)
        }
        emitOpenish(SqlOperation.OPEN_DATABASE, ctx, contextId = SqlContextTracker.identity(db))
    }

    private fun emitOpenish(operation: SqlOperation, ctx: SqlContext, contextId: Long) {
        val params = mutableMapOf<String, String>()
        ctx.journalMode?.let { params["journalMode"] = it }
        emit(
            operation = operation,
            databaseName = ctx.databaseName ?: "",
            contextId = contextId,
            provider = "SQLiteDatabase",
            dbPath = ctx.dbPath,
            params = params.takeIf { it.isNotEmpty() },
        )
    }

    private fun afterQuery(inv: Invocation, result: Any?) {
        // V43 T2:columnNames 反射/mCountOf/参数组装全部先于 emit 门控执行——
        // QUERY kind 关闭时必须首行短路(V2.4)
        if (!config().dbEnabled(com.lioisme.modev.core.model.SqlKind.QUERY)) return
        val instance = inv.thisObject ?: return
        val cursor = result as? Cursor ?: return
        val sql = extractQuerySql(inv)
        val args = queryArgs(inv)
        val ctx = SqlContextTracker.contextFor(instance)
        // V50:注入期游标类常未加载 → 以「首个游标」为触发点补装(一次 volatile 读)
        ensureCursorHooks(cursor.javaClass)
        val switch = config()
        val buffer = SqlContextTracker.cursorBuffer(cursor)
        buffer.db = instance
        buffer.sql = sql
        buffer.maxRows = switch.maxResultRows
        buffer.maxChars = switch.maxResultChars
        buffer.params = buildMap {
            val paramTypes = inv.executable?.parameterTypes
            if (paramTypes != null && inv.executable?.name?.startsWith("rawQuery") != true) {
                val tableIdx = paramTypes.indexOfFirst { it == String::class.java }
                if (tableIdx >= 0) inv.getArg(tableIdx)?.let { put("table", it.toString()) }
            }
            runCatching { cursor.columnNames?.joinToString(",") }.getOrNull()?.let { put("columns", it) }
        }.takeIf { it.isNotEmpty() }
        // 行数被动反射(mCount;未物化游标为 null)
        buffer.rowCount = mCountOf(cursor)
        emit(
            operation = if (inv.executable?.name?.startsWith("rawQuery") == true) {
                SqlOperation.RAW_QUERY
            } else {
                SqlOperation.QUERY
            },
            databaseName = ctx.databaseName ?: "",
            contextId = SqlContextTracker.identity(instance),
            provider = "SQLiteDatabase",
            dbPath = ctx.dbPath,
            sql = sql,
            bindArgs = args,
            rowCount = buffer.rowCount,
            params = buffer.params,
        )
    }

    private fun extractQuerySql(inv: Invocation): String? {
        val params = inv.executable?.parameterTypes ?: return null
        val name = inv.executable?.name ?: return null
        if (name.startsWith("rawQuery")) {
            // rawQuery(String, String[]) | rawQueryWithFactory(CursorFactory, String, String[], String)
            val sqlIdx = params.indexOfFirst { it == String::class.java }
            return inv.getArg(sqlIdx) as? String
        }
        // query 族(参数顺序随重载漂移):table = 首个 String;columns = 首个数组;
        // 数组之后的字符串按序为 selection/groupBy/having/orderBy/limit
        val stringIdx = params.indices.filter { params[it] == String::class.java }
        val arrayIdx = params.indices.filter { params[it].isArray }
        val tableIdx = stringIdx.firstOrNull() ?: return null
        val columnsIdx = arrayIdx.firstOrNull() ?: return null
        val table = inv.getArg(tableIdx) as? String ?: return null
        val columns = inv.getArg(columnsIdx) as? Array<*> ?: return null
        val afterColumns = stringIdx.filter { it > columnsIdx }
        return buildString {
            fun seg(prefix: String, n: Int) {
                val s = inv.getArg(n) as? String ?: return
                if (s.isNotBlank()) append(prefix).append(s)
            }
            append("SELECT ")
            append(columns.joinToString(",") { it?.toString() ?: "*" })
            append(" FROM ").append(table)
            seg(" WHERE ", afterColumns.getOrNull(0) ?: -1)
            seg(" GROUP BY ", afterColumns.getOrNull(1) ?: -1)
            seg(" HAVING ", afterColumns.getOrNull(2) ?: -1)
            seg(" ORDER BY ", afterColumns.getOrNull(3) ?: -1)
            seg(" LIMIT ", afterColumns.getOrNull(4) ?: -1)
        }
    }

    private fun afterDml(inv: Invocation, result: Any?, operation: SqlOperation) {
        // V44 T2.2 门控先行:旧形态在 DML 门控之前先做 ContentValues 迭代 +
        // buildString 组装 SQL + 绑定序列化(每次 insert/update/delete 白付)。
        if (!config().dbEnabled(SqlKind.DML)) return
        val instance = inv.thisObject ?: return
        val ctx = SqlContextTracker.contextFor(instance)
        val table = inv.getArg(0) as? String
        val params = mutableMapOf<String, String>()
        table?.let { params["table"] = it }
        lastIntArg(inv)?.let { params["conflictAlgorithm"] = it.toString() }
        val sql = when (operation) {
            SqlOperation.INSERT, SqlOperation.REPLACE -> {
                val cv = inv.getArg(2) as? ContentValues
                buildString {
                    append(operation.name).append(" INTO ").append(table ?: "?")
                    val keys = cv?.keySet()?.toList().orEmpty()
                    if (keys.isNotEmpty()) {
                        append(" (").append(keys.joinToString(",")).append(") VALUES (")
                        append(keys.joinToString(",") { "?" }).append(")")
                    } else {
                        append(" DEFAULT VALUES")
                    }
                }
            }
            SqlOperation.UPDATE -> {
                val cv = inv.getArg(1) as? ContentValues
                buildString {
                    append("UPDATE ").append(table ?: "?")
                    val keys = cv?.keySet()?.toList().orEmpty()
                    if (keys.isNotEmpty()) append(" SET ").append(keys.joinToString(",") { "$it=?" })
                    (inv.getArg(2) as? String)?.takeIf { it.isNotBlank() }?.let { append(" WHERE ").append(it) }
                }
            }
            else -> {
                buildString {
                    append("DELETE FROM ").append(table ?: "?")
                    (inv.getArg(1) as? String)?.takeIf { it.isNotBlank() }?.let { append(" WHERE ").append(it) }
                }
            }
        }
        val valueBinds = when (operation) {
            SqlOperation.INSERT, SqlOperation.REPLACE -> contentValuesToBinds(inv.getArg(2) as? ContentValues)
            SqlOperation.UPDATE -> contentValuesToBinds(inv.getArg(1) as? ContentValues)
            else -> null
        }
        val whereBinds = when (operation) {
            SqlOperation.UPDATE, SqlOperation.DELETE -> lastArrayArg(inv)?.let { argsToBinds(it) }
            else -> null
        }
        val values = when {
            valueBinds != null && whereBinds != null -> valueBinds + whereBinds
            valueBinds != null -> valueBinds
            else -> whereBinds
        }
        val isWrite = operation == SqlOperation.UPDATE || operation == SqlOperation.DELETE
        emit(
            operation = operation,
            databaseName = ctx.databaseName ?: "",
            contextId = SqlContextTracker.identity(instance),
            provider = "SQLiteDatabase",
            dbPath = ctx.dbPath,
            sql = sql,
            bindArgs = values,
            rowId = if (isWrite) null else (result as? Long) ?: result?.let { it as? Number }?.toLong(),
            affectedCount = if (isWrite) (result as? Long)?.toInt() ?: (result as? Number)?.toInt() else null,
            params = params.takeIf { it.isNotEmpty() },
        )
    }

    private fun afterExec(inv: Invocation, result: Any?) {
        // V44 T2.2 门控先行:绑定参数序列化(argsToBinds)成本随参数规模增长。
        if (!config().dbEnabled(SqlKind.EXEC)) return
        val instance = inv.thisObject ?: return
        val ctx = SqlContextTracker.contextFor(instance)
        emit(
            operation = SqlOperation.EXEC_SQL,
            databaseName = ctx.databaseName ?: "",
            contextId = SqlContextTracker.identity(instance),
            provider = "SQLiteDatabase",
            dbPath = ctx.dbPath,
            sql = inv.getArg(0) as? String,
            bindArgs = argsToBinds(inv.getArg(1) as? Array<*>),
        )
    }

    private fun afterCompileStatement(inv: Invocation, result: Any?) {
        // V44 T2.2 门控先行:预编译入口归 EXEC 类别,旧形态每次 compileStatement
        // 都做弱表登记(statement→db / statement→sql)后才在 execute 处丢弃。
        if (!config().dbEnabled(SqlKind.EXEC)) return
        val instance = inv.thisObject ?: return
        val statement = result ?: return
        SqlContextTracker.linkStatement(statement, instance)
        SqlContextTracker.linkSql(statement, inv.getArg(0) as? String ?: "")
    }

    private fun afterAttach(inv: Invocation, result: Any?) {
        if (!config().dbEnabled(SqlKind.EXEC)) return
        val instance = inv.thisObject ?: return
        val ctx = SqlContextTracker.contextFor(instance)
        val params = buildMap {
            inv.getArg(0)?.let { put("path", it.toString()) }
            inv.getArg(1)?.let { put("alias", it.toString()) }
        }
        emit(
            operation = SqlOperation.ATTACH,
            databaseName = ctx.databaseName ?: "",
            contextId = SqlContextTracker.identity(instance),
            provider = "SQLiteDatabase",
            dbPath = ctx.dbPath,
            params = params.takeIf { it.isNotEmpty() },
        )
    }

    private fun afterJournalMode(inv: Invocation, result: Any?) {
        val instance = inv.thisObject ?: return
        val ctx = SqlContextTracker.contextFor(instance)
        ctx.journalMode = result as? String ?: inv.getArg(0) as? String
    }

    private fun beforeBegin(inv: Invocation) {
        // V44 T2.2 门控先行:事务默认关(dbTransaction=false),旧形态每次
        // beginTransaction 都先做弱表登记 + 深度自增。
        if (!config().dbEnabled(SqlKind.TRANSACTION)) return
        val instance = inv.thisObject ?: return
        val ctx = SqlContextTracker.contextFor(instance)
        ctx.transactionDepth++
        val exclusive = inv.executable?.name == "beginTransaction" ||
            inv.executable?.name == "beginTransactionWithListener"
        emit(
            operation = SqlOperation.BEGIN_TRANSACTION,
            databaseName = ctx.databaseName ?: "",
            contextId = SqlContextTracker.identity(instance),
            provider = "SQLiteDatabase",
            dbPath = ctx.dbPath,
            params = mapOf(
                "depth" to ctx.transactionDepth.toString(),
                "exclusive" to exclusive.toString(),
            ),
        )
    }

    private fun afterSetSuccessful(inv: Invocation) {
        if (!config().dbEnabled(SqlKind.TRANSACTION)) return
        val instance = inv.thisObject ?: return
        val ctx = SqlContextTracker.contextFor(instance)
        ctx.transactionSuccess = true
        emit(
            operation = SqlOperation.SET_TRANSACTION_SUCCESSFUL,
            databaseName = ctx.databaseName ?: "",
            contextId = SqlContextTracker.identity(instance),
            provider = "SQLiteDatabase",
            dbPath = ctx.dbPath,
            params = mapOf("depth" to ctx.transactionDepth.toString()),
        )
    }

    private fun afterEndTransaction(inv: Invocation) {
        if (!config().dbEnabled(SqlKind.TRANSACTION)) return
        val instance = inv.thisObject ?: return
        val ctx = SqlContextTracker.contextFor(instance)
        emit(
            operation = SqlOperation.END_TRANSACTION,
            databaseName = ctx.databaseName ?: "",
            contextId = SqlContextTracker.identity(instance),
            provider = "SQLiteDatabase",
            dbPath = ctx.dbPath,
            params = mapOf(
                "depth" to ctx.transactionDepth.toString(),
                "success" to ctx.transactionSuccess.toString(),
            ),
        )
        if (ctx.transactionDepth > 0) ctx.transactionDepth--
        if (ctx.transactionDepth == 0) ctx.transactionSuccess = false
    }

    // endregion

    // region SQLiteCursor 被动采集(ADR-6)

    private fun installCursor(cursor: DatabaseMethodRegistry) {
        hookAfter(cursor.namedAny("moveToFirst", "moveToNext", "move", "moveToPosition")) {
            inv, result -> afterCursorMove(inv, result)
        }
        hookAfter(cursor.named("requery")) { inv, _ -> afterCursorRequery(inv) }
        hookAfter(cursor.named("close")) { inv, _ -> afterCursorClose(inv) }
    }

    /**
     * 游标钩子的**惰性补装**:注入期 `SQLiteCursor` 常尚未被加载(它要等第一次
     * 真正建游标才加载),所以「装一次失败就永久没有结果集采集」是死路 ——
     * 与 SSL 域 `installSslHooksFor` 同一病形。这里以**首次见到游标实例**为触发点:
     * `afterQuery` 已拿到 result,按实例真实类名补装(该类移动方法的声明类是 AbstractCursor,
     * 故一次补装覆盖全部游标类型)。
     *
     * 幂等靠 [cursorHooksInstalled];注册走 `engine.hook`,与本类注入期路径同源,
     * 且全进程只挂一次(V38:Vector 上同方法第二个 hook 静默不执行)。
     */
    private fun ensureCursorHooks(cursorClass: Class<*>) {
        if (cursorHooksInstalled) return
        cursorHooksInstalled = true
        val registry = DatabaseMethodRegistry.forName(cursorClass.name) ?: DatabaseMethodRegistry(cursorClass)
        installCursor(registry)
    }

    private fun afterCursorMove(inv: Invocation, result: Any?) {
        // V44 T2.2 门控先行:游标移动是查询迭代热路径(每行一次),旧形态先做
        // 类型转换/位置解析/缓冲查表,再在收集分支内判门控。
        if (!config().dbEnabled(SqlKind.QUERY)) return
        val cursor = inv.thisObject as? Cursor ?: return
        // moveToPosition(-1):回到未定位态,重置缓冲防重复行
        val position = (inv.getArg(0) as? Int) ?: -2
        if (position == -1) {
            SqlContextTracker.cursorBuffer(cursor).reset()
            return
        }
        if (result != true) return
        val buffer = SqlContextTracker.cursorBuffer(cursor)
        if (!buffer.collecting) return
        val row = collectRow(cursor, buffer) ?: return
        if (!buffer.addRow(row)) {
            flushQueryResult(cursor, buffer)
        }
    }

    private fun afterCursorRequery(inv: Invocation) {
        if (!config().dbEnabled(SqlKind.QUERY)) return
        val cursor = inv.thisObject as? Cursor ?: return
        SqlContextTracker.cursorBuffer(cursor).reset()
    }

    private fun afterCursorClose(inv: Invocation) {
        if (!config().dbEnabled(SqlKind.QUERY)) return
        val cursor = inv.thisObject as? Cursor ?: return
        val buffer = SqlContextTracker.cursorBuffer(cursor)
        flushQueryResult(cursor, buffer)
        SqlContextTracker.removeCursor(cursor)
    }

    private fun collectRow(cursor: Cursor, buffer: CursorBuffer?): SqlResultRow? {
        var names = buffer?.columnNames
        if (names == null) {
            names = runCatching { cursor.columnNames }.getOrNull()
            if (names != null) buffer?.columnNames = names
        }
        val cells = ArrayList<SqlResultCell>(names?.size ?: 0)
        for (i in 0 until cursor.columnCount) {
            val cell = runCatching {
                val name = runCatching { cursor.getColumnName(i) }.getOrNull() ?: "#$i"
                when (cursor.getType(i)) {
                    Cursor.FIELD_TYPE_INTEGER -> SqlResultCell(name, SqlValueType.L, v = cursor.getLong(i).toString())
                    Cursor.FIELD_TYPE_FLOAT -> SqlResultCell(name, SqlValueType.D, v = cursor.getDouble(i).toString())
                    Cursor.FIELD_TYPE_STRING -> SqlResultCell(name, SqlValueType.S, v = cursor.getString(i))
                    Cursor.FIELD_TYPE_BLOB -> {
                        val view = viewOf(cursor.getBlob(i))
                        SqlResultCell(
                            name, SqlValueType.B,
                            hex = view?.hex, len = view?.totalLength, truncated = view?.truncated ?: false,
                        )
                    }
                    else -> SqlResultCell(name, SqlValueType.N)
                }
            }.getOrNull() ?: return null
            cells.add(cell)
        }
        return SqlResultRow(cells)
    }

    private fun flushQueryResult(cursor: Cursor, buffer: CursorBuffer) {
        val db = buffer.db ?: return
        if (!config().dbEnabled(SqlKind.QUERY)) return
        val ctx = SqlContextTracker.contextFor(db)
        buffer.rowCount = buffer.rowCount ?: mCountOf(cursor)
        val params = (buffer.params ?: emptyMap()).toMutableMap()
        if (buffer.truncated) params["resultTruncated"] = "true"
        emit(
            operation = SqlOperation.QUERY_RESULT,
            databaseName = ctx.databaseName ?: "",
            contextId = SqlContextTracker.identity(db),
            provider = "SQLiteDatabase",
            dbPath = ctx.dbPath,
            sql = buffer.sql,
            rows = buffer.rows.toList(),
            rowCount = buffer.rowCount,
            params = params.takeIf { it.isNotEmpty() },
        )
    }

    /**
     * `mCount` 私有字段句柄按类缓存:旧形态每次游标行数探测
     * `getDeclaredField` + `setAccessible`(反射解析 + 权限检查),而该探测在
     * 查询注册与结果补发两处都调用。[com.lioisme.modev.hook.ClassValueCache] =
     * 每类一份、随类卸载回收(`null` 也缓存,缺失字段不反复付异常构造)。
     */
    private val mCountFields = com.lioisme.modev.hook.ClassValueCache<java.lang.reflect.Field?> { type ->
        runCatching { type.getDeclaredField("mCount").also { it.isAccessible = true } }.getOrNull()
    }

    /** 行数被动反射(mCount 私有字段;-1/失败置 null,不影响行数据采集)。 */
    private fun mCountOf(cursor: Cursor): Int? =
        runCatching {
            val f = mCountFields.get(cursor.javaClass) ?: return null
            val count = f.getInt(cursor)
            if (count < 0) null else count
        }.getOrNull()

    // endregion

    // region 通用工具

    /** 绑定参数序列化(String[] / Object[];blob 走双态视图)。 */
    private fun argsToBinds(args: Array<*>?): List<SqlBindValue>? {
        if (args == null) return null
        return args.mapIndexedNotNull { i, v -> valueToBind(i + 1, v) }
    }

    /** ContentValues 同构序列化(键序稳定;值按原始类型分派——getAs* 归一化会误判,如
     *  getAsBoolean("alice") 返回 false。原始值经 get 直取,类型精确)。 */
    private fun contentValuesToBinds(cv: ContentValues?): List<SqlBindValue>? {
        if (cv == null) return null
        val keys = runCatching { cv.keySet().toList() }.getOrNull() ?: return null
        val out = ArrayList<SqlBindValue>(keys.size)
        keys.forEachIndexed { i, k ->
            val raw = runCatching { cv.get(k) }.getOrNull()
            out.add(
                when (raw) {
                    is ByteArray -> valueToBind(i + 1, raw)!!
                    // 跨进程转换后 blob 为 ByteBuffer(可含 direct buffer,get 的
                    // array 拷贝会抛异常;hasArray 兜底手工读入)
                    is ByteBuffer -> {
                        val bytes =
                            if (raw.hasArray()) raw.array()
                            else {
                                val arr = ByteArray(raw.remaining())
                                raw.duplicate().get(arr)
                                arr
                            }
                        valueToBind(i + 1, bytes)!!
                    }
                    is String -> SqlBindValue(i + 1, SqlValueType.S, v = raw)
                    is Long, is Int, is Short, is Byte ->
                        SqlBindValue(i + 1, SqlValueType.L, v = (raw as Number).toString())
                    is Double, is Float ->
                        SqlBindValue(i + 1, SqlValueType.D, v = (raw as Number).toString())
                    is Boolean -> SqlBindValue(i + 1, SqlValueType.L, v = if (raw) "1" else "0")
                    else -> SqlBindValue(i + 1, SqlValueType.N)
                },
            )
        }
        return out
    }

    /** 查询绑参定位:rawQuery 系取首个数组(selectionArgs);query 系取第二个数组(首个为 columns)。 */
    private fun queryArgs(inv: Invocation): List<SqlBindValue>? {
        val params = inv.executable?.parameterTypes ?: return null
        val isRaw = inv.executable?.name?.startsWith("rawQuery") == true
        val arrays = params.indices.filter { params[it].isArray }
        val idx = arrays.getOrNull(if (isRaw) 0 else 1) ?: return null
        return argsToBinds(inv.getArg(idx) as? Array<*>)
    }

    /** 最后一个数组参数(update/delete 的 whereArgs;insert/replace 无数组返回 null)。 */
    private fun lastArrayArg(inv: Invocation): Array<*>? {
        val params = inv.executable?.parameterTypes ?: return null
        val idx = params.indices.lastOrNull { params[it].isArray } ?: return null
        return inv.getArg(idx) as? Array<*>
    }

    /** 最后一个 int 参数(insertWithOnConflict/updateWithOnConflict 的 conflictAlgorithm)。 */
    private fun lastIntArg(inv: Invocation): Int? {
        val params = inv.executable?.parameterTypes ?: return null
        val idx = params.indices.lastOrNull { params[it] == Int::class.javaPrimitiveType } ?: return null
        return inv.getArg(idx) as? Int
    }

    // endregion
}
