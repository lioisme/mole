package com.lioisme.modev.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.lioisme.modev.R

/**
 * 共享次级页顶栏(2026-08-16 V3,四界面重构;2026-08-17 V7 固定高度守卫):
 * 返回键(TipIconButton)+ 标题 + 标准配色。
 * 设计规则:禁止在标题前显示图标(无 icon 参数);返回键常显——四界面信息架构下
 * 设置页/算法详情页均为次级页,无根页化需求(根页=应用列表,不适用本组件)。
 * 标题恒单行省略(maxLines=1 + Ellipsis):长标题不换行撑高 AppBar,固定高度
 * 契约由组件层强制,调用方无需逐页约束。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModevTopBar(
    title: String,
    onBack: () -> Unit,
    actions: @Composable () -> Unit = {},
) {
    TopAppBar(
        title = {
            Text(
                title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        navigationIcon = {
            TipIconButton(tooltip = stringResource(R.string.cd_back), onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
            }
        },
        actions = { actions() },
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
            ),
    )
}
