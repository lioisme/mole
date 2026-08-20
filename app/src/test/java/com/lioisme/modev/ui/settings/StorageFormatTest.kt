package com.lioisme.modev.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/** 存储占用格式化(V19 T7):档位阈值与小数精度。 */
class StorageFormatTest {

    @Test
    fun zeroAndTinyBytes() {
        assertEquals("0 B", StorageFormat.formatBytes(0))
        assertEquals("999 B", StorageFormat.formatBytes(999))
    }

    @Test
    fun kib() {
        assertEquals("1.0 KiB", StorageFormat.formatBytes(1024))
        assertEquals("1.5 KiB", StorageFormat.formatBytes(1536))
        assertEquals("1024.0 KiB", StorageFormat.formatBytes(1024 * 1024 - 1))
    }

    @Test
    fun mib() {
        assertEquals("1.0 MiB", StorageFormat.formatBytes(1024 * 1024))
        assertEquals("2.5 MiB", StorageFormat.formatBytes((2.5 * 1024 * 1024).toLong()))
    }

    @Test
    fun gib() {
        assertEquals("1.0 GiB", StorageFormat.formatBytes(1024L * 1024 * 1024))
        assertEquals("3.2 GiB", StorageFormat.formatBytes((3.2 * 1024 * 1024 * 1024).toLong()))
    }

    @Test
    fun negativeCoercesToZero() {
        assertEquals("0 B", StorageFormat.formatBytes(-1))
    }
}