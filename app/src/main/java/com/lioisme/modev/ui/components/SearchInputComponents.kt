package com.lioisme.modev.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lioisme.modev.R

/*
 * 共享搜索输入组件(2026-08-16 LIDE 风格对齐):
 * AppBar=仅 SearchBar 规范下,返回/清除按钮置于 SearchBar 内部左右两侧
 * (LIDE SearchInputComponents 同款),与 AppBar 其他控件互斥不冲突。
 * [onBack]/[onClear] 可空:空=无该语义(根页无返回语义时隐藏返回),
 * 空值过滤在组件内完成,调用方只表达意图。
 * 搜索状态机红线(rememberSearchBarState 无参 / searchActive 独立意图 /
 * LaunchedEffect 唯一动画驱动 / 收起路径三件套)由调用方宿主遵守,本组件不触碰
 * SearchBarState 动画(避免焦点回调与动画互相激发死循环)。
 */

/**
 * 搜索输入条(折叠态 SearchBar 与全屏 ExpandedFullScreenSearchBar 共用同一实现)。
 * 展开/收起意图经 [active]/[onActiveChange] 与外部独立 state 通信(官方语义);
 * 返回按钮([onBack] 可空)置于 leadingIcon、清除 + 筛选槽([filterSlot],含
 * DropdownMenu)置于 trailingIcon;[placeholder] 由调用方传入(应用列表 /
 * 算法列表各自语义文案)。
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
) {
    SearchBarDefaults.InputField(
        query = query,
        onQueryChange = onQueryChange,
        onSearch = { onSearch() },
        expanded = active,
        onExpandedChange = onActiveChange,
        placeholder = { Text(placeholder) },
        leadingIcon = {
            if (onBack != null) {
                TipIconButton(tooltip = stringResource(R.string.cd_back), onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back),
                    )
                }
            }
        },
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (query.isNotEmpty()) {
                    TipIconButton(
                        tooltip = stringResource(R.string.clear_search),
                        onClick = { onQueryChange("") },
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.clear_search),
                        )
                    }
                }
                filterSlot()
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * 折叠态搜索条(AppBar 内):仅单击进入 SearchView。
 *
 * 不挂载 SearchBarDefaults.InputField——其 BasicTextField 内部
 * `onFocusChanged { if (isFocused) onExpandedChange(true) }` 由"获得焦点"驱动展开,
 * 而长按(文本选择/光标)同样会请求焦点,导致长按也进入 SearchView。
 * 本组件用 [detectTapGestures] 区分单击(展开)与长按(无副作用);
 * 首尾图标按钮(返回 [onBack] 可空 / 清除 / 筛选槽)各自保留单击语义与长按 tooltip。
 * 展开后的输入、焦点与键盘由 ExpandedFullScreenSearchBar 的 focusRequester 自动接管。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CollapsedSearchInput(
    query: String,
    onBack: (() -> Unit)?,
    onClear: (() -> Unit)?,
    onTapToExpand: () -> Unit,
    placeholder: String,
    filterSlot: @Composable () -> Unit,
) {
    val container = MaterialTheme.colorScheme.surfaceContainerHigh
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(container)
                .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            TipIconButton(tooltip = stringResource(R.string.cd_back), onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                )
            }
        }
        // 折叠条主体:纯 pointer 手势 + 显式语义按钮(pointerInput 语义空白——
        // 折叠态 = 一次单击展开,不随「无图标节点」被 TalkBack 跳过)。
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { onTapToExpand() },
                            onLongPress = {},
                        )
                    }
                    .semantics {
                        role = Role.Button
                        onClick {
                            onTapToExpand()
                            true
                        }
                    },
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = if (query.isEmpty()) placeholder else query,
                style = MaterialTheme.typography.bodyLarge,
                color =
                    if (query.isEmpty()) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
        if (query.isNotEmpty() && onClear != null) {
            TipIconButton(tooltip = stringResource(R.string.clear_search), onClick = onClear) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.clear_search),
                )
            }
        }
        filterSlot()
    }
}