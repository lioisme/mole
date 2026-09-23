package com.lioisme.modev.ui.apps

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.room.Room
import com.google.gson.Gson
import com.lioisme.modev.core.model.BuiltinDeviceProfiles
import com.lioisme.modev.core.model.AppListenOverride
import com.lioisme.modev.core.model.KeywordEntry
import com.lioisme.modev.core.model.KeywordMatchMode
import com.lioisme.modev.core.model.KeywordRule
import com.lioisme.modev.core.model.ProfileField
import com.lioisme.modev.core.model.ProfileFieldCatalog
import com.lioisme.modev.core.model.ProfileKind
import com.lioisme.modev.data.db.ModevDatabase
import com.lioisme.modev.data.repo.MirroredConfigWriter
import com.lioisme.modev.data.repo.SettingsRepository
import com.lioisme.modev.data.repo.SpoofProfileRepository
import com.lioisme.modev.data.server.ConfigServer
import com.lioisme.modev.domain.asset.KeywordLibraryService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 每应用「画像微调」页的整屏渲染与写侧回归(V49 R-2 第二层的组合档)。
 *
 * 覆盖三件纯派生测试够不到的事:
 *  1. **41 行全画出来**(与画像编辑器同一断言形态 —— 两屏缺一条,「这里只能改整张档」就复活);
 *  2. 被覆写的行必须**同时**出现「还原」锚点与「画像值」提示(缺前者 = 改完退不回去,
 *     缺后者 = 用户看不出这行被本应用改过);
 *  3. VM 的写路径真的落到 [SettingsRepository] 的偏差键并反映到生效快照(全装配,无 mock 服务)。
 *
 * 弹层内手势在 Robolectric 不送达(AGENTS 已登记),所以「点开 sheet 再提交」归 androidTest;
 * 本档用 VM 写入面驱动同一批呈现判定。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PerAppProfileTuneRenderRoboTest {

    @get:Rule
    val compose = createComposeRule()

    private val pkg = "com.target"
    private val gson = Gson()
    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var db: ModevDatabase
    private lateinit var profiles: SpoofProfileRepository
    private lateinit var repo: SettingsRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val pixelId = BuiltinDeviceProfiles.idFor(BuiltinDeviceProfiles.PIXEL_7_PRO)

    @Before
    fun setUp() {
        val context: Context = RuntimeEnvironment.getApplication()
        prefs = context.getSharedPreferences("profile-tune-test", Context.MODE_PRIVATE)
            .also { it.edit().clear().commit() }
        db = Room.inMemoryDatabaseBuilder(context, ModevDatabase::class.java).build()
        profiles = SpoofProfileRepository(db.spoofProfileDao(), scope)
        repo = SettingsRepository(
            ConfigServer(java.net.ServerSocket(0).use { it.localPort }),
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

    private fun viewModel() = PerAppFeatureSettingsViewModel(
        packageName = pkg,
        settings = repo,
        keywordLibrary = NoKeywords,
        profiles = profiles,
    )

    private fun showTune(vm: PerAppFeatureSettingsViewModel) {
        compose.setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    PerAppProfileTuneScreen(viewModel = vm, onBack = {})
                }
            }
        }
        compose.waitForIdle()
    }

    /**
     * 行/分区锚点挂在容器上,合并树可见;而 `profile-revert-*` / `profile-follow-*` 挂在
     * `ListItem` 的槽位内 —— 本仓已实锤**这类 tag 只在非合并树里查得到**(AGENTS「已知工具链坑」),
     * 所以两种锚点分两棵树取,否则会测出「按了没反应」的假阴性。
     */
    private fun tagCount(tag: String, slot: Boolean = false) =
        compose.onAllNodesWithTag(tag, useUnmergedTree = slot).fetchSemanticsNodes().size

    /** 一行的锚点名:布尔行走 ToggleListItem(无 profile-row 锚点),其余行走 profile-row。 */
    private fun rowTag(field: ProfileField) =
        if (field.type == com.lioisme.modev.core.model.ProfileValueType.BOOL) {
            "profile-toggle-${field.name}"
        } else {
            "profile-row-${field.name}"
        }

    /**
     * 把「VM 写 → viewModelScope(Main) → 仓库 withContext(IO) → StateFlow」这条链走完。
     *
     * `compose.waitForIdle()` 只管主线程组合与主 looper,**不等 IO 线程上的偏好写入**,
     * 所以缺这一步就会读到写前的旧快照(表现成「VM 没落盘」的假故障)。
     * 形态沿用本仓 `KeywordLibraryRepositoryTest` / `ConfigServer*Test` 的轮询等待。
     */
    private fun flushWrites() {
        val looper = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
        repeat(30) {
            looper.idle()
            Thread.sleep(5)
        }
        looper.idle()
        compose.waitForIdle()
    }

    @Test
    fun everyCatalogFieldHasItsOwnRowOnThePerAppPage() {
        runBlocking { repo.setOverride(pkg, AppListenOverride(spoofProfileId = pixelId)) }
        showTune(viewModel())

        assertEquals(
            "画像编辑器画 41 行,本页也必须画 41 行(R-2:任意一项都能单独定制)",
            ProfileField.entries.size,
            ProfileField.entries.count { tagCount(rowTag(it)) > 0 },
        )
        assertEquals(
            "分区集恒等于来源族集(两屏同一张表)",
            ProfileKind.entries.size,
            ProfileKind.entries.count { tagCount("profile-tune-section-${it.name}") > 0 },
        )
    }

    @Test
    fun overriddenRowShowsRevertAndProfileValue_andLandsInTheDeviationMap() {
        val bound = runBlocking { profiles.find(pixelId)!!.profile }
        assertTrue("前置:内置档必须真填了 MODEL,否则测的是空对空", bound.model.isNotBlank())
        runBlocking { repo.setOverride(pkg, AppListenOverride(spoofProfileId = pixelId)) }
        val vm = viewModel()
        showTune(vm)

        assertEquals("未定制时不得出现还原锚点", 0, tagCount("profile-revert-MODEL", slot = true))
        assertEquals("未定制时不得出现画像值提示", 0, tagCount("profile-follow-MODEL", slot = true))

        vm.setProfileField(ProfileField.MODEL, "Galaxy S24")
        flushWrites()

        // 先验数据面再验呈现面:两者同败 = 写路径没落地;只有后者败 = 锚点/树的问题。
        assertEquals(
            mapOf(ProfileFieldCatalog.jsonKeyOf(ProfileField.MODEL) to "Galaxy S24"),
            repo.overrideFor(pkg).profileOverrides,
        )
        assertTrue("覆写行必须有还原锚点", tagCount("profile-revert-MODEL", slot = true) > 0)
        assertTrue("覆写行必须点名画像原值", tagCount("profile-follow-MODEL", slot = true) > 0)
        assertEquals(
            "生效快照必须带着覆写值下发",
            "Galaxy S24",
            repo.effectiveSwitch(pkg).feature.deviceProfile.model,
        )
        assertEquals(
            "其余项仍随画像",
            bound.brand,
            repo.effectiveSwitch(pkg).feature.deviceProfile.brand,
        )
    }

    @Test
    fun revertHandsTheFieldBackToTheProfileInsteadOfClearingIt() {
        val bound = runBlocking { profiles.find(pixelId)!!.profile }
        runBlocking {
            repo.setOverride(pkg, AppListenOverride(spoofProfileId = pixelId))
        }
        val vm = viewModel()
        showTune(vm)
        vm.setProfileField(ProfileField.MODEL, "Galaxy S24")
        flushWrites()
        assertEquals("前置:覆写确实落了", 1, repo.overrideFor(pkg).profileOverrides?.size)

        vm.revertProfileField(ProfileField.MODEL)
        flushWrites()

        val after = repo.overrideFor(pkg)
        assertNull("还原 = 删掉这条覆写,不是写空", after.profileOverrides)
        assertEquals(
            "还原后必须回到画像值(清空语义是另一件事:行上的清除按钮)",
            bound.model,
            repo.effectiveSwitch(pkg).feature.deviceProfile.model,
        )
        assertEquals("还原后不得再有还原锚点", 0, tagCount("profile-revert-MODEL", slot = true))
    }

    @Test
    fun illegalEditNeverBecomesADeviation() {
        runBlocking { repo.setOverride(pkg, AppListenOverride(spoofProfileId = pixelId)) }
        val vm = viewModel()
        showTune(vm)

        vm.setProfileField(ProfileField.DENSITY_DPI, "999999")
        flushWrites()

        assertNull(
            "非法值不落覆写(否则屏上显示已定制、下发时被丢 = 静默分叉)",
            repo.overrideFor(pkg).profileOverrides,
        )
        assertEquals("锚点也不会凭空出现", 0, tagCount("profile-revert-DENSITY_DPI", slot = true))
    }

    /** 空关键词库:本页不测弹窗子集,只需满足契约非空实现。 */
    private object NoKeywords : KeywordLibraryService {
        override val entries: StateFlow<List<KeywordEntry>> = MutableStateFlow(emptyList())
        override suspend fun add(text: String, matchMode: KeywordMatchMode): Boolean = false
        override suspend fun update(id: Long, text: String, matchMode: KeywordMatchMode): Boolean = false
        override suspend fun setEnabled(id: Long, enabled: Boolean) = Unit
        override suspend fun remove(id: Long) = Unit
        override suspend fun clear() = Unit
        override fun resolveRules(keywordIds: List<Long>?): List<KeywordRule> = emptyList()
    }
}
