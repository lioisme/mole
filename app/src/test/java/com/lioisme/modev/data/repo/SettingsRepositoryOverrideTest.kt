package com.lioisme.modev.data.repo

import android.content.Context
import com.google.gson.Gson
import com.lioisme.modev.core.model.AppListenOverride
import com.lioisme.modev.core.model.ConfigKeys
import com.lioisme.modev.core.model.CryptoKind
import com.lioisme.modev.core.model.HookSwitch
import com.lioisme.modev.core.model.SqlKind
import com.lioisme.modev.data.server.ConfigServer
import java.net.ServerSocket
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
 * 单应用监听自定义持久化契约(V24 D2):每包一键 `override_{pkg}` 双写、
 * 零偏差即删键、init 预载、旧 `switch_{pkg}` 键隔离(不误吞)、effective 解析联动。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsRepositoryOverrideTest {

    private lateinit var context: Context
    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var server: ConfigServer

    private val gson = Gson()

    private val global = HookSwitch.DEFAULT

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        prefs = context.getSharedPreferences("settings-override-test", Context.MODE_PRIVATE)
            .also { it.edit().clear().commit() }
        server = ConfigServer(ServerSocket(0).use { it.localPort })
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun repo(): SettingsRepository =
        SettingsRepository(
            server,
            MirroredConfigWriter(prefs, gson, remotePrefs = { null }),
            gson,
        )

    @Test
    fun `setOverride persists per-package key and exposes memory state`() = runBlocking {
        val r = repo()
        val override = AppListenOverride(
            kinds = mapOf(CryptoKind.CIPHER.name to false),
        )

        r.setOverride("com.target", override)

        val key = ConfigKeys.overrideKey("com.target")
        assertTrue(prefs.contains(key))
        assertEquals(override, prefs.getString(key, null)?.let { AppListenOverride.fromJson(gson, it) })
        assertEquals(override, r.overrideFor("com.target"))
        assertEquals(override, r.overrides.value["com.target"])
    }

    @Test
    fun `setOverride with default removes stored key`() = runBlocking {
        val r = repo()
        r.setOverride("com.target", AppListenOverride(kinds = mapOf(CryptoKind.CIPHER.name to false)))

        // 偏差被清空为零偏差:等价删除存储键(避免键膨胀)
        r.setOverride("com.target", AppListenOverride.DEFAULT)

        assertFalse(prefs.contains(ConfigKeys.overrideKey("com.target")))
        assertTrue(r.overrideFor("com.target").isDefault)
    }

    @Test
    fun `removeOverride deletes key and resets to global semantics`() = runBlocking {
        val r = repo()
        r.setOverride("com.target", AppListenOverride(cryptoListen = false))

        r.removeOverride("com.target")

        assertFalse(prefs.contains(ConfigKeys.overrideKey("com.target")))
        assertEquals(HookSwitch.DEFAULT, r.effectiveSwitch("com.target"))
    }

    @Test
    fun `init reloads persisted overrides and filters invalid json`() {
        prefs.edit()
            .putString(
                ConfigKeys.overrideKey("com.a"),
                AppListenOverride(dbListen = false).toJson(gson),
            )
            .putString(ConfigKeys.overrideKey("com.b"), "{broken")
            .putString("switch_com.c", "{}") // 旧全局前缀键:与本特性隔离,不被预载
            .commit()

        val r = repo()

        assertEquals(AppListenOverride(dbListen = false), r.overrideFor("com.a"))
        assertTrue(r.overrideFor("com.b").isDefault)
        assertTrue(r.overrideFor("com.c").isDefault)
        // 旧 switch_{pkg} 键不受 override 特性影响(migrateLegacy 清理属既有行为,断言仅关注互不干扰)
        assertEquals(mapOf("com.a" to AppListenOverride(dbListen = false)), r.overrides.value)
    }

    @Test
    fun `effectiveSwitch combines global and override`() = runBlocking {
        val r = repo()
        r.set(global.copy(cipher = false))
        r.setOverride(
            "com.target",
            AppListenOverride(
                cryptoListen = true,
                kinds = mapOf(CryptoKind.CIPHER.name to true),
            ),
        )

        val effective = r.effectiveSwitch("com.target")
        assertTrue(effective.cipher)
        // 未定制位取全局
        assertEquals(global.messageDigest, effective.messageDigest)
        // 其他应用无覆盖 → 纯全局
        assertFalse(r.effectiveSwitch("com.other").cipher)
    }

    @Test
    fun `effectiveSwitchFlow re-emits on global change`() = runBlocking {
        val r = repo()
        r.setOverride("com.target", AppListenOverride(kinds = mapOf(CryptoKind.MAC.name to false)))

        val before = r.effectiveSwitchFlow("com.target").first()
        assertFalse(before.mac)

        r.set(global.copy(mac = false, cipher = false))
        val after = r.effectiveSwitchFlow("com.target").first()
        assertFalse(after.mac)
        assertFalse(after.cipher) // 默认配置变更传导到未定制位
    }
}
