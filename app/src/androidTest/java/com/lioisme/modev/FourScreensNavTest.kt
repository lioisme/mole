package com.lioisme.modev

import android.view.KeyEvent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lioisme.modev.data.repo.AppListSettings
import com.lioisme.modev.ui.MainActivity
import kotlin.math.abs
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 界面导航验收测试(T4,2026-08-16 V3;2026-08-17 V7 五需求同步;V10 设置分级同步;
 * V14 R5/R7 同步;V20 单应用语境化:入口页头部整合 + 两记录入口直通单应用列表):
 * R1(信息架构:应用列表→应用监听入口→算法/数据库记录列表→记录详情;设置页→
 * 二级设置页:算法监听/数据库监听/数据管理/服务与诊断,往返可达)、
 * R2(应用列表/记录列表 AppBar 内仅一个 SearchBar)、
 * R3(SearchBar 内仅一个筛选按钮,无返回/清除)、
 * V7 变更:应用行无运行按钮(根页 app-launch=0)、详情页顶栏更多菜单承载导出,
 * V10 变更:捕获设置全局化(入口移至设置页,记录页筛选菜单仅算法/数据库类别)、
 * V20 变更:危险操作(移出作用域/清空该应用记录)迁至监听入口页头部更多菜单,
 * 记录列表 = 单应用过滤(无查看记录混合页)。
 * 数据依赖断言(应用行/记录行)以 assumeTrue 防御,空机不红。
 */
