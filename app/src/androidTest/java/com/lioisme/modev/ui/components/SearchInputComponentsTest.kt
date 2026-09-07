package com.lioisme.modev.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lioisme.modev.R
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 定向测试(SearchInputComponents;V40 T5.1 MD3 化重写):
 * 折叠/展开统一 = 标准 `SearchBarDefaults.InputField`(CollapsedSearchInput
 * 退役),槽位语义保留——[onBack] 提供时显示返回,查询非空时显示清除;
 * 空值参数=无该语义(根页)。行为探针:单击/聚焦驱动展开意图(MD3 标准
 * 交互,取代 D4 手搓胶囊的「长按不展开」定制);输入回传。
 */
@RunWith(AndroidJUnit4::class)
class SearchInputComponentsTest {

    @get:Rule
    val rule = createComposeRule()

    private val filterCd =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.cd_filter)
    private val backCd =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.cd_back)
    private val clearCd =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.clear_search)

    private fun filterSlot(): @androidx.compose.runtime.Composable () -> Unit = {
        TipIconButton(tooltip = "Filter", onClick = {}) {
            // 图标仅作按钮锚点(内容无意义):用 core 已验证可解析的 Star。
            Icon(Icons.Default.Star, contentDescription = filterCd)
        }
    }

    /** 组合内读取查询状态(重组由状态驱动,替身不得在组合外取值)。 */
    private fun setInput(
        queryState: MutableState<String>,
        onBack: (() -> Unit)? = null,
        leadingSlot: (@androidx.compose.runtime.Composable () -> Unit)? = null,
        onActiveChange: (Boolean) -> Unit = {},
    ) {
        rule.setContent {
            MaterialTheme {
                SearchInputField(
                    query = queryState.value,
                    onQueryChange = { queryState.value = it },
                    active = false,
                    onActiveChange = onActiveChange,
                    onSearch = {},
                    onBack = onBack,
                    placeholder = "Search…",
                    filterSlot = filterSlot(),
                    leadingSlot = leadingSlot,
                )
            }
        }
    }

    @Test
    fun collapsed_hidesBackAndClearWhenNotProvided_showsPlaceholderWhenEmpty() {
        setInput(mutableStateOf(""))
        rule.onNodeWithText("Search…").assertIsDisplayed()
        // 根页规范:仅筛选按钮一个可交互图标(无返回、无清除、无前导槽)。
        rule.onAllNodesWithContentDescription(filterCd).assertCountEquals(1)
        rule.onAllNodesWithContentDescription(backCd).assertCountEquals(0)
        rule.onAllNodesWithContentDescription(clearCd).assertCountEquals(0)
    }

    @Test
    fun collapsed_showsBackAndClearWhenProvided() {
        val query = mutableStateOf("abc")
        setInput(query, onBack = {})
        rule.onNodeWithText("abc").assertIsDisplayed()
        rule.onAllNodesWithContentDescription(backCd).assertCountEquals(1)
        rule.onAllNodesWithContentDescription(clearCd).assertCountEquals(1)
        rule.onAllNodesWithContentDescription(filterCd).assertCountEquals(1)
        // 点击清除 → 查询置空(清除按钮随之隐藏,状态驱动重组)。
        rule.onNodeWithContentDescription(clearCd).performClick()
        rule.runOnIdle { assertTrue(query.value.isEmpty()) }
        rule.waitForIdle()
        rule.onAllNodesWithContentDescription(clearCd).assertCountEquals(0)
    }

    @Test
    fun collapsed_leadingSlot_replacesBack() {
        var leadingClicked = 0
        setInput(
            mutableStateOf(""),
            onBack = {},
            leadingSlot = {
                TipIconButton(tooltip = "Settings", onClick = { leadingClicked++ }) {
                    Icon(Icons.Default.Star, contentDescription = "settings-slot")
                }
            },
        )
        // leadingSlot 优先于 onBack(根页设置按钮形态)。
        rule.onAllNodesWithContentDescription(backCd).assertCountEquals(0)
        rule.onNodeWithContentDescription("settings-slot").performClick()
        rule.runOnIdle { assertTrue(leadingClicked == 1) }
        rule.onAllNodesWithContentDescription(filterCd).assertCountEquals(1)
    }

    @Test
    fun tap_expandsViaFocusIntent() {
        // 行为探针(MD3 标准):折叠态单击/聚焦由 InputField 焦点回调驱动
        // onExpandedChange(true)——这是 MD3 SearchBar 的标准交互(取代 D4 手搓
        // 胶囊的「长按不展开」定制;长按选词同样聚焦展开 = 标准形态,契约已更新)。
        var expansions = 0
        setInput(
            mutableStateOf(""),
            onActiveChange = { if (it) expansions++ },
        )
        rule.onNodeWithText("Search…").performClick()
        rule.runOnIdle { assertTrue("单击/聚焦须驱动展开意图", expansions >= 1) }
    }

    @Test
    fun inputField_showsBackAndClearWhileTyping_hidesClearWhenEmpty() {
        val query = mutableStateOf("")
        setInput(query, onBack = {})
        // 查询空:返回在,清除不在。
        rule.onAllNodesWithContentDescription(backCd).assertCountEquals(1)
        rule.onAllNodesWithContentDescription(clearCd).assertCountEquals(0)
        rule.onNode(hasSetTextAction()).performTextInput("abc")
        rule.runOnIdle { assertTrue(query.value == "abc") }
        rule.waitForIdle()
        // 输入后:清除出现,筛选仍在。
        rule.onAllNodesWithContentDescription(clearCd).assertCountEquals(1)
        rule.onAllNodesWithContentDescription(filterCd).assertCountEquals(1)
    }

    @Test
    fun inputField_hidesBackWhenNotProvided() {
        setInput(mutableStateOf(""), onBack = null)
        rule.onAllNodesWithContentDescription(backCd).assertCountEquals(0)
        rule.onAllNodesWithContentDescription(filterCd).assertCountEquals(1)
    }
}
