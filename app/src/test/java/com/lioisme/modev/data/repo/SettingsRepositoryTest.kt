package com.lioisme.modev.data.repo

import android.content.Context
import com.google.gson.Gson
import com.lioisme.modev.core.model.HookSwitch
import com.lioisme.modev.data.server.ConfigServer
import java.net.ServerSocket
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * SettingsRepository 全局化单测(V10):迁移按包旧键 → switch_global、
 * 全局键优先、无旧键回落默认并落盘、set 更新内存并广播。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsRepositoryTest {

    private lateinit var context: Context
    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var server: ConfigServer

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        prefs = context.getSharedPreferences("settings-test", Context.MODE_PRIVATE).also { it.edit().clear().commit() }
        server = ConfigServer(ServerSocket(0).use { it.localPort })
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun writer(): MirroredConfigWriter =
        MirroredConfigWriter(prefs, Gson(), remotePrefs = { null })

    private fun repo(): SettingsRepository =
        SettingsRepository(server, writer(), Gson())

    @Test
    fun `legacy per-package keys migrate to global key and are cleaned`() {
        prefs.edit()
            .putString("switch_com.a", Gson().toJson(HookSwitch.DEFAULT.copy(cipher = false)))
            .putString("switch_com.b", Gson().toJson(HookSwitch.DEFAULT.copy(base64 = true)))
            .commit()

        val r = repo()

        // 取首份旧配置作为全局配置(升序取首:com.a)
        assertEquals(false, r.switch.cipher)
        // 全局键已落盘,旧键全部清理(本地残留消除)
        assertTrue(prefs.contains(MirroredConfigWriter.KEY))
        assertFalse(prefs.contains("switch_com.a"))
        assertFalse(prefs.contains("switch_com.b"))
    }

    @Test
    fun `existing global key wins over legacy keys`() {
        prefs.edit()
            .putString(
                MirroredConfigWriter.KEY,
                Gson().toJson(HookSwitch.DEFAULT.copy(mac = false)),
            )
            .putString("switch_com.a", Gson().toJson(HookSwitch.DEFAULT.copy(cipher = false)))
            .commit()

        val r = repo()

        // 用户当前全局配置优先,不被旧键覆盖
        assertEquals(false, r.switch.mac)
        assertEquals(true, r.switch.cipher)
        // 旧键仍被清理
        assertFalse(prefs.contains("switch_com.a"))
    }

    @Test
    fun `no legacy keys - falls back to default and persists global key`() {
        val r = repo()

        assertEquals(HookSwitch.DEFAULT, r.switch)
        assertTrue(prefs.contains(MirroredConfigWriter.KEY))
        assertEquals(HookSwitch.DEFAULT, writer().get())
    }

    @Test
    fun `set updates memory state and persists`() {
        val r = repo()
        val updated = r.switch.copy(keyAgreement = false, random = false)

        r.set(updated)

        assertEquals(updated, r.globalSwitch.value)
        assertEquals(updated, writer().get())
    }
}