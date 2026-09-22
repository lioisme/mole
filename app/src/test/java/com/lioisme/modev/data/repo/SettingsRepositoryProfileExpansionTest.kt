package com.lioisme.modev.data.repo

import android.content.Context
import androidx.room.Room
import com.google.gson.Gson
import com.lioisme.modev.core.model.AppListenOverride
import com.lioisme.modev.core.model.BuiltinDeviceProfiles
import com.lioisme.modev.core.model.DeviceProfile
import com.lioisme.modev.core.model.FeatureConfig
import com.lioisme.modev.core.model.ProfileField
import com.lioisme.modev.core.model.ProfileValues
import com.lioisme.modev.core.model.withText
import com.lioisme.modev.data.db.ModevDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * V47 T2 / 决策 D-12 端到端证据:`spoofProfileId` 在**宿主侧**被展开成
 * [com.lioisme.modev.core.model.FeatureConfig.deviceProfile] 快照,注入侧只消费快照。
 *
 * 这是整条重建链路唯一一处「绑定关系真正变成可用值」的地方,所以断言全部打在
 * 真实产物上(真 Room 库 + 真设置仓库 + 真持久化),不使用 mock 服务。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsRepositoryProfileExpansionTest {

    private val gson = Gson()
    private lateinit var context: Context
    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var db: ModevDatabase
    private lateinit var profiles: SpoofProfileRepository
    private lateinit var repo: SettingsRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val pixelId = BuiltinDeviceProfiles.idFor(BuiltinDeviceProfiles.PIXEL_7_PRO)
    private val xiaomiId = BuiltinDeviceProfiles.idFor(BuiltinDeviceProfiles.XIAOMI_13)

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        prefs = context.getSharedPreferences("settings-profile-test", Context.MODE_PRIVATE)
            .also { it.edit().clear().commit() }
        db = Room.inMemoryDatabaseBuilder(context, ModevDatabase::class.java).build()
        profiles = SpoofProfileRepository(db.spoofProfileDao(), scope)
        repo = SettingsRepository(
            com.lioisme.modev.data.server.ConfigServer(
                java.net.ServerSocket(0).use { it.localPort },
            ),
            MirroredConfigWriter(prefs, gson, remotePrefs = { null }),
            gson,
            prefs,
        )
        runBlocking { profiles.synchronizeBuiltins() }
        repo.attachSpoofProfiles(profiles)
    }

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
    }

    @Test
    fun `global binding expands into the injected snapshot`() = runBlocking {
        repo.setGlobalFeature(FeatureConfig(spoofProfileId = pixelId))
        val effective = repo.effectiveSwitch("com.target")
        assertEquals(pixelId, effective.feature.spoofProfileId)
        assertEquals(
            "快照必须恰为画像库正文(不是内置常量再查表)",
            profiles.find(pixelId)!!.profile,
            effective.feature.deviceProfile,
        )
        assertEquals("google", effective.feature.deviceProfile.brand)
    }

    @Test
    fun `per-package binding overrides the global snapshot`() = runBlocking {
        repo.setGlobalFeature(FeatureConfig(spoofProfileId = pixelId))
        repo.setOverride("com.target", AppListenOverride(spoofProfileId = xiaomiId))

        assertEquals("Xiaomi", repo.effectiveSwitch("com.target").feature.deviceProfile.brand)
        assertEquals("google", repo.effectiveSwitch("com.other").feature.deviceProfile.brand)
    }

    @Test
    fun `unknown profile id yields empty snapshot and never falls back to a builtin`() = runBlocking {
        repo.setGlobalFeature(FeatureConfig(spoofProfileId = pixelId))
        repo.setOverride("com.target", AppListenOverride(spoofProfileId = "deleted-long-ago"))

        val target = repo.effectiveSwitch("com.target").feature.deviceProfile
        assertEquals(
            "绑了不存在的画像必须落回不伪装 —— 回落任何档位就是 GAP-02 的静默失效",
            DeviceProfile.EMPTY,
            target,
        )
        // 其他包不受牵连
        assertEquals("google", repo.effectiveSwitch("com.other").feature.deviceProfile.brand)
    }

    @Test
    fun `explicit unbind clears the snapshot`() = runBlocking {
        repo.setGlobalFeature(FeatureConfig(spoofProfileId = pixelId))
        repo.setOverride("com.target", AppListenOverride(spoofProfileId = ""))

        assertTrue(repo.effectiveSwitch("com.target").feature.deviceProfile.isEmpty)
        assertNotEquals(
            DeviceProfile.EMPTY,
            repo.effectiveSwitch("com.other").feature.deviceProfile,
        )
    }

    @Test
    fun `editing a profile in the library propagates to the effective stream`() = runBlocking {
        repo.setGlobalFeature(FeatureConfig(spoofProfileId = pixelId))
        val before = repo.effectiveSwitchFlow("com.target").first()
        assertEquals(500, before.feature.deviceProfile.densityDpi)

        val edited = ProfileField.DENSITY_DPI.withText(profiles.find(pixelId)!!.profile, "420")
        assertTrue(profiles.update(pixelId, "改过的 Pixel", "", edited))

        val after = repo.effectiveSwitchFlow("com.target").first()
        assertEquals("画像库改动未随生效流重推", 420, after.feature.deviceProfile.densityDpi)
        assertEquals("google", after.feature.deviceProfile.brand)
    }

    @Test
    fun `legacy resolved profile survives when the library is not wired`() {
        // 注入侧「直读两键 / 磁盘兜底」两条通路不经宿主展开,
        // 因此**未装配画像库时绝不能用空快照覆盖来路已带的快照**。
        val unwired = SettingsRepository(
            com.lioisme.modev.data.server.ConfigServer(
                java.net.ServerSocket(0).use { it.localPort },
            ),
            MirroredConfigWriter(prefs, gson, remotePrefs = { null }),
            gson,
            prefs,
        )
        val carried = ProfileField.BRAND.withText(DeviceProfile.EMPTY, "pushed-by-script")
        val snapshot = gson.toJson(
            AppListenOverride(spoofProfileId = pixelId, resolvedProfile = carried),
        )

        val restored = AppListenOverride.fromJson(gson, snapshot)
        assertEquals(carried, restored.resolvedProfile)

        runBlocking { unwired.setOverride("com.target", restored) }
        assertEquals(
            "未装配画像库时快照必须原样透传",
            "pushed-by-script",
            unwired.effectiveSwitch("com.target").feature.deviceProfile.brand,
        )
    }

    @Test
    fun `carried snapshot round trips through persisted override json`() = runBlocking {
        val carried = ProfileField.DENSITY_DPI.withText(
            ProfileField.BRAND.withText(DeviceProfile.EMPTY, "samsung"),
            "560",
        )
        repo.setOverride(
            "com.target",
            AppListenOverride(spoofProfileId = xiaomiId, resolvedProfile = carried),
        )

        val persisted = prefs.getString(
            com.lioisme.modev.core.model.ConfigKeys.overrideKey("com.target"),
            null,
        )
        assertTrue("快照必须真的落盘", persisted!!.contains("resolvedProfile"))

        val restored = AppListenOverride.fromJson(gson, persisted)
        assertEquals(carried, restored.resolvedProfile)
        assertEquals(xiaomiId, restored.spoofProfileId)
    }

    @Test
    fun `derived snapshot is never counted as a user deviation`() {
        // 派生数据若计入偏差,「恢复默认」行的计数与副标就会与真实意图分叉。
        val withSnapshot = AppListenOverride(resolvedProfile = DeviceProfile(brand = "x"))
        assertTrue(
            "仅有快照而无任何用户意图 ⇒ 必须仍视为零偏差(否则空画像键会被永久留存)",
            withSnapshot.isDefault,
        )
        assertEquals(0, withSnapshot.deviationCount)

        val bound = AppListenOverride(spoofProfileId = pixelId, resolvedProfile = DeviceProfile(brand = "x"))
        assertEquals("绑定算 1 项偏差,快照不得再加一项", 1, bound.deviationCount)
        assertFalse("绑定了画像不得判为零偏差", bound.isDefault)
    }

    @Test
    fun `clearing the feature domain drops the snapshot together with the binding`() {
        val both = AppListenOverride(
            spoofProfileId = pixelId,
            resolvedProfile = DeviceProfile(brand = "google"),
            extraRootPackages = listOf("com.magisk"),
        )
        val cleaned = both.clearDomain(com.lioisme.modev.core.model.CapabilityDomain.FEATURE)
        assertEquals(null, cleaned.spoofProfileId)
        assertEquals(
            "快照与绑定同生同灭:单留会指向不存在的绑定",
            null,
            cleaned.resolvedProfile,
        )
        assertEquals(
            "同域其他偏差仍应被清除",
            null,
            cleaned.extraRootPackages,
        )
    }

    @Test
    fun `sentinel slots of the expanded snapshot stay unhook`() {
        val effective = runBlocking {
            repo.setGlobalFeature(FeatureConfig(spoofProfileId = pixelId))
            repo.effectiveSwitch("com.target")
        }.feature.deviceProfile
        assertEquals(
            "内置档未填网络类型 ⇒ 注入侧不得改写网络",
            ProfileValues.NET_UNHOOK,
            effective.networkType,
        )
        assertEquals(ProfileValues.SCREENSHOT_UNHOOK, effective.screenshotsFlag)
        assertEquals(ProfileValues.UNSET_DOUBLE, effective.latitude, 0.0)
    }
}
