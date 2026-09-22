package com.lioisme.modev.data.repo

import android.content.Context
import com.google.gson.Gson
import com.lioisme.modev.core.model.ConfigKeys
import com.lioisme.modev.core.model.McpTokenEntry
import com.lioisme.modev.data.server.ConfigServer
import java.net.ServerSocket
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
 * MCP 令牌契约(V39 R3,Robolectric;V40 D-4 解耦修订):
 * - 默认停用、空列表;启用即时派生(避免启用即锁死);
 * - 多令牌增删 + 校验集回调原地更新;
 * - V40 解耦:外部访问与令牌认证**互相独立**(开外部访问不动认证、关认证不动外部访问);
 * - V38 单键迁移:旧 mcp_token 导入为条目并删旧键;认证直读偏好重放;
 * - 持久化跨实例重放。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsRepositoryMcpTokenTest {

    private lateinit var context: Context
    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var server: ConfigServer

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        prefs = context.getSharedPreferences("mcp-token-test", Context.MODE_PRIVATE).also { it.edit().clear().commit() }
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
    fun `token auth off and list empty by default`() {
        val r = repo()
        assertFalse(r.mcpTokenAuth.value)
        assertTrue(r.mcpTokens.value.isEmpty())
    }

    @Test
    fun `enabling auth derives a token and updates check set`() {
        val r = repo()
        val sets = mutableListOf<Set<String>>()
        r.mcpTokenSetUpdater = { sets += it }

        runBlocking { r.setMcpTokenAuth(true) }

        assertTrue(r.mcpTokenAuth.value)
        assertEquals(1, r.mcpTokens.value.size)
        assertEquals(32, r.mcpTokens.value.single().value.length)
        assertEquals(setOf(r.mcpTokens.value.single().value), sets.last())
    }

    @Test
    fun `add and revoke roundtrip with set updates`() {
        val r = repo()
        val sets = mutableListOf<Set<String>>()
        r.mcpTokenSetUpdater = { sets += it }

        val a = runBlocking { r.addMcpToken("alpha") }
        val b = runBlocking { r.addMcpToken("") }
        assertEquals(2, r.mcpTokens.value.size)
        assertEquals(setOf(a.value, b.value), sets.last())

        runBlocking { r.revokeMcpToken(a.id) }
        assertEquals(listOf(b.id), r.mcpTokens.value.map { it.id })
        assertEquals(setOf(b.value), sets.last())
    }

    @Test
    fun `external access independent of token auth`() {
        val r = repo()
        runBlocking { r.setExternalAccess(true) }
        assertTrue(r.serverExternalAccess.value)
        assertFalse("外部访问与令牌认证互相独立,不得级联启用认证", r.mcpTokenAuth.value)
        assertTrue("不得自动派生令牌", r.mcpTokens.value.isEmpty())
    }

    @Test
    fun `disabling auth keeps external access on`() {
        val r = repo()
        runBlocking { r.setExternalAccess(true) }
        runBlocking { r.setMcpTokenAuth(true) }
        runBlocking { r.setMcpTokenAuth(false) }
        assertFalse(r.mcpTokenAuth.value)
        assertTrue("关认证不得级联关闭外部访问", r.serverExternalAccess.value)
    }

    @Test
    fun `legacy single token key migrates to entry and is removed`() {
        prefs.edit()
            .putString(ConfigKeys.MCP_TOKEN, "legacy-token-value")
            .putBoolean(ConfigKeys.MCP_TOKEN_AUTH, true)
            .commit()

        val r = repo()

        val imported = r.mcpTokens.value.single()
        assertEquals("legacy-token-value", imported.value)
        assertFalse(prefs.contains(ConfigKeys.MCP_TOKEN))
        assertTrue("认证开关直读偏好重放", r.mcpTokenAuth.value)
    }

    @Test
    fun `tokens persist across repository instances`() {
        val r1 = repo()
        val created = runBlocking { r1.addMcpToken("kept") }

        val r2 = repo()
        assertEquals(1, r2.mcpTokens.value.size)
        assertEquals(created.value, r2.mcpTokens.value.single().value)
        assertEquals("kept", r2.mcpTokens.value.single().label)
        assertFalse("认证开关默认停用,不随令牌存在而开", r2.mcpTokenAuth.value)
    }

    @Test
    fun `external access and auth states replay from prefs`() {
        // V41 统一键:统一键置位 + 令牌认证直读(双键分裂已退役)。
        prefs.edit()
            .putBoolean(ConfigKeys.SERVER_EXTERNAL_ACCESS, true)
            .putBoolean(ConfigKeys.MCP_TOKEN_AUTH, true)
            .commit()
        val r = repo()
        assertTrue(r.serverExternalAccess.value)
        assertTrue(r.mcpTokenAuth.value)

        // V40 D-4:外部访问开而认证关 = 合法组合,重启后原样重放。
        prefs.edit().putBoolean(ConfigKeys.MCP_TOKEN_AUTH, false).commit()
        val r2 = repo()
        assertTrue(r2.serverExternalAccess.value)
        assertFalse("认证不得按外部访问重推导", r2.mcpTokenAuth.value)
    }

    @Test
    fun `revoke last token keeps auth on deny-all recoverable via UI`() {
        val r = repo()
        runBlocking {
            r.setMcpTokenAuth(true)
            val token = r.mcpTokens.value.single()
            r.revokeMcpToken(token.id)
        }
        assertTrue(r.mcpTokenAuth.value)
        assertTrue(r.mcpTokens.value.isEmpty())
    }

    // ---- V46 R3 重命名(setMcpTokenLabel)----

    @Test
    fun `rename updates label only and keeps value order intact`() {
        val r = repo()
        val sets = mutableListOf<Set<String>>()
        r.mcpTokenSetUpdater = { sets += it }

        val first = runBlocking { r.addMcpToken("alpha") }
        val second = runBlocking { r.addMcpToken("beta") }
        runBlocking { r.setMcpTokenLabel(first.id, "  改名后  ") }

        val tokens = r.mcpTokens.value
        assertEquals("条目数与顺序不变", listOf(first.id, second.id), tokens.map { it.id })
        assertEquals("label 两端空白被裁", "改名后", tokens[0].label)
        assertEquals("beta", tokens[1].label)
        assertEquals(
            "值集合内容不变(原地生效、不重启不踢会话)",
            setOf(first.value, second.value),
            sets.last(),
        )
    }

    @Test
    fun `rename to blank falls back to unnamed rendering form`() {
        val r = repo()
        val entry = runBlocking { r.addMcpToken("tmp") }
        runBlocking { r.setMcpTokenLabel(entry.id, "") }
        assertEquals("", r.mcpTokens.value.single().label)
    }

    @Test
    fun `rename unknown id is an idempotent no-op`() {
        val r = repo()
        runBlocking { r.addMcpToken("keep") }
        val before = r.mcpTokens.value
        val sets = mutableListOf<Set<String>>()
        r.mcpTokenSetUpdater = { sets += it }

        runBlocking { r.setMcpTokenLabel("no-such-id", "x") }

        assertEquals(before, r.mcpTokens.value)
        assertTrue("未知 id 不得触发持久化与校验集回调", sets.isEmpty())
    }

    @Test
    fun `rename persists across repository instances`() {
        val r = repo()
        val entry = runBlocking { r.addMcpToken("before") }
        runBlocking { r.setMcpTokenLabel(entry.id, "after") }

        val replayed = repo().mcpTokens.value.single()
        assertEquals("after", replayed.label)
        assertEquals("值不受改名影响", entry.value, replayed.value)
    }
}
