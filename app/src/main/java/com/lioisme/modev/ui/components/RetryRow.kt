package com.lioisme.modev.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lioisme.modev.R

/**
 * 重试行(V19:MD3 TextButton 承载,替代手搓 clickable 文本;
 * 加载失败 / 分页失败统一入口)。
 */
@Composable
fun RetryRow(onRetry: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
        TextButton(onClick = onRetry) {
            Text(stringResource(R.string.retry))
        }
    }
}
