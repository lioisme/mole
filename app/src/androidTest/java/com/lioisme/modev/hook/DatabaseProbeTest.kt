package com.lioisme.modev.hook

import android.content.ContentValues
import android.database.sqlite.SQLiteCursor
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lioisme.modev.core.model.HookSwitch
import com.lioisme.modev.core.model.MonitoredRecord
import com.lioisme.modev.core.model.RecordTypes
import com.lioisme.modev.core.model.SqlKind
import com.lioisme.modev.core.model.SqlOperation
import com.lioisme.modev.core.model.SqlRecord
import com.lioisme.modev.hook.probes.DatabaseProbe
import java.io.File
import java.lang.reflect.Executable
import java.lang.reflect.Method
import java.util.Collections
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 数据库监听真机端到端(T15):宿主进程内以 DatabaseProbe 装配三 Hooker
 * (FakePlatform 捕获注册,手动按调用时序驱动真实 SQLiteDatabase 原生实现),
 * 验证 open→insert→query 迭代→事务全链产出、行数/上下文关联、开关关闭零产出。
 * 与 T14 Robolectric 集成测试同源口径,真机差异点:mCount 反射为正数(真实物化)。
 */
@RunWith(AndroidJUnit4::class)
class DatabaseProbeTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var platform: FakePlatform
    private lateinit var sink: RecordSink
    private lateinit var captured: MutableList<MonitoredRecord>
    private lateinit var configVar: HookSwitch
    private lateinit var db: SQLiteDatabase
    private lateinit var dbPath: String
    private lateinit var dbFile: File
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
        val probeContext = ProbeContext(
            engine = HookEngine(platform),
            config = { configVar },
            tracker = CryptoContextTracker,
            sink = sink,
            packageName = "com.lioisme.modev.test",
        )
        DatabaseProbe().install(probeContext)
        dbFile = context.getDatabasePath("t15.db")
        dbFile.parentFile?.mkdirs()
        dbPath = dbFile.absolutePath
        db = SQLiteDatabase.openOrCreateDatabase(dbPath, null)
        db.execSQL("CREATE TABLE t (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, val INTEGER)")
        fireAfter(
            method(SQLiteDatabase::class.java, "openOrCreateDatabase", String::class.java, SQLiteDatabase.CursorFactory::class.java),
            null, arrayOf(dbPath, null), db,
        )
        // 等待 OPEN_DATABASE 落定并留存供断言,其余用例断言前清空防异步到达污染
        openRecord = awaitSql(1).single() as SqlRecord
        captured.clear()
    }

    @After
    fun tearDown() {
        runCatching { db.close() }
        runCatching { dbFile.delete() }
        runCatching { File("$dbPath-journal").delete() }
        runCatching { File("$dbPath-wal").delete() }
        runCatching { File("$dbPath-shm").delete() }
    }

    @Test
    fun open_insert_query_iteration_transaction_full_chain() {
        // open(留存字段,setUp 已等待落定)
        val open = openRecord
        assertEquals(RecordTypes.DATABASE, open.recordType)
        assertEquals(SqlOperation.OPEN_DATABASE, open.operation)
        assertEquals("t15.db", open.databaseName)
        assertEquals(dbPath, open.dbPath)
        assertEquals("SQLiteDatabase", open.provider)

        // DML:insert(ContentValues 还原 SQL + 绑参 + rowId)
        val cv = ContentValues().apply {
            put("name", "alice")
            put("val", 42)
        }
        val rowId = db.insert("t", null, cv)
        fireAfter(
            method(SQLiteDatabase::class.java, "insert", String::class.java, String::class.java, ContentValues::class.java),
            db, arrayOf("t", null, cv), rowId,
        )
        val insert = awaitSql(1)[0] as SqlRecord
        assertEquals(SqlOperation.INSERT, insert.operation)
        assertEquals(SqlKind.DML, insert.kind)
        assertTrue(insert.sql.orEmpty().startsWith("INSERT INTO t ("))
        assertEquals(rowId, insert.rowId)
        val binds = insert.bindArgs.orEmpty()
        assertEquals(2, binds.size)
        assertEquals(setOf("alice", "42"), binds.map { it.v }.toSet())

        // query + 游标被动采集(逐行迭代后补发 QUERY_RESULT)
        val cursor = db.rawQuery("SELECT id, name FROM t ORDER BY id", null)
        fireAfter(
            method(SQLiteDatabase::class.java, "rawQuery", String::class.java, Array<String>::class.java),
            db, arrayOf("SELECT id, name FROM t ORDER BY id", null), cursor,
        )
        val query = awaitSql(2)[1] as SqlRecord
        assertEquals(SqlOperation.RAW_QUERY, query.operation)
        assertEquals("SELECT id, name FROM t ORDER BY id", query.sql)

        assertTrue(cursor.moveToFirst())
        fireAfter(method(SQLiteCursor::class.java, "moveToFirst"), cursor, emptyArray(), true)
        assertFalse(cursor.moveToNext())
        fireAfter(method(SQLiteCursor::class.java, "moveToNext"), cursor, emptyArray(), false)
        // QUERY_RESULT 在游标 close 时补发(ADR-6)
        cursor.close()
        fireAfter(method(SQLiteCursor::class.java, "close"), cursor, emptyArray(), null)

        val result = awaitSql(3)[2] as SqlRecord
        assertEquals(SqlOperation.QUERY_RESULT, result.operation)
        assertEquals(query.contextId, result.contextId)
        assertEquals(1, result.rows?.size)
        // 真机游标物化后 mCount 反射为正数(宽松断言与 Robolectric 口径一致)
        assertTrue(result.rowCount == null || result.rowCount == 1)
        assertEquals("id", result.rows!![0].c[0].n)
        assertEquals(rowId.toString(), result.rows!![0].c[0].v)
        assertEquals("alice", result.rows!![0].c[1].v)

        // 事务:深度与成功标记
        fireBefore(method(SQLiteDatabase::class.java, "beginTransaction"), db, emptyArray())
        db.beginTransaction()
        db.setTransactionSuccessful()
        fireAfter(method(SQLiteDatabase::class.java, "setTransactionSuccessful"), db, emptyArray(), null)
        db.endTransaction()
        fireAfter(method(SQLiteDatabase::class.java, "endTransaction"), db, emptyArray(), null)

        val tx = awaitSql(6)
        assertEquals(SqlOperation.BEGIN_TRANSACTION, (tx[3] as SqlRecord).operation)
        assertEquals("1", (tx[3] as SqlRecord).params?.get("depth"))
        assertEquals(SqlOperation.SET_TRANSACTION_SUCCESSFUL, (tx[4] as SqlRecord).operation)
        assertEquals(SqlOperation.END_TRANSACTION, (tx[5] as SqlRecord).operation)
        assertEquals("true", (tx[5] as SqlRecord).params?.get("success"))
    }

    @Test
    fun db_switches_off_emits_nothing() {
        configVar = HookSwitch(
            dbOpen = false, dbQuery = false, dbDml = false,
            dbExec = false, dbTransaction = false,
        )
        val cv = ContentValues().apply { put("name", "nobody") }
        db.insert("t", null, cv)
        fireAfter(
            method(SQLiteDatabase::class.java, "insert", String::class.java, String::class.java, ContentValues::class.java),
            db, arrayOf("t", null, cv), 1L,
        )
        Thread.sleep(500)
        assertEquals(0, captured.size)
    }

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