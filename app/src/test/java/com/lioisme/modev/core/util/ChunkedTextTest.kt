package com.lioisme.modev.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

/** 惰性分块契约(2026-08-17 V11):size O(1)、get(i) 惰性 substring、边界兜底。 */
class ChunkedTextTest {

    @Test
    fun empty_text_has_zero_chunks() {
        assertEquals(0, ChunkedText("").size)
    }

    @Test
    fun size_is_ceil_of_length_over_chunk() {
        assertEquals(1, ChunkedText("abc", chunkSize = 4).size)
        assertEquals(1, ChunkedText("abcd", chunkSize = 4).size)
        assertEquals(2, ChunkedText("abcde", chunkSize = 4).size)
    }

    @Test
    fun get_slices_chunks_in_order() {
        val chunks = ChunkedText("abcdefghij", chunkSize = 4)
        assertEquals(3, chunks.size)
        assertEquals("abcd", chunks[0])
        assertEquals("efgh", chunks[1])
        assertEquals("ij", chunks[2])
    }

    @Test
    fun get_concatenated_equals_original() {
        val text = "ab".repeat(5_000)
        val chunks = ChunkedText(text)
        assertEquals(
            text,
            buildString { repeat(chunks.size) { i -> append(chunks[i]) } },
        )
    }

    @Test
    fun get_out_of_bounds_returns_empty() {
        val chunks = ChunkedText("abc", chunkSize = 4)
        assertEquals("", chunks[1])
        assertEquals("", chunks[99])
    }
}
