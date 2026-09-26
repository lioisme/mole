package com.lioisme.modev.data.repo

import android.content.Context
import com.google.gson.Gson
import com.lioisme.modev.core.model.ConfigKeys
import com.lioisme.modev.data.server.ConfigServer
import java.net.ServerSocket
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 统一外部访问迁移契约:
 * - `SERVER_EXTERNAL_ACCESS` 为唯一事实源(wire + MCP 双服务消费);
 * - V38 遗留 `mcp_external_access` 读后 OR 合并进统一键并**删除旧键**(幂等);
 * - 迁移重放:合并结果跨仓库实例持久;统一开关写路径不触碰令牌/认证态。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsRepositoryExternalAccessTest {

    private lateinit var context: Context
    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var server: ConfigServer

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        prefs = context.getSharedPreferences("external-access-test", Context.MODE_PRIVATE).also {
            it.edit().clear().commit()
        }
        server = ConfigServer(ServerSocket(0).use { it.localPort })
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun repo(): SettingsRepository =
        SettingsRepository(
            server = server,
            writer = MirroredConfigWriter(prefs, Gson(), remotePrefs = { null }),
            gson = Gson(),
            localPrefs = prefs,
        )

    @Test
    fun `legacy mcp key merges into unified key and is removed`() {
        // V38 用户仅开启 MCP 外部访问:升级后统一键 = true(双服务同绑 0.0.0.0,RK-1 语义)
        prefs.edit().putBoolean(ConfigKeys.MCP_EXTERNAL_ACCESS, true).commit()

        val r = repo()

        assertTrue(r.serverExternalAccess.value)
        assertTrue(server.externalAccess)
        assertFalse("旧键合并后必须删除", prefs.contains(ConfigKeys.MCP_EXTERNAL_ACCESS))
        assertTrue("合并结果必须落统一键", prefs.getBoolean(ConfigKeys.SERVER_EXTERNAL_ACCESS, false))
    }

    @Test
    fun `both legacy keys true unifies to true`() {
        prefs.edit()
            .putBoolean(ConfigKeys.SERVER_EXTERNAL_ACCESS, true)
            .putBoolean(ConfigKeys.MCP_EXTERNAL_ACCESS, true)
            .commit()

        val r = repo()

        assertTrue(r.serverExternalAccess.value)
        assertFalse(prefs.contains(ConfigKeys.MCP_EXTERNAL_ACCESS))
    }

    @Test
    fun `both legacy keys false unifies to false with old key removed`() {
        prefs.edit()
            .putBoolean(ConfigKeys.SERVER_EXTERNAL_ACCESS, false)
            .putBoolean(ConfigKeys.MCP_EXTERNAL_ACCESS, false)
            .commit()

        val r = repo()

        assertFalse(r.serverExternalAccess.value)
        assertFalse("停用态迁移同样删除旧键", prefs.contains(ConfigKeys.MCP_EXTERNAL_ACCESS))
    }

    @Test
    fun `migration is idempotent across replays`() {
        prefs.edit().putBoolean(ConfigKeys.MCP_EXTERNAL_ACCESS, true).commit()
        val r1 = repo()
        assertTrue(r1.serverExternalAccess.value)
        assertFalse(prefs.contains(ConfigKeys.MCP_EXTERNAL_ACCESS))

        // 二次 init:旧键已删,统一键保持,零改写
        val r2 = repo()
        assertTrue(r2.serverExternalAccess.value)
        assertTrue(prefs.getBoolean(ConfigKeys.SERVER_EXTERNAL_ACCESS, false))
    }

    @Test
    fun `unified write path leaves token state untouched`() = kotlinx.coroutines.runBlocking {
        val r = repo()

        r.setExternalAccess(true)
        assertTrue(r.serverExternalAccess.value)
        assertFalse("统一开关不得级联启用认证", r.mcpTokenAuth.value)
        assertTrue("统一开关不得派生令牌", r.mcpTokens.value.isEmpty())

        r.setExternalAccess(false)
        assertFalse(r.mcpTokenAuth.value)
        assertTrue(r.mcpTokens.value.isEmpty())
    }
}
