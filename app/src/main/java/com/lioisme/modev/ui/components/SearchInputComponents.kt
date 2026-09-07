package com.lioisme.modev.ui.components

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
 */

/**
 * 搜索输入域(折叠态 SearchBar 与全屏 ExpandedFullScreenSearchBar 共用同一实现)。
 * 展开/收起意图经 [active]/[onActiveChange] 与外部独立 state 通信(官方语义);
 * 返回按钮([onBack] 可空)置于 leadingIcon、清除 + 动作槽([actionSlot],V39 R5
 * Appbar 排他规范下页面动作收进搜索条)+ 筛选槽([filterSlot],含
 * DropdownMenu)置于 trailingIcon;[placeholder] 由调用方传入。
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
    SearchBarDefaults.InputField(
        query = query,
        onQueryChange = onQueryChange,
        onSearch = { onSearch() },
        expanded = active,
        onExpandedChange = onActiveChange,
        placeholder = { Text(placeholder) },
        leadingIcon = {
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
        },
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
