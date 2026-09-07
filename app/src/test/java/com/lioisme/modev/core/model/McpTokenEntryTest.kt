package com.lioisme.modev.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MCP 令牌条目(V39,JVM 直测):值形态(32 位小写 hex)、唯一性、条目装配。
 */
class McpTokenEntryTest {

    @Test
    fun `generated value is 32 lowercase hex chars`() {
        repeat(32) {
            val value = McpTokenEntry.generateValue()
            assertEquals(32, value.length)
            assertTrue("hex only: $value", value.all { c -> c in '0'..'9' || c in 'a'..'f' })
        }
    }

    @Test
    fun `generated values are unique`() {
        val values = List(256) { McpTokenEntry.generateValue() }.toSet()
        assertEquals(256, values.size)
    }

    @Test
    fun `create assembles id label and timestamp`() {
        val before = System.currentTimeMillis()
        val entry = McpTokenEntry.create(label = "work")
        val after = System.currentTimeMillis()

        assertEquals("work", entry.label)
        assertEquals(32, entry.value.length)
        assertTrue(entry.createdAt in before..after)
        assertNotEquals(entry.id, McpTokenEntry.create(label = "work").id)
    }
}
