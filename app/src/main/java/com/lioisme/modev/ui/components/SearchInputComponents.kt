package com.lioisme.modev.ui.components

import android.os.SystemClock
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import com.lioisme.modev.R
import com.lioisme.modev.ui.common.rememberLocaleAwareResources

/*
 * 共享搜索输入组件(V40 T5.1 MD3 化,取代 2026-08-16 手搓折叠胶囊):
 * 折叠态与展开态统一走 M3 `SearchBarDefaults.InputField` 标准实现——
 * 原 CollapsedSearchInput(D4 例外)退役:手搓 Row + detectTapGestures 重造
 * 输入域形态;标准 InputField 自带 placeholder/leading/trailing 槽、
 * 语义与焦点展开行为(长按选词与展开的冲突经 SearchController.onInputActiveChange
 * 的「仅收起态接受展开意图」守卫缓解,行为探针 SearchInputComponentsTest 验收)。
 * AppBar=仅 SearchBar 规范下,返回/清除/动作/筛选全部置于输入域内部槽位。
 * [onBack]/[onClear] 可空:空=无该语义(根页无返回语义时隐藏返回)。
 * 搜索状态机红线(rememberSearchBarState 无参 / searchActive 独立意图 /
 * LaunchedEffect 唯一动画驱动 / 收起路径三件套)由调用方宿主遵守,本组件不触碰
 * SearchBarState 动画(避免焦点回调与动画互相激发死循环)。
 *
 * V42 T1 槽长按门禁:槽按钮位于 BasicTextField decoration box 内,长按不被
 * 按钮 clickable 消费,焦点链路直触展开——现按维护者决策抑制「槽按钮按压
 * 引发的展开意图」(文本区长按选词→聚焦展开保持 MD3 标准不变;取代 V41 D-3
 * 的「整体接受」口径)。实现 = 槽容器按压追踪(pointerInput 仅观测不消费,
 * 按钮 click/tooltip 行为零改变)+ [onExpandedChange] 门禁:按压进行中或
 * 释放后 [SLOT_EXPAND_GRACE_MS] 宽限内拒绝 true(吸收 M3 延迟焦点回调
 * ~100ms 形态),收起意图恒放行。
 */

/** 槽按压释放后仍抑制展开意图的宽限期(吸收 M3 延迟焦点回调;实测定档 400ms)。 */
internal const val SLOT_EXPAND_GRACE_MS = 400L

/**
 * 搜索输入域(折叠态 SearchBar 与全屏 ExpandedFullScreenSearchBar 共用同一实现)。
 * 展开/收起意图经 [active]/[onActiveChange] 与外部独立 state 通信(官方语义,
 * V42 起槽按压窗口内的展开意图被静默抑制);返回按钮([onBack] 可空)置于
 * leadingIcon、清除 + 动作槽([actionSlot],V39 R5 Appbar 排他规范下页面动作
 * 收进搜索条)+ 筛选槽([filterSlot],含 DropdownMenu)置于 trailingIcon;
 * [placeholder] 由调用方传入。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchInputField(
    query: String,
    onQueryChange: (String) -> Unit,
    active: Boolean,
    onActiveChange: (Boolean) -> Unit,
    onSearch: () -> Unit,
    onBack: (() -> Unit)?,
    placeholder: String,
    filterSlot: @Composable () -> Unit,
    actionSlot: (@Composable () -> Unit)? = null,
    /** 折叠态前导槽(根页设置按钮);优先于 [onBack] 渲染,两者互斥语义。 */
    leadingSlot: (@Composable () -> Unit)? = null,
) {
    // 展开态可能在独立 Dialog 窗口组合:窗口组合不接收主树 LocalContext 更新,
    // 文本一律经语言状态驱动解析(见 rememberLocaleAwareResources)。
    val res = rememberLocaleAwareResources()

    // 槽按压窗口(leading/trailing 两容器共享):true=有指针按下未释放;
    // releasedAt 供释放后宽限期判定。pointerInput 协程与本回调同主线程,免同步。
    var slotPressed by remember { mutableStateOf(false) }
    var slotReleasedAt by remember { mutableStateOf(0L) }

    val gatedOnActiveChange: (Boolean) -> Unit = { value ->
        val inSlotPressWindow =
            slotPressed || SystemClock.uptimeMillis() - slotReleasedAt < SLOT_EXPAND_GRACE_MS
        if (value && inSlotPressWindow) {
            // 抑制:槽按压引发的焦点展开意图(行为探针 SearchInputComponentsTest)。
        } else {
            onActiveChange(value)
        }
    }

    SearchBarDefaults.InputField(
        query = query,
        onQueryChange = onQueryChange,
        onSearch = { onSearch() },
        expanded = active,
        onExpandedChange = gatedOnActiveChange,
        placeholder = { Text(placeholder) },
        leadingIcon = {
            Box(Modifier.slotPressWindow({ slotPressed = it }, { slotReleasedAt = it })) {
                when {
                    leadingSlot != null -> leadingSlot()
                    onBack != null -> {
                        TipIconButton(tooltip = res.getString(R.string.cd_back), onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = res.getString(R.string.cd_back),
                            )
                        }
                    }
                }
            }
        },
        trailingIcon = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.slotPressWindow({ slotPressed = it }, { slotReleasedAt = it }),
            ) {
                if (query.isNotEmpty()) {
                    TipIconButton(
                        tooltip = res.getString(R.string.clear_search),
                        onClick = { onQueryChange("") },
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = res.getString(R.string.clear_search),
                        )
                    }
                }
                actionSlot?.invoke()
                filterSlot()
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * 槽按压窗口观测:记录容器内按下→全部指针释放的窗口。
 * 仅观测不消费(requireUnconsumed=false 覆盖按钮已消费事件),按钮 click/
 * 长按 tooltip 行为零改变;try/finally 保证取消路径不残留按压态。
 */
private fun Modifier.slotPressWindow(
    onPressChanged: (Boolean) -> Unit,
    onReleased: (Long) -> Unit,
): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            onPressChanged(true)
            try {
                // awaitAllPointersUp 为 foundation internal(本库不可见)——
                // 等效公开 API 循环:逐事件等待,全部指针抬起/取消即出窗。
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.changes.all { !it.pressed }) break
                }
            } finally {
                onPressChanged(false)
                onReleased(SystemClock.uptimeMillis())
            }
        }
    }
