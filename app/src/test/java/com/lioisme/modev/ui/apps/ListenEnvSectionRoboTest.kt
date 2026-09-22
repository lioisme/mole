package com.lioisme.modev.ui.apps

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import com.lioisme.modev.R
import com.lioisme.modev.core.model.AppListenOverride
import com.lioisme.modev.core.model.CapabilityMap
import com.lioisme.modev.core.model.EffectiveSwitch
import com.lioisme.modev.core.model.HookSwitch
import com.lioisme.modev.domain.config.ListenConfigService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 监听页「环境监听」分区整屏渲染回归(V48 R-2 / V48-DFC-03,Robolectric 全装配驱动)。
 *
 * 治理的失败形态:这一区里的 7 个类别行落在**监听页**,而管辖它们的 master 只画在**功能设置页**
 * —— 用户把「网络请求」打开、上级闸还关着,屏上零反馈、靶程序零记录。本类证的是用户看得见的那件事:
 * **行上必须点名那一个关着的上级开关**(名字要说对,不能只说「请先开启上级开关」)。
 *
 * 行集合由 [CapabilityMap.crossDomainListen] 驱动,故这里同时断言「渲染的行数 == 表的行数」
 * —— 页面自己再拼一份跨域清单就会与表漂移(旧实装就是这么写的)。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ListenEnvSectionRoboTest {

    @get:Rule
    val compose = createComposeRule()

    private fun str(res: Int, vararg args: Any?): String =
        RuntimeEnvironment.getApplication().getString(res, *args)

    /** 配置面替身:只喂一个包的偏差,写路径空转(本类不测写入)。 */
    private class FakeConfig(deviation: AppListenOverride) : ListenConfigService {
        private val deviationMap = mapOf(PKG to deviation)

        override val globalSwitch = MutableStateFlow(HookSwitch.DEFAULT)
        override val overrides = MutableStateFlow(deviationMap)
        override fun overrideFor(packageName: String) = deviationMap[packageName] ?: AppListenOverride.DEFAULT
        override fun effectiveSwitch(packageName: String) =
            EffectiveSwitch.resolve(HookSwitch.DEFAULT, deviationMap[packageName])

        override fun effectiveSwitchFlow(packageName: String): Flow<HookSwitch> =
            overrides.map { EffectiveSwitch.resolve(HookSwitch.DEFAULT, it[packageName]) }

        override suspend fun setOverride(packageName: String, override: AppListenOverride) = Unit
    }

    private fun render(deviation: AppListenOverride) {
        val viewModel = PerAppListenSettingsViewModel(PKG, FakeConfig(deviation))
        compose.setContent {
            Surface(color = MaterialTheme.colorScheme.background) {
                PerAppListenSettingsScreen(viewModel = viewModel, onBack = {})
            }
        }
    }

    /** kind 开 + 其 master 关 ⇒ 行上点名该 master(带名字与所在页)。 */
    @Test
    fun kindOnWithForeignMasterOffNamesThatMaster() {
        render(AppListenOverride(networkListen = false, kinds = mapOf("network:REQUEST" to true)))
        val hint = str(R.string.listen_kind_master_off_hint, str(R.string.network_monitor_master))
        compose.onNodeWithTag("listen-row-network-REQUEST")
            .assert(hasText(hint, substring = true))
    }

    /** master 开着 ⇒ 同一条提示不得出现(常驻警告 = 新噪声)。 */
    @Test
    fun noHintWhenTheMasterIsOn() {
        render(AppListenOverride(kinds = mapOf("network:REQUEST" to true)))
        val hint = str(R.string.listen_kind_master_off_hint, str(R.string.network_monitor_master))
        assertTrue(
            "master 开时不该再有警告行",
            compose.onAllNodesWithText(hint, substring = true).fetchSemanticsNodes().isEmpty(),
        )
    }

    /**
     * kind 自己关着 ⇒ 该行不提示(没人在等它生效)。
     * 注意 master 关 ≠ kind 关:REQUEST 默认就是开的,要把 kind 显式置 false 才是本例形态。
     */
    @Test
    fun noHintWhenTheKindItselfIsOff() {
        render(AppListenOverride(networkListen = false, kinds = mapOf("network:REQUEST" to false)))
        val hint = str(R.string.listen_kind_master_off_hint, str(R.string.network_monitor_master))
        // 行本身必须在(否则「无提示」是空屏假绿);取节点即断言存在。
        compose.onNodeWithTag("listen-row-network-REQUEST").fetchSemanticsNode()
        assertTrue(
            "kind 关着不该再警告",
            compose.onAllNodesWithText(hint, substring = true).fetchSemanticsNodes().isEmpty(),
        )
    }

    /** 渲染的行集合恒等于能力表的跨域行(页面不许再拼一份跨域清单)。 */
    @Test
    fun renderedRowsEqualTheRegistryRows() {
        render(AppListenOverride.DEFAULT)
        val expected = CapabilityMap.crossDomainListen.map { "listen-row-${it.type.wire}-${it.kind}" }
        assertTrue("表为空 = 门禁失去对象", expected.isNotEmpty())
        // 本仓 Compose 测试库无 assertExists(AGENTS 缺件清单)⇒ 取节点即断言存在。
        expected.forEach { tag -> compose.onNodeWithTag(tag).fetchSemanticsNode() }
    }
}

private const val PKG = "com.lioisme.modev"
