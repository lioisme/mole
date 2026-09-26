package com.lioisme.modev.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * RecordType 领域枚举契约(V17 R2):
 * fromWire 已知/未知;wire 与 RecordTypes 字符串常量库单源一致。
 */
class RecordTypeTest {

    @Test
    fun `fromWire maps known wire to enum`() {
        assertEquals(RecordType.CRYPTO, RecordType.fromWire("crypto"))
        assertEquals(RecordType.DATABASE, RecordType.fromWire("db"))
        assertEquals(RecordType.BEHAVIOR, RecordType.fromWire("behavior"))
        assertEquals(RecordType.PREFS, RecordType.fromWire("prefs"))
        assertEquals(RecordType.WEBVIEW, RecordType.fromWire("webview"))
        assertEquals(RecordType.NETWORK, RecordType.fromWire("network"))
        assertEquals(RecordType.DEVICE_ENV, RecordType.fromWire("device_env"))
    }

    @Test
    fun `fromWire returns null for unknown or null wire`() {
        assertNull(RecordType.fromWire("database"))
        assertNull(RecordType.fromWire("file"))
        assertNull(RecordType.fromWire(""))
        assertNull(RecordType.fromWire(null))
    }

    @Test
    fun `wire is single-sourced from RecordTypes constants`() {
        assertEquals(RecordTypes.CRYPTO, RecordType.CRYPTO.wire)
        assertEquals(RecordTypes.DATABASE, RecordType.DATABASE.wire)
        assertEquals(RecordTypes.BEHAVIOR, RecordType.BEHAVIOR.wire)
        assertEquals(RecordTypes.PREFS, RecordType.PREFS.wire)
        assertEquals(RecordTypes.WEBVIEW, RecordType.WEBVIEW.wire)
        assertEquals(RecordTypes.NETWORK, RecordType.NETWORK.wire)
        assertEquals(RecordTypes.DEVICE_ENV, RecordType.DEVICE_ENV.wire)
    }
}
