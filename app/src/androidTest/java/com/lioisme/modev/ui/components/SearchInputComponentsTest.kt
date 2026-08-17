package com.lioisme.modev.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lioisme.modev.R
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 定向测试(SearchInputComponents,2026-08-16 LIDE 风格对齐):
 * SearchBar 内部左右允许控件按钮(LIDE 同款)——[onBack] 提供时显示返回,
 * 查询非空且 [onClear] 提供时显示清除;空值参数=无该语义(根页)。
 * 折叠条单击展开 / 长按无副作用(状态机红线)/ 输入回传。
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

    @Test
    fun collapsed_hidesBackAndClearWhenNotProvided_showsPlaceholderWhenEmpty() {
        rule.setContent {
            MaterialTheme {
                CollapsedSearchInput(
                    query = "",
                    // 根页:无返回语义;查询空:无清除。
                    onBack = null,
                    onClear = null,
                    onTapToExpand = {},
                    placeholder = "Search…",
                    filterSlot = filterSlot(),
                )
            }
        }
        rule.onNodeWithText("Search…").assertIsDisplayed()
        // 根页规范:仅筛选按钮一个可交互图标。
        rule.onAllNodesWithContentDescription(filterCd).assertCountEquals(1)
        rule.onAllNodesWithContentDescription(backCd).assertCountEquals(0)
        rule.onAllNodesWithContentDescription(clearCd).assertCountEquals(0)
    }

    @Test
    fun collapsed_showsBackAndClearWhenProvided() {
        var query by mutableStateOf("abc")
        rule.setContent {
            MaterialTheme {
                CollapsedSearchInput(
                    query = query,
                    onBack = {},
                    onClear = { query = "" },
                    onTapToExpand = {},
                    placeholder = "Search…",
                    filterSlot = filterSlot(),
                )
            }
        }
        rule.onNodeWithText("abc").assertIsDisplayed()
        rule.onAllNodesWithContentDescription(backCd).assertCountEquals(1)
        rule.onAllNodesWithContentDescription(clearCd).assertCountEquals(1)
        rule.onAllNodesWithContentDescription(filterCd).assertCountEquals(1)
        // 点击清除 → 查询置空(清除按钮随之隐藏)。
        rule.onNodeWithContentDescription(clearCd).performClick()
        rule.runOnIdle { assertTrue(query.isEmpty()) }
        rule.onAllNodesWithContentDescription(clearCd).assertCountEquals(0)
    }

    @Test
    fun collapsed_tapExpands_longPressDoesNothing() {
        var expanded = 0
        rule.setContent {
            MaterialTheme {
                CollapsedSearchInput(
                    query = "",
                    onBack = {},
                    onClear = null,
                    onTapToExpand = { expanded++ },
                    placeholder = "Search…",
                    filterSlot = {},
                )
            }
        }
        // 长按(文本选择/光标意图)→ 无副作用(红线:长按不得进入 SearchView)
        rule.onNodeWithText("Search…").performTouchInput { longClick() }
        rule.runOnIdle { assertFalse(expanded > 0) }
        // 单击 → 展开
        rule.onNodeWithText("Search…").performClick()
        rule.runOnIdle { assertTrue(expanded == 1) }
    }

    @Test
    fun inputField_showsBackAndClearWhileTyping_hidesClearWhenEmpty() {
        var query by mutableStateOf("")
        rule.setContent {
            MaterialTheme {
                SearchInputField(
                    query = query,
                    onQueryChange = { query = it },
                    active = false,
                    onActiveChange = {},
                    onSearch = {},
                    onBack = {},
                    placeholder = "Search…",
                    filterSlot = filterSlot(),
                )
            }
        }
        // 查询空:返回在,清除不在。
        rule.onAllNodesWithContentDescription(backCd).assertCountEquals(1)
        rule.onAllNodesWithContentDescription(clearCd).assertCountEquals(0)
        rule.onNode(hasSetTextAction()).performTextInput("abc")
        rule.runOnIdle { assertTrue(query == "abc") }
        // 输入后:清除出现,筛选仍在。
        rule.onAllNodesWithContentDescription(clearCd).assertCountEquals(1)
        rule.onAllNodesWithContentDescription(filterCd).assertCountEquals(1)
    }

    @Test
    fun inputField_hidesBackWhenNotProvided() {
        var query by mutableStateOf("")
        rule.setContent {
            MaterialTheme {
                SearchInputField(
                    query = query,
                    onQueryChange = { query = it },
                    active = false,
                    onActiveChange = {},
                    onSearch = {},
                    // 根页展开态:仍无返回语义。
                    onBack = null,
                    placeholder = "Search…",
                    filterSlot = filterSlot(),
                )
            }
        }
        rule.onAllNodesWithContentDescription(backCd).assertCountEquals(0)
        rule.onAllNodesWithContentDescription(filterCd).assertCountEquals(1)
    }
}