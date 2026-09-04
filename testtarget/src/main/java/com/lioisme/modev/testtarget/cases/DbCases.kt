package com.lioisme.modev.testtarget.cases

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.lioisme.modev.testtarget.Tt

/** L-DB-*:数据库监听触发器(SQLiteDatabase / SQLiteOpenHelper / SQLiteStatement / Cursor)。 */
object DbCases {

    private class Helper(ctx: Context, name: String) :
        SQLiteOpenHelper(ctx, name, null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE t_items(id INTEGER PRIMARY KEY, name TEXT, qty INTEGER)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    private fun freshDb(ctx: Context, tag: String): Pair<SQLiteDatabase, String> {
        val name = "tt_$tag.db"
        ctx.deleteDatabase(name)
        return ctx.openOrCreateDatabase(name, Context.MODE_PRIVATE, null) to name
    }

    fun run(ctx: Context, case: String) {
        when (case) {
            "L-DB-01" -> {
                val (db, name) = freshDb(ctx, "open")
                db.close()
                Tt.result(case, "db" to name)
            }
            "L-DB-02" -> {
                ctx.deleteDatabase("tt_helper.db")
                val h = Helper(ctx, "tt_helper.db")
                val db = h.writableDatabase
                db.close()
                h.close()
                Tt.result(case, "helper" to "ok")
            }
            "L-DB-03" -> {
                val (db, _) = freshDb(ctx, "query")
                db.execSQL("CREATE TABLE t(id INTEGER PRIMARY KEY, name TEXT)")
                db.execSQL("INSERT INTO t(name) VALUES('alpha'),('beta'),('gamma')")
                val c = db.query("t", null, null, null, null, null, null)
                val n = c.count
                c.close()
                db.close()
                Tt.result(case, "rows" to n.toString())
            }
            "L-DB-04" -> {
                val (db, _) = freshDb(ctx, "raw")
                db.execSQL("CREATE TABLE t(id INTEGER PRIMARY KEY, name TEXT, note TEXT)")
                db.execSQL("INSERT INTO t(name,note) VALUES('a',NULL),('b','x')")
                val c = db.rawQuery("SELECT id,name,note FROM t WHERE name != ?", arrayOf("zzz"))
                while (c.moveToNext()) { /* 被动采集:App 自身迭代 */ }
                c.close()
                db.close()
                Tt.result(case, "iterated" to "true")
            }
            "L-DB-05" -> {
                val (db, _) = freshDb(ctx, "cursor")
                db.execSQL("CREATE TABLE t(id INTEGER PRIMARY KEY, v TEXT)")
                for (i in 1..5) db.execSQL("INSERT INTO t(v) VALUES('v$i')")
                val c: Cursor = db.rawQuery("SELECT id,v FROM t ORDER BY id", null)
                var first = false
                var count = 0
                if (c.moveToFirst()) {
                    do { count++ } while (c.moveToNext())
                    first = true
                }
                c.close()
                db.close()
                Tt.result(case, "first" to first.toString(), "count" to count.toString())
            }
            "L-DB-06" -> {
                val (db, _) = freshDb(ctx, "dml")
                db.execSQL("CREATE TABLE t(id INTEGER PRIMARY KEY, name TEXT, qty INTEGER)")
                db.insert("t", null, ContentValues().apply {
                    put("name", "ins"); put("qty", 1)
                })
                db.replace("t", null, ContentValues().apply {
                    put("id", 1); put("name", "rep"); put("qty", 2)
                })
                db.update("t", ContentValues().apply { put("qty", 9) }, "id=?", arrayOf("1"))
                db.delete("t", "id=?", arrayOf("1"))
                db.close()
                Tt.result(case, "dml" to "4")
            }
            "L-DB-07" -> {
                val (db, _) = freshDb(ctx, "exec")
                db.execSQL("CREATE TABLE t(id INTEGER PRIMARY KEY, v TEXT)")
                db.execSQL("INSERT INTO t(v) VALUES(?)", arrayOf("bound"))
                db.close()
                Tt.result(case, "exec" to "ok")
            }
            "L-DB-08" -> {
                val (db, _) = freshDb(ctx, "stmt")
                db.execSQL("CREATE TABLE t(id INTEGER PRIMARY KEY, v TEXT)")
                val s = db.compileStatement("INSERT INTO t(v) VALUES(?)")
                s.bindString(1, "stmt-ins")
                s.executeInsert()
                val u = db.compileStatement("UPDATE t SET v=? WHERE id=?")
                u.bindString(1, "stmt-upd")
                u.bindLong(2, 1)
                u.executeUpdateDelete()
                db.close()
                Tt.result(case, "statement" to "ok")
            }
            "L-DB-09" -> {
                val (db, _) = freshDb(ctx, "tx")
                db.execSQL("CREATE TABLE t(id INTEGER PRIMARY KEY, v TEXT)")
                db.beginTransaction()
                try {
                    db.execSQL("INSERT INTO t(v) VALUES('tx1')")
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
                db.close()
                Tt.result(case, "tx" to "ok")
            }
            "L-DB-11" -> {
                // C-GATE-01 复用:大结果集(200 行 × ~60 字符)触发行数/字节双闸门截断
                val (db, _) = freshDb(ctx, "big")
                db.execSQL("CREATE TABLE t(id INTEGER PRIMARY KEY, v TEXT)")
                db.beginTransaction()
                val pad = "x".repeat(60)
                try {
                    for (i in 1..200) db.execSQL("INSERT INTO t(v) VALUES('$pad$i')")
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
                val c = db.rawQuery("SELECT id,v FROM t", null)
                while (c.moveToNext()) { /* 迭代全部 */ }
                val n = c.count
                c.close()
                db.close()
                Tt.result(case, "rows" to n.toString())
            }
            "L-DB-12" -> {
                val (db, _) = freshDb(ctx, "close")
                db.close()
                Tt.result(case, "closed" to "true")
            }
            else -> Tt.result(case, "error" to "unknown-case")
        }
    }
}
