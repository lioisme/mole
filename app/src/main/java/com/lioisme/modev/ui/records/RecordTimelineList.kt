package com.lioisme.modev.ui.records

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import com.lioisme.modev.R
import com.lioisme.modev.data.mapping.RowModel
import com.lioisme.modev.ui.components.EmptyState
import com.lioisme.modev.ui.components.RecordRow
import com.lioisme.modev.ui.components.RetryRow
import com.lioisme.modev.ui.components.TimelineHeader
import com.lioisme.modev.ui.common.groupLabelOrNull
import com.lioisme.modev.ui.common.rememberEffectiveLocale
import com.lioisme.modev.ui.common.secondKey
import com.lioisme.modev.ui.common.timelineLabel

/**
 * 记录时间线列表(V23 T4.2 自单应用记录页抽取,全局/单应用三页共享):
 * - 手动索引循环(V17 R3 秒级时间线分组):组起始插组头,首项恒为组起始;
 * - 三态:空(detail_empty)/加载/刷新失败(RetryRow)+ append 追加态;
 * - 滚动状态由 VM 级 [LazyListState] 承载,详情往返原位恢复(V20 R3);
 * - 组头 locale/时制与 RecordRow 尾部时间同源(context 包 locale)。
 */
@Composable
fun RecordTimelineList(
    records: LazyPagingItems<RowModel>,
    listState: LazyListState,
    onOpenRecord: (Long) -> Unit,
) {
    val context = LocalContext.current
    val locale = rememberEffectiveLocale()
    val is24h = remember(context) { DateFormat.is24HourFormat(context) }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        when {
            records.itemCount == 0 && records.loadState.refresh is LoadState.NotLoading ->
                item(key = "empty") {
                    Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                        EmptyState(
                            icon = Icons.Outlined.Terminal,
                            message = stringResource(R.string.detail_empty),
                        )
                    }
                }

            records.itemCount == 0 && records.loadState.refresh is LoadState.Loading ->
                item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.loading),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

            records.itemCount == 0 && records.loadState.refresh is LoadState.Error ->
                item(key = "refresh-error") {
                    RetryRow(onRetry = { records.retry() })
                }

            else -> {
                for (index in 0 until records.itemCount) {
                    val record = records[index]
                    if (record == null) {
                        item(key = "ph-$index") { Box(Modifier.fillMaxWidth().height(0.dp)) }
                    } else {
                        val previous = if (index > 0) records[index - 1] else null
                        val label =
                            if (index == 0) {
                                timelineLabel(record.timestamp, locale, is24h)
                            } else {
                                groupLabelOrNull(
                                    record.timestamp,
                                    previous?.timestamp,
                                    locale,
                                    is24h,
                                )
                            }
                        if (label != null) {
                            item(key = "h-" + secondKey(record.timestamp)) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    TimelineHeader(text = label)
                                }
                            }
                        }
                        item(key = record.id) {
                            RecordRow(
                                record = record,
                                onClick = { onOpenRecord(record.id) },
                                modifier =
                                    Modifier
                                        .testTag("record-row")
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }
        }
        when (val append = records.loadState.append) {
            is LoadState.Loading -> item {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.loading),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            is LoadState.Error -> item {
                RetryRow(onRetry = { records.retry() })
            }
            else -> {}
        }
    }
}
