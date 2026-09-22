package com.lioisme.modev

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.fail

/*
 * 屏级锚点探针(V45 DEF-V45-009 取证沉淀)。
 * 插桩导航链一旦断裂,后续断言抛出的是一份「与根因无关的容器语义转储」
 * (例如停在 A 屏却在找 B 屏的行文本),归因只能靠几何猜。本探针把
 * 「等锚点 → 超时 → 报告当前停在哪一屏」收口成一次调用,失败信息自带定位。
 */

/**
 * 每屏一枚「本屏恒在、他屏不在」的 testTag 登记表。
 * 新增屏幕只需在此登记一行,不必为诊断另造标记。
 */
private val screenAnchors: List<Pair<String, String>> = listOf(
    "app-row" to "应用列表",
    "monitor-entry-algorithm" to "应用监控页",
    "records-filter" to "记录列表页",
    "detail-more" to "记录详情页",
    "settings-page" to "设置页",
    "services-external-access" to "服务页",
)

/** 当前停住的屏(按登记表命中拼接;全不命中 = 未登记屏)。 */
fun ComposeTestRule.currentScreenLabel(): String {
    val hit = screenAnchors
        .filter { onAllNodesWithTag(it.first).fetchSemanticsNodes().isNotEmpty() }
        .map { it.second }
    return if (hit.isEmpty()) "<未登记屏>" else hit.joinToString("+")
}

/**
 * 有界等待页面锚点 [tag] 出现;超时即失败,并把「当前屏 + 已到位锚点」写进消息。
 * [note] 应描述这一步的预期(点哪一行 → 应到哪一屏)。
 */
fun ComposeTestRule.awaitAnchor(
    tag: String,
    note: String,
    timeoutMs: Long = 10_000,
) {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
        waitForIdle()
        if (onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()) return
        Thread.sleep(100)
    }
    fail("$note —— 锚点 '$tag' 在 ${timeoutMs}ms 内未出现;当前停在:${currentScreenLabel()}")
}

/**
 * 先滚进视口再点击。视口外节点的 `performClick` 坐标落在窗口外,触摸被静默丢弃
 * ——零异常、零导航,失败只在下一屏的断言上炸开(V40 T4 在监控页入口行实锤,
 * DEF-V45-009 在同一坑的另两处复现)。
 * `performScrollTo` 对已可见节点在部分版本抛断言错,故吞掉后再点。
 */
fun ComposeTestRule.clickScrolledIntoView(target: SemanticsMatcher) {
    try {
        onNode(target).performScrollTo()
    } catch (_: AssertionError) {
        // 已在视口内。
    }
    waitForIdle()
    onNode(target).performClick()
}

/**
 * 根页 → 设置页前置。V41 根页 IA 五分区把主题色板/服务/端口等行移出根页后,
 * 三类用例仍直接按文案点这些行(拿到的恒是应用列表)⇒ 一律先走本前置,
 * 失败信息由锚点给出「停在哪一屏」,不再是一句「找不到节点」。
 */
fun ComposeTestRule.openSettings(settingsCd: String) {
    onAllNodesWithContentDescription(settingsCd).onFirst().performClick()
    awaitAnchor("settings-page", "点顶栏设置入口应进设置页")
}

/** 根页 → 设置页 → 服务页(端口 sheet 类用例的前置)。 */
fun ComposeTestRule.openServices(
    settingsCd: String,
    servicesTitle: String,
) {
    openSettings(settingsCd)
    clickScrolledIntoView(hasText(servicesTitle))
    awaitAnchor("services-external-access", "点「服务」行应进服务页")
}

/**
 * 有界等待「全屏搜索 Dialog 内的输入域」出现。
 * Dialog 是**新窗口**,单次 `waitForIdle()` 不保证该窗口的首帧已组合(模拟器高负载下
 * 尤其如此),旧写法的「点折叠条 → waitForIdle → onNode(SetText && isDialog)」因此在
 * 负载 6+ 的机器上随机红。超时消息同时排除「展开意图被 V42 槽按压宽限窗抑制」这一说:
 * 若意图被抑制,Dialog 恒不出现,消息即定位到那一步。
 */
fun ComposeTestRule.awaitSearchDialogInput(note: String, timeoutMs: Long = 8_000) {
    val matcher = hasSetTextAction() and hasAnyAncestor(isDialog())
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
        waitForIdle()
        if (onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()) return
        Thread.sleep(100)
    }
    fail("$note —— Dialog 内搜索输入域在 ${timeoutMs}ms 内未出现(展开未生效);当前停在:${currentScreenLabel()}")
}
