package com.lioisme.modev.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SearchBarState
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/*
 * 搜索状态机共享封装(2026-08-16 V3,四界面重构,收敛 AppsScreen/记录列表共用)。
 * 状态机红线(2026-08-15 Phase 4 确立)全守:
 *  1. rememberSearchBarState 必须无参调用,禁止显式传动画 spec;
 *  2. searchActive 独立意图(rememberSaveable),与动画进度解耦;
 *  3. LaunchedEffect(searchActive) 是唯一动画驱动(展开/收起互斥);
 *  4. 收起路径「收起动画 → 完成后导航」由同一协程执行(pendingNavigation);
 *  5. rememberSaveable 可能恢复半程动画进度,snapTo 强制归零兜底。
 * 调用方仅需:rememberSearchController(onNavigate = { 导航 }) → 读写 [query]/[searchActive]。
 */

/**
 * 搜索状态机控制器(泛型 [T] = 待导航目标,如应用列表=包名、算法列表=记录 id)。
 * [active] 为折叠条与全屏 SearchView 共用的单一事实源(单源驱动,防双态失步)。
 */
@OptIn(ExperimentalMaterial3Api::class)
class SearchController<T>(
    /** 折叠条 ↔ 全屏 SearchView 的共享动画状态(官方组件状态)。 */
    val searchBarState: SearchBarState,
    private val active: MutableState<Boolean>,
    private val query: MutableState<String>,
    private val pending: MutableState<T?>,
) {
    /** 搜索态意图(true=展开全屏 SearchView)。 */
    var searchActive: Boolean by active

    /** 查询词(UI 层持有,变更下发 ViewModel 过滤)。 */
    var queryText: String by query

    /**
     * 点击列表行:挂起导航目标并收起搜索态。
     * 实际导航由动画唯一驱动协程在收起动画完成后执行(见 [rememberSearchController])。
     */
    fun collapseWithNavigation(target: T) {
        pending.value = target
        active.value = false
    }

    /** 仅收起(返回/搜索完成路径),不触发导航。 */
    fun collapse() {
        pending.value = null
        active.value = false
    }

    /**
     * 输入框的激活意图回调(SearchInputField.onExpandedChange 直通)。
     * 焦点驱动的 true 仅在「当前已收起」时接受:展开/收起动画期间(currentValue 仍为
     * Expanded)忽略,防止 collapse 后 M3 InputField 的延迟焦点回调(+~100ms)把
     * 收起意图反向顶回——实机回归:进程重建恢复全屏搜索后按返回,搜索又自动重开。
     */
    fun onInputActiveChange(value: Boolean) {
        if (value && searchBarState.currentValue != SearchBarValue.Collapsed) return
        active.value = value
    }
}

/**
 * 构造搜索状态机控制器,并常驻驱动唯一动画协程。
 * [onNavigate] 在收起动画完成后携带 [SearchController.collapseWithNavigation] 的目标执行;
 * 调用方组合期传入(如 `{ pkg -> navigate(pkg) }`),本函数只依赖其在收起时被调用。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> rememberSearchController(
    onNavigate: (T) -> Unit,
): SearchController<T> {
    val searchBarState = rememberSearchBarState()
    val searchActive = rememberSaveable { mutableStateOf(false) }
    val query = rememberSaveable { mutableStateOf("") }
    val pending = remember { mutableStateOf<T?>(null) }

    LaunchedEffect(searchActive.value) {
        if (searchActive.value) {
            searchBarState.animateToExpanded()
        } else {
            searchBarState.animateToCollapsed()
            pending.value?.let(onNavigate)
            pending.value = null
        }
    }
    // 防御:rememberSaveable 可能恢复半程动画进度,强制归零兜底。
    LaunchedEffect(Unit) {
        if (!searchActive.value) searchBarState.snapTo(0f)
    }

    return remember(searchBarState) {
        SearchController(searchBarState, searchActive, query, pending)
    }
}
