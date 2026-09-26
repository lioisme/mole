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
 * SettingsRepository 默认配置契约:
 * - 旧按包键迁移链路保留(migrateLegacy 清理残留);
 * - **init 无条件重置 `switch_global` 为内置推荐值**——清除 V24 及更早全局设置页
 *   遗留的用户自定义(默认配置自此无 UI 写入路径,陈旧安装自愈);
 * - globalSwitch 流恒为 [HookSwitch.DEFAULT]。
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
        SettingsRepository(server, writer(), Gson(), prefs)

    @Test
    fun `legacy per-package keys are cleaned during migration`() {
        prefs.edit()
            .putString("switch_com.a", Gson().toJson(HookSwitch.DEFAULT.copy(cipher = false)))
            .putString("switch_com.b", Gson().toJson(HookSwitch.DEFAULT.copy(base64 = true)))
            .commit()

        val r = repo()

        // 全局键落盘为内置默认,旧按包键全部清理(本地残留消除)
        assertEquals(HookSwitch.DEFAULT, r.globalSwitch.value)
        assertTrue(prefs.contains(MirroredConfigWriter.KEY))
        assertFalse(prefs.contains("switch_com.a"))
        assertFalse(prefs.contains("switch_com.b"))
    }

    @Test
    fun `stale customized switch_global is reset to builtin default on init`() {
        prefs.edit()
            .putString(
                MirroredConfigWriter.KEY,
                Gson().toJson(HookSwitch.DEFAULT.copy(mac = false, maxBytesPerField = 42)),
            )
            .commit()

        val r = repo()

        // V25 D2:历史全局自定义强制重置为内置推荐值(含远程镜像)
        assertEquals(HookSwitch.DEFAULT, r.globalSwitch.value)
        assertEquals(HookSwitch.DEFAULT, writer().get())
    }

    @Test
    fun `no legacy keys - default is seeded and persisted`() {
        val r = repo()

        assertEquals(HookSwitch.DEFAULT, r.globalSwitch.value)
        assertTrue(prefs.contains(MirroredConfigWriter.KEY))
        assertEquals(HookSwitch.DEFAULT, writer().get())
    }

    @Test
    fun `external access defaults to disabled`() {
        val r = repo()
        assertFalse(r.serverExternalAccess.value)
    }

    @Test
    fun `setExternalAccess persists and updates flow both ways`() = kotlinx.coroutines.runBlocking {
        val r = repo()

        r.setExternalAccess(true)
        assertTrue(r.serverExternalAccess.value)
        assertTrue(prefs.getBoolean(com.lioisme.modev.core.model.ConfigKeys.SERVER_EXTERNAL_ACCESS, false))
        assertTrue(server.externalAccess)

        r.setExternalAccess(false)
        assertFalse(r.serverExternalAccess.value)
        assertFalse(prefs.getBoolean(com.lioisme.modev.core.model.ConfigKeys.SERVER_EXTERNAL_ACCESS, true))
        assertFalse(server.externalAccess)
    }

    @Test
    fun `stored external access preference is replayed on init`() {
        prefs.edit()
            .putBoolean(com.lioisme.modev.core.model.ConfigKeys.SERVER_EXTERNAL_ACCESS, true)
            .commit()

        val r = repo()

        // 进程重启后偏好重放:仓库流与服务端标志同步为开启(restart 异步改绑)
        assertTrue(r.serverExternalAccess.value)
        assertTrue(server.externalAccess)
    }
}
