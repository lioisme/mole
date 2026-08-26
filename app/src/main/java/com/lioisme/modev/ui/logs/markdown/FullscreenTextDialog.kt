package com.lioisme.modev.ui.logs.markdown

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lioisme.modev.R
import com.lioisme.modev.core.util.ChunkedText
import com.lioisme.modev.ui.components.TipIconButton

/**
 * 全屏文本查看器(V31 修复):
 * - Dialog 全屏覆盖(usePlatformDefaultWidth = false),正文经 [ChunkedText]
 *   O(1) 分块惰性虚拟化——多 MB 全量文本仅组合可视块;
 * - 文本自动换行(softWrap=true),纵向 LazyColumn 滚动浏览完整内容;
 * - 每 chunk 行独立 [SelectionContainer](修复 V26 SelectionContainer 包裹 LazyColumn
 *   导致滚动失效的已知缺陷);
 * - TopAppBar/body 正确处理系统 insets(状态栏 + 导航栏),无遮蔽;
 * - 复制走屏幕层统一出口(4M 护栏 + Snackbar 反馈)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullscreenTextDialog(
    title: String,
    fullText: String,
    onDismiss: () -> Unit,
    onCopyFull: (String) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                        )
                    },
                    navigationIcon = {
                        TipIconButton(tooltip = stringResource(R.string.detail_exit_fullscreen), onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.detail_exit_fullscreen))
                        }
                    },
                    actions = {
                        TipIconButton(tooltip = stringResource(R.string.section_copy), onClick = { onCopyFull(fullText) }) {
                            Icon(
                                Icons.Filled.ContentCopy,
                                contentDescription = stringResource(R.string.section_copy),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                    windowInsets = WindowInsets.statusBars,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                FullscreenBody(fullText)
            }
        }
    }
}

@Composable
private fun FullscreenBody(fullText: String) {
    val chunks = remember(fullText) { ChunkedText(fullText) }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("fullscreen-list")
            .padding(WindowInsets.navigationBars.asPaddingValues()),
        horizontalAlignment = Alignment.Start,
    ) {
        items(count = chunks.size, key = { i -> "fs-$i" }) { i ->
            SelectionContainer {
                Text(
                    text = chunks[i],
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                )
            }
        }
    }
}
