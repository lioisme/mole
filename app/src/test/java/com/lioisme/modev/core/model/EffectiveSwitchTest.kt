package com.lioisme.modev.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * EffectiveSwitch 三态继承解析全分支(V23 D1):
 * 总开关联动、类别覆盖映射、INHERIT 传导、未知 kind 忽略、零拷贝短路。
 */
class EffectiveSwitchTest {

    private val global = HookSwitch.DEFAULT

    @Test
    fun `null override returns same instance`() {
        assertSame(global, EffectiveSwitch.resolve(global, null))
    }

    @Test
    fun `default override returns same instance`() {
        assertSame(global, EffectiveSwitch.resolve(global, AppListenOverride.DEFAULT))
    }

    @Test
    fun `crypto master ON forces enabled when global disabled`() {
        val g = global.copy(cryptoListen = false)
        val effective = EffectiveSwitch.resolve(
            g,
            AppListenOverride(cryptoListen = TriState.ON),
        )
        assertTrue(effective.cryptoListen)
        // 未覆盖位保持全局原值
        assertEquals(g.dbListen, effective.dbListen)
    }

    @Test
    fun `db master OFF forces disabled when global enabled`() {
        val effective = EffectiveSwitch.resolve(
            global,
            AppListenOverride(dbListen = TriState.OFF),
        )
        assertFalse(effective.dbListen)
        assertEquals(global.cryptoListen, effective.cryptoListen)
    }

    @Test
    fun `kind override maps crypto field`() {
        val effective = EffectiveSwitch.resolve(
            global,
            AppListenOverride(kindOverrides = mapOf(CryptoKind.CIPHER.name to TriState.OFF)),
        )
        assertFalse(effective.cipher)
        assertTrue(effective.enabled(CryptoKind.MESSAGE_DIGEST))
        assertFalse(effective.enabled(CryptoKind.CIPHER))
    }

    @Test
    fun `kind override maps sql field`() {
        val effective = EffectiveSwitch.resolve(
            global,
            AppListenOverride(kindOverrides = mapOf(SqlKind.DML.name to TriState.ON)),
        )
        assertTrue(effective.dbDml)
        assertTrue(effective.dbEnabled(SqlKind.DML))
        // 未覆盖 SQL 类别取全局默认(dbOpen 默认 false)
        assertFalse(effective.dbEnabled(SqlKind.OPEN))
    }

    @Test
    fun `unknown kind name is ignored`() {
        val effective = EffectiveSwitch.resolve(
            global,
            AppListenOverride(
                kindOverrides = mapOf("NOT_A_KIND" to TriState.OFF, CryptoKind.MAC.name to TriState.OFF),
            ),
        )
        assertFalse(effective.mac)
        // 其余字段不受垃圾键影响
        assertEquals(global, effective.copy(mac = true))
    }

    @Test
    fun `inherit kind entries are no-op`() {
        val effective = EffectiveSwitch.resolve(
            global,
            AppListenOverride(
                kindOverrides = mapOf(CryptoKind.CIPHER.name to TriState.INHERIT),
            ),
        )
        assertEquals(global, effective)
    }

    @Test
    fun `override wins over global for same kind`() {
        val g = global.copy(cipher = false, dbDml = false)
        val effective = EffectiveSwitch.resolve(
            g,
            AppListenOverride(
                kindOverrides = mapOf(
                    CryptoKind.CIPHER.name to TriState.ON,
                    SqlKind.DML.name to TriState.ON,
                ),
            ),
        )
        assertTrue(effective.cipher)
        assertTrue(effective.dbDml)
    }

    @Test
    fun `master OFF still suppresses kinds forced on`() {
        // HookSwitch.enabled 语义:总开关 false 时类别恒停用 —— 覆盖不破坏该语义
        val effective = EffectiveSwitch.resolve(
            global,
            AppListenOverride(
                cryptoListen = TriState.OFF,
                kindOverrides = mapOf(CryptoKind.CIPHER.name to TriState.ON),
            ),
        )
        assertTrue(effective.cipher)
        assertFalse(effective.anyCryptoEnabled)
        assertFalse(effective.enabled(CryptoKind.CIPHER))
    }

    @Test
    fun `derived flags follow resolved result`() {
        val effective = EffectiveSwitch.resolve(
            global,
            AppListenOverride(
                dbListen = TriState.ON,
                kindOverrides = mapOf(SqlKind.OPEN.name to TriState.ON),
            ),
        )
        assertTrue(effective.anyDbEnabled)
    }
}
