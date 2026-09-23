package com.lioisme.modev.ui.settings.profiles

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import com.lioisme.modev.R
import com.lioisme.modev.core.model.AppListenOverride
import com.lioisme.modev.core.model.CapabilityDomain
import com.lioisme.modev.core.model.DeviceProfile
import com.lioisme.modev.core.model.DeviceProfileEntry
import com.lioisme.modev.core.model.FeatureConfig
import com.lioisme.modev.core.model.HookSwitch
import com.lioisme.modev.core.model.SpoofProfiles
import kotlinx.coroutines.runBlocking
import com.lioisme.modev.domain.config.FeatureService
import com.lioisme.modev.domain.config.ListenConfigService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 画像库页的**整屏渲染**回归(V47 WP-5,Robolectric 档 = 只测「画什么」)。
 *
 * 与 [ProfileEditorRenderRoboTest] 同一分工:弹层内手势在本 harness 下不生效
 * (AGENTS「已知工具链坑」实锤条),故本类经 VM/契约写入面驱动,行「更多」菜单的
 * 展开与三项动作落在插桩档 `ProfileEditorFlowTest`。
 *
 * 钉住的形态:
 *  1. 一行一画像、内置行有徽章、DIY 行没有(身份与呈现分离的直观面);
 *  2. 摘要「几项已填」与「被几应用绑定」同源计数 —— 绑定计数走 `overrides` 快照,
 *     禁逐包 binder 查询(与 AGENTS 目录内存映射同一红线);
 *  3. 接管前的旧通路(全局档位单选 + 字段覆写清单)仍在屏上:它仍是既有 hooker 的
 *     取值通路,与 MCP `spoof_global_set` 同一 id 空间,撤掉 = 撤掉一条用户能力。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SpoofProfileLibraryRenderRoboTest {

    @get:Rule
    val compose = createComposeRule()

    private fun str(res: Int, vararg args: Any?): String =
        RuntimeEnvironment.getApplication().getString(res, *args)

    private fun quantity(res: Int, n: Int): String =
        RuntimeEnvironment.getApplication().resources.getQuantityString(res, n, n)

    private class FakeListen(
        override val overrides: MutableStateFlow<Map<String, AppListenOverride>> = MutableStateFlow(emptyMap()),
    ) : ListenConfigService {
        override val globalSwitch: StateFlow<HookSwitch> = MutableStateFlow(HookSwitch.DEFAULT)
        override fun overrideFor(packageName: String): AppListenOverride =
            overrides.value[packageName] ?: AppListenOverride.DEFAULT

        override fun effectiveSwitch(packageName: String): HookSwitch = HookSwitch.DEFAULT
        override fun effectiveSwitchFlow(packageName: String): Flow<HookSwitch> = globalSwitch
        override suspend fun setOverride(packageName: String, override: AppListenOverride) {
            overrides.value = overrides.value + (packageName to override)
        }

        override suspend fun resetOverride(packageName: String, domain: CapabilityDomain) = Unit
    }

    private class FakeFeature : FeatureService {
        override val globalFeature = MutableStateFlow(FeatureConfig())
        override suspend fun setGlobalFeature(config: FeatureConfig) {
            globalFeature.value = config
        }
    }

    private fun tagCount(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().size

    /** ListItem 槽位内的自定义 testTag 只在**非合并树**里可见(AGENTS 实锤条)。 */
    private fun slotTagCount(tag: String) =
        compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun textCount(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().size

    private fun show(
        store: FakeSpoofProfileStore,
        listen: FakeListen = FakeListen(),
        feature: FakeFeature = FakeFeature(),
    ) {
        val vm = SpoofProfileLibraryViewModel(store, listen, feature)
        compose.setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    SpoofProfileLibraryScreen(
                        viewModel = vm,
                        onBack = {},
                        onOpenProfile = {},
                        onCreateProfile = {},
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun builtinStore() = FakeSpoofProfileStore(
        listOf(
            DeviceProfileEntry(
                id = "builtin:pixel-7-pro",
                name = "",
                summary = "",
                profile = DeviceProfile(brand = "google", model = "Pixel 7 Pro", product = "raven", device = "raven"),
                builtinSeedId = "pixel-7-pro",
            ),
            DeviceProfileEntry(
                id = "0f4c1a2b3c4d5e6f7a8b9c0d1e2f3a4b",
                name = "我的档",
                summary = "",
                profile = DeviceProfile(brand = "LGE", model = "Nexus 5X"),
                builtinSeedId = null,
                managed = false,
            ),
        ),
    )

    @Test
    fun rows_onePerProfile_andOnlyBuiltinsCarryTheBadge() {
        val store = builtinStore()
        show(store)

        assertEquals("一行一画像", 1, tagCount("profile-row-builtin:pixel-7-pro"))
        assertEquals("一行一画像", 1, tagCount("profile-row-0f4c1a2b3c4d5e6f7a8b9c0d1e2f3a4b"))
        assertEquals("内置行才有徽章", 1, slotTagCount("profile-badge-builtin-builtin:pixel-7-pro"))
        assertEquals(
            "DIY 行不得挂内置徽章",
            0,
            slotTagCount("profile-badge-builtin-0f4c1a2b3c4d5e6f7a8b9c0d1e2f3a4b"),
        )
        // 展示名:内置取 seedId 资源,DIY 取存储名(两者都不许露出空串或 unknown 兜底)。
        assertTrue(textCount(str(R.string.feature_profile_pixel7pro)) > 0)
        assertTrue(textCount("我的档") > 0)
        assertTrue("未知兜底文案不得上屏", textCount(str(R.string.unknown_error)) == 0)
    }

    @Test
    fun summaries_areDerivedFromTheProfileAndTheOverrideSnapshot() {
        val listen = FakeListen()
        runBlocking {
            listen.setOverride("com.a", AppListenOverride(spoofProfileId = "builtin:pixel-7-pro"))
            listen.setOverride("com.b", AppListenOverride(spoofProfileId = "builtin:pixel-7-pro"))
            listen.setOverride("com.c", AppListenOverride(spoofProfileId = "unrelated"))
        }
        show(builtinStore(), listen)

        assertTrue(
            "摘要必须给出「几项已填」",
            textCount(quantity(R.plurals.profile_summary_filled, 4)) > 0,
        )
        assertTrue(
            "绑定计数须逐行给出(取自 overrides 快照,零 binder 查询)",
            textCount(quantity(R.plurals.profile_summary_bound, 2)) > 0,
        )
        assertEquals("未被绑定的行不得出现绑定计数", 0, textCount(quantity(R.plurals.profile_summary_bound, 1)))
    }

    @Test
    fun legacyChannels_beforeTakeover_areStillEditable() {
        val store = builtinStore()
        val feature = FakeFeature()
        show(store, FakeListen(), feature)

        // 旧三常量档单选 + 字段覆写清单仍在(接管前仍是生效通路)。
        SpoofProfiles.BUILTINS.forEach { preset ->
            assertEquals("档位 ${preset.id} 必须可选", 1, tagCount("profile-option-${preset.id}"))
        }
        assertEquals(0, tagCount("profile-override-key-build.MODEL"))
        assertTrue("覆写为空时必须明说", textCount(str(R.string.profiles_overrides_empty)) > 0)

        runBlocking {
            feature.setGlobalFeature(FeatureConfig(spoofOverrides = mapOf("build.MODEL" to "Mi 14")))
        }
        compose.waitForIdle()
        assertTrue("覆写键必须随行上屏", textCount("build.MODEL") > 0)
        assertEquals(1, tagCount("profile-override-delete-build.MODEL"))
    }

    @Test
    fun emptyLibrary_showsTheEmptyState_notABlankCard() {
        show(FakeSpoofProfileStore())
        assertTrue(textCount(str(R.string.profile_library_empty)) > 0)
        assertTrue(textCount(str(R.string.profile_library_empty_hint)) > 0)
    }

    /**
     * V48-DFC-12:一页三段必须**在名字上就能分清谁是谁**。
     * 旧形态是「画像库 / 画像档位 / 字段覆写」—— 三段都叫"画像/档",而其中两段写的
     * 是另一套状态(遗留常量档 + 覆写字典),用户据此以为在改同一份东西。
     */
    @Test
    fun threeSections_areNamedSoTheLivePathIsNotAmbiguous() {
        show(builtinStore())
        val library = str(R.string.profile_library_section)
        val legacyPresets = str(R.string.profiles_global_section)
        val legacyOverrides = str(R.string.profiles_overrides_section)
        assertEquals(3, setOf(library, legacyPresets, legacyOverrides).size)
        // 两段遗留通路必须自己声明"仍在生效"(Robolectric 默认 locale = en ⇒ 断英文形态),
        // 否则用户会以为它们是待删的残渣。
        assertTrue(legacyPresets, legacyPresets.contains("still in effect"))
        assertTrue(legacyOverrides, legacyOverrides.contains("still in effect"))
        // 新库这一段不许被读成"已接管":它只承诺编辑/校验/绑定。
        assertTrue(library, library.contains("Profile library"))
    }

    /** 接管边界必须**恒在上屏第一屏**说清(AGENTS #30:说「画像已生效于靶程序」即假)。 */
    @Test
    fun takeoverBoundaryIsAlwaysOnScreen() {
        show(builtinStore())
        assertEquals(1, slotTagCount("profile-takeover-status"))
        // V49 WP-D1:这句从「全未接管」的写死散文改成**派生名单** ——
        // 断言因此两件事:名单里的族必须都来自注入侧登记集,且屏上文本就是按该名单组装的那句。
        val resources = org.robolectric.RuntimeEnvironment.getApplication().resources
        val families = takenOverProfileFamilies(resources)
        assertTrue(
            "接管边界必须逐一点名注入侧已登记的画像族",
            com.lioisme.modev.core.model.DeviceEnvKind.entries
                .filter { it.name.startsWith("PROFILE_") }
                .all { families.contains(resources.getString(com.lioisme.modev.ui.components.deviceEnvKindLabelRes(it))) },
        )
        assertTrue(textCount(str(R.string.profile_takeover_status_hint, families)) > 0)
    }
}
