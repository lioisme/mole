package com.lioisme.modev.hook

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteCursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteStatement
import com.lioisme.modev.core.model.HookSwitch
import com.lioisme.modev.core.model.MonitoredRecord
import com.lioisme.modev.core.model.RecordTypes
import com.lioisme.modev.core.model.SqlKind
import com.lioisme.modev.core.model.SqlOperation
import com.lioisme.modev.core.model.SqlRecord
import com.lioisme.modev.hook.probes.DatabaseProbe
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Executable
import java.lang.reflect.Method
import java.util.Collections

/**
 * 注入侧数据库监听集成测试(T14):FakePlatform 捕获注册的 MethodHook,
 * 手动按调用时序驱动真实 SQLiteDatabase(SQLiteCursor/SQLiteStatement 同为 Robolectric
 * 真实实现),验证 open→insert→query→close 全链产出、事务深度、语句绑定、
 * 游标被动采集与开关关闭态零产出。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SqlDatabaseIntegrationTest {

    private lateinit var platform: FakePlatform
    private lateinit var sink: RecordSink
    private lateinit var captured: MutableList<MonitoredRecord>
    private lateinit var configVar: HookSwitch
    private lateinit var db: SQLiteDatabase
    private lateinit var dbPath: String
    private lateinit var openRecord: SqlRecord

    @Before
    fun setUp() {
        configVar = HookSwitch()
        platform = FakePlatform()
        captured = Collections.synchronizedList(ArrayList())
        sink = RecordSink(
            jsonOf = { record ->
                captured.add(record)
                "{}"
            },
            sendJson = { true },
            isConnected = { true },
        )
        sink.start()
        val context = ProbeContext(
            engine = HookEngine(platform),
            config = { configVar },
            tracker = CryptoContextTracker,
            sink = sink,
            packageName = "com.test.target",
        )
        DatabaseProbe().install(context)
        dbPath = org.robolectric.RuntimeEnvironment.getApplication().getDatabasePath("t14.db").absolutePath
        db = SQLiteDatabase.openOrCreateDatabase(dbPath, null)
        db.execSQL("CREATE TABLE t (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, val INTEGER, data BLOB)")
        fireAfter(method(SQLiteDatabase::class.java, "openOrCreateDatabase", String::class.java, SQLiteDatabase.CursorFactory::class.java), null, arrayOf(dbPath, null), db)
        // 等待 OPEN_DATABASE 记录落定:存入字段供 open 用例断言,其余用例断言前清空防异步到达污染
        openRecord = awaitSql(1).single() as SqlRecord
        captured.clear()
    }

    @After
    fun tearDown() {
        runCatching { db.close() }
    }

    // region 生命周期与查询

    @Test
    fun `open 产出 OPEN_DATABASE 且库名路径正确`() {
        val record = openRecord

        assertEquals(RecordTypes.DATABASE, record.recordType)
        assertEquals(SqlOperation.OPEN_DATABASE, record.operation)
        assertEquals("t14.db", record.databaseName)
        assertEquals(dbPath, record.dbPath)
        assertEquals("SQLiteDatabase", record.provider)
        assertEquals("com.test.target", record.packageName)
    }

    @Test
    fun `insert 产出 INSERT 记录含还原 SQL 绑参与 rowId`() {
        val cv = ContentValues().apply {
            put("name", "alice")
            put("val", 42)
            put("data", byteArrayOf(1, 2, 3))
        }
        val rowId = db.insert("t", null, cv)
        val method = method(SQLiteDatabase::class.java, "insert", String::class.java, String::class.java, ContentValues::class.java)
        fireAfter(method, db, arrayOf("t", null, cv), rowId)

        val record = awaitSql(1).single() as SqlRecord
        assertEquals(SqlOperation.INSERT, record.operation)
        assertEquals(SqlKind.DML, record.kind)
        assertEquals("t", record.params?.get("table"))
        assertTrue(record.sql.orEmpty().startsWith("INSERT INTO t ("))
        assertEquals(rowId, record.rowId)
        val binds = record.bindArgs.orEmpty()
        assertEquals(3, binds.size)
        // ContentValues.keySet 顺序与插入顺序无契约;blob 绑定的 v 恒 null(hex 视图),
        // 按值多重集断言时排除 B 型
        assertEquals(setOf("alice", "42"), binds.filter { it.t.name != "B" }.map { it.v }.toSet())
        val blob = binds.single { it.t.name == "B" }
        assertEquals("010203", blob.hex)
        assertEquals(3, blob.len)
    }

    @Test
    fun `rawQuery 全链产出 QUERY 与游标迭代后的 QUERY_RESULT`() {
        db.execSQL("INSERT INTO t (name, val) VALUES ('a', 1), ('b', 2)")
        val cursor = db.rawQuery("SELECT id, name FROM t ORDER BY id", null)
        val qm = method(SQLiteDatabase::class.java, "rawQuery", String::class.java, Array<String>::class.java)
        fireAfter(qm, db, arrayOf("SELECT id, name FROM t ORDER BY id", null), cursor)

        val query = awaitSql(1).single() as SqlRecord
        assertEquals(SqlOperation.RAW_QUERY, query.operation)
        assertEquals("SELECT id, name FROM t ORDER BY id", query.sql)

        val fm = method(SQLiteCursor::class.java, "moveToFirst")
        val nm = method(SQLiteCursor::class.java, "moveToNext")
        assertTrue(cursor.moveToFirst())
        fireAfter(fm, cursor, emptyArray(), true)
        assertTrue(cursor.moveToNext())
        fireAfter(nm, cursor, emptyArray(), true)
        assertFalse(cursor.moveToNext())
        fireAfter(nm, cursor, emptyArray(), false)

        cursor.close()
        fireAfter(method(SQLiteCursor::class.java, "close"), cursor, emptyArray(), null)

        val result = awaitSql(2)[1] as SqlRecord
        assertEquals(SqlOperation.QUERY_RESULT, result.operation)
        assertEquals(query.contextId, result.contextId)
        assertEquals(2, result.rows?.size)
        // mCount 反射在 Robolectric 恒为 -1 → null(真机物化后为正数);宽松断言
        assertTrue(result.rowCount == null || result.rowCount == 2)
        assertEquals("id", result.rows!![0].c[0].n)
        assertEquals("1", result.rows!![0].c[0].v)
        assertEquals("a", result.rows!![0].c[1].v)
        assertEquals("2", result.rows!![1].c[0].v)
    }

    @Test
    fun `query 重建 SELECT SQL 且绑参定位正确`() {
        db.execSQL("INSERT INTO t (name, val) VALUES ('x', 9)")
        val cursor = db.query("t", arrayOf("id", "name"), "val = ?", arrayOf("9"), null, null, "id DESC")
        val qm = method(
            SQLiteDatabase::class.java, "query",
            String::class.java, Array<String>::class.java, String::class.java,
            Array<String>::class.java, String::class.java, String::class.java, String::class.java,
        )
        fireAfter(qm, db, arrayOf("t", arrayOf("id", "name"), "val = ?", arrayOf("9"), null, null, "id DESC"), cursor)

        val record = awaitSql(1).single() as SqlRecord
        assertEquals(SqlOperation.QUERY, record.operation)
        assertTrue(record.sql.orEmpty().contains("SELECT id,name FROM t"))
        assertTrue(record.sql.orEmpty().contains("WHERE val = ?"))
        assertTrue(record.sql.orEmpty().contains("ORDER BY id DESC"))
        assertEquals("t", record.params?.get("table"))
        val binds = record.bindArgs.orEmpty()
        assertEquals(1, binds.size)
        assertEquals("9", binds[0].v)
        cursor.close()
        fireAfter(method(SQLiteCursor::class.java, "close"), cursor, emptyArray(), null)
    }

    // endregion

    // region 事务

    @Test
    fun `事务深度与成功标记全链产出`() {
        val begin = method(SQLiteDatabase::class.java, "beginTransaction")
        fireBefore(begin, db, emptyArray())
        db.beginTransaction()

        val success = method(SQLiteDatabase::class.java, "setTransactionSuccessful")
        db.setTransactionSuccessful()
        fireAfter(success, db, emptyArray(), null)

        val end = method(SQLiteDatabase::class.java, "endTransaction")
        db.endTransaction()
        fireAfter(end, db, emptyArray(), null)

        val records = awaitSql(3)
        val beginRecord = records[0] as SqlRecord
        val setRecord = records[1] as SqlRecord
        val endRecord = records[2] as SqlRecord

        assertEquals(SqlOperation.BEGIN_TRANSACTION, beginRecord.operation)
        assertEquals(SqlKind.TRANSACTION, beginRecord.kind)
        assertEquals("1", beginRecord.params?.get("depth"))
        assertEquals(SqlOperation.SET_TRANSACTION_SUCCESSFUL, setRecord.operation)
        assertEquals(SqlOperation.END_TRANSACTION, endRecord.operation)
        assertEquals("1", endRecord.params?.get("depth"))
        assertEquals("true", endRecord.params?.get("success"))
    }

    // endregion

    // region 预编译语句

    @Test
    fun `compileStatement bind executeInsert 绑定快照入记录`() {
        val stmt = db.compileStatement("INSERT INTO t (name, val) VALUES (?, ?)")
        val cm = method(SQLiteDatabase::class.java, "compileStatement", String::class.java)
        fireAfter(cm, db, arrayOf("INSERT INTO t (name, val) VALUES (?, ?)"), stmt)

        stmt.bindString(1, "bob")
        fireAfter(method(SQLiteStatement::class.java, "bindString", Integer.TYPE, String::class.java), stmt, arrayOf(1, "bob"), null)
        stmt.bindLong(2, 7L)
        fireAfter(method(SQLiteStatement::class.java, "bindLong", Integer.TYPE, java.lang.Long.TYPE), stmt, arrayOf(2, 7L), null)

        val rowId = stmt.executeInsert()
        fireAfter(method(SQLiteStatement::class.java, "executeInsert"), stmt, emptyArray(), rowId)

        val record = awaitSql(1).single() as SqlRecord
        assertEquals(SqlOperation.STATEMENT_EXECUTE_INSERT, record.operation)
        assertEquals(rowId, record.rowId)
        assertEquals("INSERT INTO t (name, val) VALUES (?, ?)", record.sql)
        val binds = record.bindArgs.orEmpty()
        assertEquals(2, binds.size)
        assertEquals("bob", binds[0].v)
        assertEquals("7", binds[1].v)
        assertEquals(SqlKind.EXEC, record.kind)
    }

    // endregion

    // region 关闭与开关

    @Test
    fun `update 与 delete 产出受影响行数且 whereArgs 入绑定`() {
        db.execSQL("INSERT INTO t (name, val) VALUES ('a', 1), ('b', 1)")

        val updated = db.update("t", ContentValues().apply { put("val", 2) }, "val = ?", arrayOf("1"))
        fireAfter(
            method(SQLiteDatabase::class.java, "update", String::class.java, ContentValues::class.java, String::class.java, Array<String>::class.java),
            db, arrayOf("t", ContentValues().apply { put("val", 2) }, "val = ?", arrayOf("1")), updated,
        )
        val update = awaitSql(1).single() as SqlRecord
        assertEquals(SqlOperation.UPDATE, update.operation)
        assertEquals(2, update.affectedCount)
        // 绑定序 = valueBinds 在前、whereArgs 在后
        assertEquals("2", update.bindArgs.orEmpty()[0].v)
        assertEquals("1", update.bindArgs.orEmpty()[1].v)

        val deleted = db.delete("t", "val = ?", arrayOf("2"))
        fireAfter(
            method(SQLiteDatabase::class.java, "delete", String::class.java, String::class.java, Array<String>::class.java),
            db, arrayOf("t", "val = ?", arrayOf("2")), deleted,
        )
        val delete = awaitSql(2)[1] as SqlRecord
        assertEquals(SqlOperation.DELETE, delete.operation)
        assertEquals(2, delete.affectedCount)
        assertEquals("2", delete.bindArgs.orEmpty()[0].v)
    }

    @Test
    fun `close 产出 CLOSE_DATABASE`() {
        db.close()
        fireAfter(method(SQLiteDatabase::class.java, "close"), db, emptyArray(), null)

        val record = awaitSql(1).single() as SqlRecord
        assertEquals(SqlOperation.CLOSE_DATABASE, record.operation)
    }

    @Test
    fun `db 开关全关时零产出`() {
        configVar = HookSwitch(
            dbOpen = false, dbQuery = false, dbDml = false,
            dbExec = false, dbTransaction = false,
        )
        val cv = ContentValues().apply { put("name", "nobody") }
        db.insert("t", null, cv)
        fireAfter(method(SQLiteDatabase::class.java, "insert", String::class.java, String::class.java, ContentValues::class.java), db, arrayOf("t", null, cv), 1L)

        Thread.sleep(300)
        assertEquals(0, captured.size)
    }

    // endregion

    // region 工具

    private fun method(clazz: Class<*>, name: String, vararg params: Class<*>): Method =
        clazz.getMethod(name, *params)

    private fun fireAfter(method: Method, thisObject: Any?, args: Array<Any?>, result: Any?) {
        platform.fireAfter(method, thisObject, args, result)
    }

    private fun fireBefore(method: Method, thisObject: Any?, args: Array<Any?>) {
        platform.fireBefore(method, thisObject, args)
    }

    private fun awaitSql(count: Int): List<MonitoredRecord> {
        val deadline = System.currentTimeMillis() + 5000
        while (captured.count { it is SqlRecord } < count && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
        }
        val records = captured.filterIsInstance<SqlRecord>().take(count)
        assertEquals(count, records.size)
        return records
    }

    // endregion
}

private class FakePlatform : Platform {

    private val registrations = LinkedHashMap<Executable, MutableList<MethodHook>>()

    override fun register(executable: Executable?, hook: MethodHook): Boolean {
        if (executable == null) return false
        registrations.getOrPut(executable) { mutableListOf() }.add(hook)
        return true
    }

    override fun log(message: String, throwable: Throwable?) {}

    fun fireAfter(method: Executable, thisObject: Any?, args: Array<Any?>, result: Any?) {
        registrations[method]?.forEach { hook ->
            hook.after(SimpleInvocation(method, thisObject, args, result))
        }
    }

    fun fireBefore(method: Executable, thisObject: Any?, args: Array<Any?>) {
        registrations[method]?.forEach { hook ->
            hook.before(SimpleInvocation(method, thisObject, args, null))
        }
    }
}

private class SimpleInvocation(
    override val executable: Executable?,
    override val thisObject: Any?,
    private val args: Array<Any?>,
    override val result: Any?,
) : Invocation() {

    override val argCount: Int get() = args.size

    override fun getArgUnsafe(index: Int): Any? = args[index]
}