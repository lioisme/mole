package com.lioisme.modev.ui.detail

import com.lioisme.modev.core.model.SqlBindValue
import com.lioisme.modev.core.model.SqlResultCell
import com.lioisme.modev.core.model.SqlResultRow
import com.lioisme.modev.core.model.SqlValueType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V28 D3 JSON 发射器契约(纯 JVM):params / bindArgs / rows 以真实 JSON 承载,
 * 序列化经 Gson(转义正确性由库保证),护栏计数与文本版同规。
 */
class ParamsJsonTest {

    // ---- formatParamsJson ----

    @Test
    fun params_json_empty_map_yields_null_document() {
        val result = formatParamsJson(emptyMap())
        assertNull(result.json)
        assertEquals(0, result.omittedEntries)
        assertEquals(0, result.truncatedValues)
    }

    @Test
    fun params_json_preserves_order_and_escapes() {
        val json = formatParamsJson(linkedMapOf("z" to "1", "a" to "line\nbreak\"q")).json!!
        // 插入序保持(JsonObject 底层 LinkedTreeMap)
        assertTrue(json.indexOf("\"z\"") < json.indexOf("\"a\""))
        // Gson 转义正确
        assertTrue(json.contains("line\\nbreak\\\"q"))
    }

    @Test
    fun params_json_value_truncation_counts() {
        val big = "x".repeat(PARAMS_MAX_VALUE_CHARS + 1)
        val result = formatParamsJson(mapOf("k" to big, "ok" to "v"))
        assertTrue(result.json!!.contains("…" ))
        assertEquals(1, result.truncatedValues)
        assertEquals(0, result.omittedEntries)
    }

    @Test
    fun params_json_entry_cap_counts_omitted() {
        val params = (1..PARAMS_MAX_ENTRIES + 5).associate { "k$it" to "v" }
        val result = formatParamsJson(params)
        assertEquals(PARAMS_MAX_ENTRIES + 5, params.size)
        assertEquals(5, result.omittedEntries)
    }

    // ---- formatBindArgsJson ----

    @Test
    fun bind_args_json_array_shape_and_null_semantics() {
        val json =
            formatBindArgsJson(
                listOf(
                    SqlBindValue(i = 1, t = SqlValueType.L, v = "42"),
                    SqlBindValue(i = 2, t = SqlValueType.S, v = "he\"llo"),
                    SqlBindValue(i = 3, t = SqlValueType.N),
                ),
            ).json!!
        assertTrue(json.trimStart().startsWith("["))
        assertTrue(json.contains("\"index\": 1") || json.contains("\"index\":1"))
        assertTrue(json.contains("\"type\": \"L\"") || json.contains("\"type\":\"L\""))
        assertTrue(json.contains("he\\\"llo"))
        // NULL → JSON null(非字符串 "NULL")
        assertTrue("actual=<" + json + ">", json.contains(": null"))
    }

    @Test
    fun bind_args_json_row_cap_counts_omitted() {
        val args = (1..BIND_ARGS_MAX_ROWS + 3).map { SqlBindValue(i = it, t = SqlValueType.S, v = "v") }
        val result = formatBindArgsJson(args)
        assertEquals(3, result.omittedRows)
    }

    // ---- formatRowsNdjson ----

    @Test
    fun rows_ndjson_each_line_is_independent_object() {
        val rows =
            listOf(
                SqlResultRow(c = listOf(SqlResultCell(n = "id", t = SqlValueType.L, v = "1"), SqlResultCell(n = "name", t = SqlValueType.S, v = "a,b"))),
                SqlResultRow(c = listOf(SqlResultCell(n = "id", t = SqlValueType.N))),
            )
        val result = formatRowsNdjson(rows)
        assertEquals(2, result.lines.size)
        val second = result.lines[1]
        // 每行独立可解析(NDJSON);NULL 单元格 → null 值
        assertTrue(second.contains("\"id\""))
        assertTrue("actual=<" + second + ">", second.contains(": null"))
        assertEquals(0, result.omittedRows)
    }

    @Test
    fun rows_ndjson_row_and_col_caps() {
        val wideRow = SqlResultRow(c = (1..RESULT_MAX_COLS + 2).map { SqlResultCell(n = "c$it", t = SqlValueType.L, v = "$it") })
        val rows = (1..RESULT_MAX_ROWS + 4).map { wideRow }
        val result = formatRowsNdjson(rows)
        assertEquals(RESULT_MAX_ROWS, result.lines.size)
        assertEquals(4, result.omittedRows)
        // 行内列数钳制:单行不含 c52/c53
        assertTrue(!result.lines[0].contains("\"c${RESULT_MAX_COLS + 1}\""))
    }
}
