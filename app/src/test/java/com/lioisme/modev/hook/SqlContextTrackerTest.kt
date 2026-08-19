package com.lioisme.modev.hook

import com.lioisme.modev.core.model.SqlBindValue
import com.lioisme.modev.core.model.SqlResultCell
import com.lioisme.modev.core.model.SqlResultRow
import com.lioisme.modev.core.model.SqlValueType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SqlContextTracker 契约测试(T5,ADR-5/6/7):
 * 实例 ID 唯一稳定、事务深度增减、绑定暂存/清空、游标缓冲重置与双闸门截断。
 * 纯 JVM(不依赖框架类)。
 */
class SqlContextTrackerTest {

    @Test
    fun `实例 ID 唯一且稳定`() {
        val a = Any()
        val b = Any()

        val idA1 = SqlContextTracker.identity(a)
        val idA2 = SqlContextTracker.identity(a)
        val idB = SqlContextTracker.identity(b)

        assertEquals(idA1, idA2)
        assertNotEquals(idA1, idB)
    }

    @Test
    fun `removeDb 后实例上下文重建为新 ID`() {
        val db = Any()
        val first = SqlContextTracker.identity(db)
        SqlContextTracker.removeDb(db)

        assertNotEquals(first, SqlContextTracker.identity(db))
    }

    @Test
    fun `事务深度随 begin 增减`() {
        val db = Any()
        val ctx = SqlContextTracker.contextFor(db)

        assertEquals(0, ctx.transactionDepth)
        ctx.transactionDepth++
        ctx.transactionDepth++
        assertEquals(2, ctx.transactionDepth)
        ctx.transactionDepth--
        assertEquals(1, ctx.transactionDepth)
    }

    @Test
    fun `绑定暂存写入读取与清空`() {
        val stmt = Any()
        val binds = SqlContextTracker.bindsOf(stmt)

        binds[1] = SqlBindValue(1, SqlValueType.L, v = "42")
        binds[2] = SqlBindValue(2, SqlValueType.S, v = "hello")

        assertEquals("42", SqlContextTracker.bindsOf(stmt)[1]?.v)
        assertEquals("hello", SqlContextTracker.bindsOf(stmt)[2]?.v)
        assertNull(SqlContextTracker.bindsOf(stmt)[3])

        SqlContextTracker.clearBinds(stmt)
        assertTrue(SqlContextTracker.bindsOf(stmt).isEmpty())
    }

    @Test
    fun `语句与 DB 实例关联及 SQL 登记`() {
        val stmt = Any()
        val db = Any()

        SqlContextTracker.linkStatement(stmt, db)
        SqlContextTracker.linkSql(stmt, "SELECT 1")

        assertEquals(db, SqlContextTracker.dbOf(stmt))
        assertEquals("SELECT 1", SqlContextTracker.sqlOf(stmt))
    }

    @Test
    fun `游标缓冲重置保留注册信息且清空采集态`() {
        val cursor = Any()
        val buffer = SqlContextTracker.cursorBuffer(cursor)
        val db = Any()

        buffer.db = db
        buffer.sql = "SELECT * FROM t"
        buffer.maxRows = 100
        buffer.maxChars = 1024
        buffer.rows.add(SqlResultRow(listOf(SqlResultCell("a", SqlValueType.L, v = "1"))))
        buffer.rowCount = 5
        buffer.columnNames = arrayOf("a")
        buffer.truncated = true

        buffer.reset()

        assertTrue(buffer.rows.isEmpty())
        assertNull(buffer.rowCount)
        assertNull(buffer.columnNames)
        assertFalse(buffer.truncated)
        assertTrue(buffer.collecting)
        // 注册信息保留(随游标弱引用生命周期)
        assertEquals(db, buffer.db)
        assertEquals("SELECT * FROM t", buffer.sql)
        assertEquals(100, buffer.maxRows)
    }

    @Test
    fun `行缓冲字符闸门超限截断`() {
        val buffer = CursorBuffer()
        buffer.maxRows = 100
        buffer.maxChars = 20

        // 单行约 8 + "abcdefghijkl"(12) + 8 = 28 ≥ 20 → 追加后即截断
        val row = SqlResultRow(listOf(SqlResultCell("c", SqlValueType.S, v = "abcdefghijkl")))
        val still = buffer.addRow(row)

        assertFalse(still)
        assertTrue(buffer.truncated)
        assertFalse(buffer.collecting)
        assertEquals(1, buffer.rows.size)
    }

    @Test
    fun `行缓冲行数闸门超限截断`() {
        val buffer = CursorBuffer()
        buffer.maxRows = 2
        buffer.maxChars = 1_000_000

        assertTrue(buffer.addRow(SqlResultRow(emptyList())))
        assertFalse(buffer.addRow(SqlResultRow(emptyList())))

        assertTrue(buffer.truncated)
        assertFalse(buffer.collecting)
        assertEquals(2, buffer.rows.size)
    }

    @Test
    fun `未设上限的缓冲不截断`() {
        val buffer = CursorBuffer()
        // 默认 maxRows/maxChars = 0 → collecting 恒 false(上限未配置视为不可采集)
        assertFalse(buffer.collecting)
    }

    @Test
    fun `contextFor 自动注册并保持同一上下文`() {
        val db = Any()
        val ctx1 = SqlContextTracker.contextFor(db)
        val ctx2 = SqlContextTracker.contextFor(db)

        assertNotNull(ctx1)
        assertEquals(ctx1, ctx2)
    }
}