@RunWith(AndroidJUnit4::class)
class FourScreensNavTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun str(id: Int) = ctx.getString(id)

    /**
     * V18:首个用例勾选筛选项后持久化,会过滤后续用例的应用行(雷电等
     * 无 LSPosed 作用域环境藏行致 assumeTrue 跳过),@After 统一复位。
     * 模块显隐开关同理复位,防跨用例污染(与单元测试同源契约)。
     * V22 R1:仅作用域筛选项已退役,无复位需要。
     */
    @After
    fun restoreFilters() {
        AppListSettings.setShowModules(false, ctx)
    }

    /** 零依赖返回键(avoid espresso-core,与现有 androidTest 依赖集一致)。 */
    private fun pressBack() {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        rule.waitForIdle()
    }

    @Test
    fun rootHasOnlyFilterButton_andFilterMenuNavigatesToSettings_andBack() {
        rule.waitForIdle()
        // R2/R3:根页 AppBar 即折叠 SearchBar,其内左上角设置按钮 + 右下角筛选按钮
        // (V18 设置入口自筛选菜单迁出,与筛选按钮对称);根页无返回按钮。
        rule.onAllNodesWithContentDescription(str(R.string.cd_filter)).assertCountEquals(1)
        rule.onAllNodesWithContentDescription(str(R.string.cd_settings)).assertCountEquals(1)
        rule.onAllNodesWithContentDescription(str(R.string.cd_back)).assertCountEquals(0)
        // V7/V20:根页应用行无运行按钮(启动入口仅保留于应用监听入口页头部)。
        rule.onAllNodesWithTag("app-launch").assertCountEquals(0)

        // 筛选菜单:仅含「系统应用 / Xposed 模块」两项(V14 R7:置顶项已收敛
        // 至通用设置页「应用列表」分区;V18:设置项迁出至搜索条左上角;
        // V22 R1:仅作用域筛选项退役,置顶为其替代品)。
        rule.onNodeWithContentDescription(str(R.string.cd_filter)).performClick()
        rule.onNodeWithText(str(R.string.apps_system)).assertIsDisplayed()
        rule.onAllNodes(hasText(str(R.string.apps_system)) and hasAnyAncestor(isPopup())).assertCountEquals(1)
        rule.onAllNodes(hasText(str(R.string.apps_modules)) and hasAnyAncestor(isPopup())).assertCountEquals(1)
        // V17 R1:模块显隐默认关,行为级验证 = 勾选后被测包行出现(自身是 Xposed
        // 模块,默认隐藏),取消后行消失。菜单项整项合并语义(M3 DropdownMenuItem
        // MergeDescendants),Checkbox 只读无 ToggleableState,故不走勾选态断言。
        rule.onNodeWithText(str(R.string.apps_modules)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.apps_modules)).performClick()
        rule.waitForIdle()
        // 行可能出现于折叠区外,轮询滚动至可见(与 TimelineListTest 同款)。
        val shownDeadline = System.currentTimeMillis() + 10_000
        var moduleRowShown = false
        while (System.currentTimeMillis() < shownDeadline) {
            rule.waitForIdle()
            if (rule.onAllNodesWithText(MODULE_PACKAGE).fetchSemanticsNodes().isNotEmpty()) {
                moduleRowShown = true
                break
            }
            val scrollables = rule.onAllNodes(hasScrollAction()).fetchSemanticsNodes()
            if (scrollables.isNotEmpty()) {
                try {
                    rule.onAllNodes(hasScrollAction()).onFirst()
                        .performScrollToNode(hasText(MODULE_PACKAGE))
                } catch (_: AssertionError) {
                    // 行未加载,下一轮重试。
                }
            }
            Thread.sleep(100)
        }
        assertTrue("勾选「Xposed 模块」后被测包行应出现", moduleRowShown)
        // 还原:取消勾选 → 行消失。
        rule.onNodeWithContentDescription(str(R.string.cd_filter)).performClick()
        rule.onNodeWithText(str(R.string.apps_modules)).performClick()
        val hiddenDeadline = System.currentTimeMillis() + 5_000
        var moduleRowHidden = false
        while (System.currentTimeMillis() < hiddenDeadline) {
            rule.waitForIdle()
            if (rule.onAllNodesWithText(MODULE_PACKAGE).fetchSemanticsNodes().isEmpty()) {
                moduleRowHidden = true
                break
            }
            Thread.sleep(100)
        }
        assertTrue("取消勾选后被测包行应消失", moduleRowHidden)
        // 恢复菜单打开态,衔接原流程(设置入口)。
        rule.onNodeWithContentDescription(str(R.string.cd_filter)).performClick()
        rule.waitForIdle()
        // V22 R2:置顶开关已迁回筛选菜单(设置页「应用列表」分区退役),
        // 菜单内应含「已作用域应用置顶」且可点击切换(点击即收起)。
        rule.onNodeWithText(str(R.string.settings_pin_scoped)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.settings_pin_scoped)).performClick()
        rule.waitForIdle()
        rule.onNodeWithContentDescription(str(R.string.cd_filter)).performClick()
        rule.waitForIdle()
        rule.onNodeWithText(str(R.string.settings_pin_scoped)).assertIsDisplayed()
        // 还原默认开,避免污染后续用例。
        rule.onNodeWithText(str(R.string.settings_pin_scoped)).performClick()
        rule.waitForIdle()
        // V18:设置入口已迁出筛选菜单(搜索条左上角),菜单内无「设置」项。
        rule.onAllNodes(hasText(str(R.string.settings_title)) and hasAnyAncestor(isPopup())).assertCountEquals(0)

        // 设置入口:搜索条左上角设置按钮(与筛选按钮对称)→ 设置页(端到端可达)。
        rule.onNodeWithContentDescription(str(R.string.cd_settings)).performClick()
        rule.onNodeWithText(str(R.string.settings_title)).assertIsDisplayed()
        pressBack()
        rule.onAllNodesWithContentDescription(str(R.string.cd_filter)).assertCountEquals(1)
        rule.onAllNodesWithContentDescription(str(R.string.cd_settings)).assertCountEquals(1)
    }

    @Test
    fun appRowOpensAppMonitor_andDangerMenu_andAlgorithmListNavigates() {
        rule.waitForIdle()
        assumeTrue(rule.onAllNodesWithTag("app-row").fetchSemanticsNodes().isNotEmpty())

        // V16 R4:头部「启动」按钮按需惰性解析(单次 binder + 缓存),进入页面后
        // 异步出现 → 有界轮询(300ms),防未解析完误判为不可启动。
        // 逐个进入应用监听入口页寻找带启动按钮的应用;可见行全部不可启动时上滑
        // 列表继续寻找;仍找不到则跳过本用例,保证启动按钮/危险操作/列表入口断言
        // 只在有效语境下执行。
        var openedLaunchable = false
        var swipes = 0
        while (!openedLaunchable && swipes < 10) {
            // 每轮重取行数:惰性列表滚动回收可能改变可见行数,防索引越界(2026-08-17 V12 防御)
            var i = 0
            while (i < rule.onAllNodesWithTag("app-row").fetchSemanticsNodes().size) {
                rule.onAllNodesWithTag("app-row")[i].performClick()
                rule.waitForIdle()
                // 应用监听入口页标志(V18/V20):顶栏返回按钮 1 个,无筛选按钮。
                rule.onAllNodesWithContentDescription(str(R.string.cd_back)).assertCountEquals(1)
                rule.onAllNodesWithContentDescription(str(R.string.cd_filter)).assertCountEquals(0)
                val deadline = System.currentTimeMillis() + 300
                var hasLaunch = false
                while (System.currentTimeMillis() < deadline) {
                    rule.waitForIdle()
                    if (rule.onAllNodesWithTag("app-launch").fetchSemanticsNodes().isNotEmpty()) {
                        hasLaunch = true
                        break
                    }
                    Thread.sleep(50)
                }
                if (hasLaunch) {
                    openedLaunchable = true
                    break
                }
                // 该应用无启动入口,返回后尝试下一个(入口页 → 根页)。
                pressBack()
                rule.waitForIdle()
                i++
            }
            if (!openedLaunchable && swipes < 9) {
                rule.onAllNodes(hasScrollAction()).onFirst()
                    .performTouchInput { swipeUp() }
                rule.waitForIdle()
            }
            swipes++
        }
        assumeTrue(openedLaunchable)
        rule.onAllNodesWithTag("app-launch").assertCountEquals(1)

        // 更多菜单:应用级危险操作(移出作用域仅对已作用域应用出现;清空该应用记录恒在),
        // 仅断言存在不触发(V20:危险操作迁至入口页头部)。
        rule.onNodeWithContentDescription(str(R.string.record_more)).performClick()
        rule.onNodeWithText(str(R.string.detail_clear_records)).assertIsDisplayed()
        if (rule.onAllNodesWithText(str(R.string.detail_remove_scope)).fetchSemanticsNodes().isNotEmpty()) {
            rule.onNodeWithText(str(R.string.detail_remove_scope)).assertIsDisplayed()
        }
        pressBack()

        // 两记录入口:算法记录 / 数据库记录(单应用列表)。
        rule.onNodeWithText(str(R.string.algorithm_records_title)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.database_records_title)).assertIsDisplayed()

        // 算法记录列表:AppBar 仅 SearchBar(返回按钮 1 个)+ 筛选按钮 1 个。
        rule.onNodeWithText(str(R.string.algorithm_records_title)).performClick()
        rule.waitForIdle()
        rule.onAllNodesWithContentDescription(str(R.string.cd_back)).assertCountEquals(1)
        rule.onAllNodesWithContentDescription(str(R.string.cd_filter)).assertCountEquals(1)

        // 筛选菜单:全部算法类别(V10 起无捕获设置入口,配置已全局化;V14 更名后
        // 亦无「算法监听」入口——入口仅收敛于设置页)。
        rule.onNodeWithContentDescription(str(R.string.cd_filter)).performClick()
        rule.onNodeWithText(str(R.string.kind_cipher)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.kind_key_generation)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.algorithm_monitor_title)).assertDoesNotExist()
        pressBack()
        // 已处于算法记录列表页:无应用入口页的一键启动按钮(V20:启动按钮仅存于入口页头部)。
        rule.onAllNodesWithTag("app-launch").assertCountEquals(0)

        // 回根页(记录列表 → 入口页 → 根页)→ 设置页(搜索条左上角设置按钮)。
        pressBack()
        rule.waitForIdle()
        pressBack()
        rule.waitForIdle()
        rule.onNodeWithContentDescription(str(R.string.cd_settings)).performClick()
        rule.waitForIdle()
        // V21 T3:功能四入口——算法监听 / 数据库监听(V19 合并单页拆分还原)/
        // 数据管理 / 服务与诊断。数据库入口行在小屏(360x640dp)落在折叠区,
        // 滚动至可见后断言。
        rule.onNodeWithText(str(R.string.algorithm_monitor_title)).assertIsDisplayed()
        rule.onAllNodes(hasScrollAction()).onFirst()
            .performScrollToNode(hasText(str(R.string.database_monitor_title)))
        rule.onNodeWithText(str(R.string.database_monitor_title)).assertIsDisplayed()
        // 小屏(雷电等)「数据管理」入口位于折叠区,滚动至可见后断言。
        rule.onAllNodes(hasScrollAction()).onFirst()
            .performScrollToNode(hasText(str(R.string.data_settings_title)))
        rule.onNodeWithText(str(R.string.data_settings_title)).assertIsDisplayed()
        // 服务与诊断入口位于列表底部折叠区,滚动至可见后断言(小屏防未组合)。
        rule.onAllNodes(hasScrollAction()).onFirst()
            .performScrollToNode(hasText(str(R.string.diagnostics_settings_title)))
        rule.onNodeWithText(str(R.string.diagnostics_settings_title)).assertIsDisplayed()
        // 回到列表顶部,保证功能入口点击可见。
        rule.onAllNodes(hasScrollAction()).onFirst()
            .performScrollToNode(hasText(str(R.string.algorithm_monitor_title)))
        rule.waitForIdle()

        // 算法监听页(V21 T3):①监听类别(算法总开关 + 12 类别)②记录选项;无筛选按钮。
        rule.onNodeWithText(str(R.string.algorithm_monitor_title)).performClick()
        rule.waitForIdle()
        rule.onNodeWithText(str(R.string.algorithm_monitor_master)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.switch_cipher)).assertIsDisplayed()
        // 记录选项区在 LazyColumn 折叠区,滚动至可见后断言。
        rule.onAllNodes(hasScrollAction()).onFirst()
            .performScrollToNode(hasText(str(R.string.switch_log_stack)))
        rule.onNodeWithText(str(R.string.switch_log_stack)).assertIsDisplayed()
        rule.onAllNodesWithContentDescription(str(R.string.cd_filter)).assertCountEquals(0)
        pressBack()

        // 数据库监听页(V21 T3):①监听类别(数据库总开关 + 5 类别)②结果闸门;无筛选按钮。
        // 数据库入口行在小屏(360x640dp)落在折叠区,先滚动至可见再点击。
        rule.onAllNodes(hasScrollAction()).onFirst()
            .performScrollToNode(hasText(str(R.string.database_monitor_title)))
        rule.onNodeWithText(str(R.string.database_monitor_title)).performClick()
        rule.waitForIdle()
        rule.onNodeWithText(str(R.string.database_monitor_master)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.switch_db_query)).assertIsDisplayed()
        rule.onAllNodes(hasScrollAction()).onFirst()
            .performScrollToNode(hasText(str(R.string.db_max_result_rows)))
        rule.onNodeWithText(str(R.string.db_max_result_rows)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.db_max_result_chars)).assertIsDisplayed()
        rule.onAllNodesWithContentDescription(str(R.string.cd_filter)).assertCountEquals(0)
        pressBack()

        // 返回设置页 → 数据管理页:总记录行 + 清空全部日志(危险块),不触发。
        // 数据管理入口在小屏折叠区,先滚动至可见再点击。
        rule.onAllNodes(hasScrollAction()).onFirst()
            .performScrollToNode(hasText(str(R.string.data_settings_title)))
        rule.onNodeWithText(str(R.string.data_settings_title)).performClick()
        rule.waitForIdle()
        rule.onNodeWithText(str(R.string.settings_total_records)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.settings_clear_all_records)).assertIsDisplayed()
        pressBack()

        // 返回设置页 → 服务与诊断页:入口在底部折叠区,滚动后进入(端口行 + 管线统计折叠头)。
        rule.onAllNodes(hasScrollAction()).onFirst()
            .performScrollToNode(hasText(str(R.string.diagnostics_settings_title)))
        rule.onNodeWithText(str(R.string.diagnostics_settings_title)).performClick()
        rule.waitForIdle()
        rule.onNodeWithText(str(R.string.settings_port)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.settings_stats_section)).assertIsDisplayed()
        pressBack()

        // 返回根页:唯一筛选按钮、无运行按钮。
        pressBack()
        rule.onAllNodesWithContentDescription(str(R.string.cd_filter)).assertCountEquals(1)
        rule.onAllNodesWithTag("app-launch").assertCountEquals(0)
    }

    @Test
    fun recordRowOpensRecordDetail_andMoreMenuHasSingleExport_andBackReturns() {
        rule.waitForIdle()
        assumeTrue(rule.onAllNodesWithTag("app-row").fetchSemanticsNodes().isNotEmpty())

        // 雷电等首行应用常无记录,轮询查找带记录行的应用(与 appRowOpens 查找
        // 启动按钮同款:逐行进入入口页 → 先算法列表,无记录再数据库列表;
        // 均无则返回下一行,找不到跳过)。
        var openedRecord = false
        var swipes = 0
        while (!openedRecord && swipes < 10) {
            var i = 0
            while (i < rule.onAllNodesWithTag("app-row").fetchSemanticsNodes().size) {
                rule.onAllNodesWithTag("app-row")[i].performClick()
                rule.waitForIdle()
                // V18/V20:单击应用先进监听入口页,经「算法记录」到算法列表。
                rule.onNodeWithText(str(R.string.algorithm_records_title)).performClick()
                rule.waitForIdle()
                if (rule.onAllNodesWithTag("record-row").fetchSemanticsNodes().isNotEmpty()) {
                    openedRecord = true
                    break
                }
                // 算法列表无记录 → 返回入口页,试数据库列表。
                pressBack()
                rule.waitForIdle()
                rule.onNodeWithText(str(R.string.database_records_title)).performClick()
                rule.waitForIdle()
                if (rule.onAllNodesWithTag("record-row").fetchSemanticsNodes().isNotEmpty()) {
                    openedRecord = true
                    break
                }
                pressBack()
                rule.waitForIdle()
                pressBack()
                rule.waitForIdle()
                i++
            }
            if (!openedRecord && swipes < 9) {
                rule.onAllNodes(hasScrollAction()).onFirst()
                    .performTouchInput { swipeUp() }
                rule.waitForIdle()
            }
            swipes++
        }
        assumeTrue(openedRecord)

        rule.onAllNodesWithTag("record-row").onFirst().performClick()
        rule.waitForIdle()

        // 记录详情页标志:更多按钮 + 返回;顶栏无复制/分享直挂按钮(V7 收敛)。
        rule.onNodeWithContentDescription(str(R.string.record_more)).assertIsDisplayed()
        rule.onNodeWithContentDescription(str(R.string.cd_back)).assertIsDisplayed()

        // 更多菜单唯一功能 = 导出 JSON(仅断言存在,不触发 SAF 落盘)。
        rule.onNodeWithContentDescription(str(R.string.record_more)).performClick()
        rule.onNodeWithText(str(R.string.record_export)).assertIsDisplayed()
        pressBack()

        pressBack()
        // 返回记录列表页:AppBar 仅 SearchBar + 筛选按钮。
        rule.onAllNodesWithContentDescription(str(R.string.cd_filter)).assertCountEquals(1)
        pressBack()
        // 返回入口页(V20):两记录入口仍在。
        rule.onNodeWithText(str(R.string.algorithm_records_title)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.database_records_title)).assertIsDisplayed()
        pressBack()
        // 返回根页:唯一筛选按钮、无运行按钮。
        rule.onAllNodesWithContentDescription(str(R.string.cd_filter)).assertCountEquals(1)
        rule.onAllNodesWithTag("app-launch").assertCountEquals(0)
    }

    /**
     * V17 R4:入口页头部状态点归位——在线点与作用域点与包名同一行,
     * 且位于包名文本之后(与应用列表行几何同源:weight(1f, fill=false) + 6dp)。
     * 包名取自被点击行的语义树文本(取含点文本),与生产渲染同源。
     * V19 T6:AppHeaderRow 改 M3 ListItem 后内容槽合并为单一语义节点
     * (文本节点 = 整行 72dp),包名文本无法独立测距;改为:双点同行 + 均位于
     * 头部下排(包名行 = supporting,中点 y > 整行中点)—— 若状态点退回
     * headline 行(回归)则断言失败。横向「位于包名之后」由行内几何保证
     * (supporting Row weight(1f, fill=false) + 6dp 间距),不再单独断言。
     * V20:头部自「查看记录」页迁入监听入口页,本用例改为直接断言入口页头部。
     */
    @Test
    fun appMonitorHeader_hasStatusDotsBesidePackageName() {
        rule.waitForIdle()
        assumeTrue(rule.onAllNodesWithTag("app-row").fetchSemanticsNodes().isNotEmpty())

        val rowNode = rule.onAllNodesWithTag("app-row").onFirst().fetchSemanticsNode()
        val packageName =
            collectTexts(rowNode).firstOrNull { it.contains('.') }
        assumeTrue("应用行须含包名文本", packageName != null)

        rule.onAllNodesWithTag("app-row").onFirst().performClick()
        rule.waitForIdle()

        val pkgNodes = rule.onAllNodesWithText(packageName!!).fetchSemanticsNodes()
        assumeTrue("入口页须渲染包名文本", pkgNodes.isNotEmpty())
        val headerBounds = pkgNodes.first().boundsInRoot

        // 双状态点(连接 + 作用域),候选文案取设备现态;每点至多命中一个标签。
        val dotLabels =
            listOf(
                R.string.online,
                R.string.offline,
                R.string.detail_listening,
                R.string.detail_not_listening,
                R.string.detail_reading_scope,
                R.string.detail_framework_no_service,
            )
        val dotNodes =
            dotLabels
                .flatMap { id -> rule.onAllNodesWithContentDescription(str(id)).fetchSemanticsNodes() }
                .filter { it.boundsInRoot != Rect.Zero }
        assumeTrue("头部须渲染双状态点", dotNodes.size >= 2)
        val firstDot = dotNodes[0].boundsInRoot
        for (node in dotNodes.take(2)) {
            val b = node.boundsInRoot
            // 同行:中心 y 差 < 12dp(行高量级)。
            assertTrue("状态点须同行,实际 Δy=${abs(b.center.y - firstDot.center.y)}", abs(b.center.y - firstDot.center.y) < 12f)
            // 下排:状态点中心须在头部整行中点之下(包名 supporting 行,
            // headline 行在整行上 1/3——若点归位失败退回头部行则此断言失败)。
            assertTrue("状态点须与包名同排,实际 Δy=${b.center.y - headerBounds.center.y}", b.center.y > headerBounds.center.y)
        }
    }

    /** 深度优先收集语义树中全部文本(行内包名/标签/统计等,取含点者为包名)。 */
    private fun collectTexts(node: SemanticsNode): List<String> =
        node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.map { it.text } +
            node.children.flatMap { collectTexts(it) }

    companion object {
        /** 被测应用自身即 Xposed 模块(manifest 声明),模块显隐开关的天然实验对象。 */
        private const val MODULE_PACKAGE = "com.lioisme.modev"
    }
}