package com.lioisme.modev.ui.records

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import com.lioisme.modev.R
import com.lioisme.modev.domain.record.RowModel
import com.lioisme.modev.ui.components.RecordRow
import com.lioisme.modev.ui.components.RetryRow
import com.lioisme.modev.ui.common.groupLabelOrNull
import com.lioisme.modev.ui.common.rememberEffectiveLocale
import com.lioisme.modev.ui.common.secondKey
import com.lioisme.modev.ui.common.timelineLabel
import com.lioisme.modev.ui.theme.Tokens

/**
 * 记录时间线列表(V23 T4.2 自单应用记录页抽取,全局/单应用三页共享):
 * - 手动索引循环(V17 R3 秒级时间线分组):组起始插组头,首项恒为组起始;
 * - 三态:空(detail_empty + 引导副文)/加载/刷新失败(RetryRow)+ append 追加态;
 * - 滚动状态由 VM 级 [LazyListState] 承载,详情往返原位恢复;
 * - 组头 locale/时制与行尾时间同源(context 包 locale);
 * - V27 M1-T4:[onRecordLongPress] 行为接线(宿主承担复制摘要 + Snackbar);
 * - V40 T4:[packageLabels] 非 null = 行内应用名上下文(按类型全局列表)。
 */
@Composable
fun RecordTimelineList(
    records: LazyPagingItems<RowModel>,
    listState: LazyListState,
    onOpenRecord: (Long) -> Unit,
    onRecordLongPress: ((RowModel) -> Unit)? = null,
    packageLabels: Map<String, String>? = null,
) {
    val context = LocalContext.current
    val locale = rememberEffectiveLocale()
    val is24h = remember(context) { DateFormat.is24HourFormat(context) }

    // V43 T5:组头判定配对记忆化——groupLabelOrNull/timelineLabel 为纯函数
    // (AGENTS #10 邻行比较契约不变),但分页 append 致全列表内容体重组合,旧实现
    // 每索引 2×secondKey + 2×dayKey + 格式化重算;同 (prev, ts) 输入必得同输出,
    // 缓存按 locale/时制作用域,命中即 O(1)。缓存写入幂等,重启安全。
    val groupCache = remember(locale, is24h) { HashMap<Pair<Long?, Long>, String?>() }
    val firstLabelCache = remember(locale, is24h) { HashMap<Long, String>() }
    fun groupLabelCached(
        ts: Long,
        prev: Long?,
    ): String? {
        val key = prev to ts
        if (groupCache.containsKey(key)) return groupCache[key]
        return groupLabelOrNull(ts, prev, locale, is24h).also { groupCache[key] = it }
    }
    fun firstLabelCached(ts: Long): String =
        firstLabelCache.getOrPut(ts) { timelineLabel(ts, locale, is24h) }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        when {
            records.itemCount == 0 && records.loadState.refresh is LoadState.NotLoading ->
                item(key = "empty", contentType = "empty") {
                    // V40 T5.4:空态内联(EmptyState 组件退役)——MD3 原语组合。
                    val emptyMessage = stringResource(R.string.detail_empty)
                    val emptyHint = stringResource(R.string.detail_empty_hint)
                    Column(
                        modifier =
                            Modifier
                                .fillParentMaxSize()
                                .wrapContentSize(Alignment.Center)
                                .semantics { stateDescription = emptyMessage },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                        .copy(alpha = Tokens.emptyIconContainerAlpha),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Outlined.Terminal,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    .copy(alpha = Tokens.emptyIconAlpha),
                                modifier = Modifier.size(28.dp),
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            emptyMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            emptyHint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                .copy(alpha = Tokens.emptyIconHintAlpha),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp),
                        )
                    }
                }

            records.itemCount == 0 && records.loadState.refresh is LoadState.Loading ->
                item(key = "loading", contentType = "loading") {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.loading),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

            records.itemCount == 0 && records.loadState.refresh is LoadState.Error ->
                item(key = "refresh-error", contentType = "error") {
                    RetryRow(onRetry = { records.retry() })
                }

            else -> {
                for (index in 0 until records.itemCount) {
                    val record = records[index]
                    if (record == null) {
                        item(key = "ph-$index", contentType = "placeholder") { Box(Modifier.fillMaxWidth().height(0.dp)) }
                    } else {
                        val previous = if (index > 0) records[index - 1] else null
                        val label =
                            if (index == 0) {
                                firstLabelCached(record.timestamp)
                            } else {
                                groupLabelCached(record.timestamp, previous?.timestamp)
                            }
                        if (label != null) {
                            item(key = "h-" + secondKey(record.timestamp), contentType = "header") {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    // V40 T5.2:组头内联(TimelineHeader 组件退役)——
                                    // Surface+Text 即 MD3 原语;testTag/形态契约不变。
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainer,
                                        modifier = Modifier.testTag("timeline-header"),
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                        )
                                    }
                                }
                            }
                        }
                        item(key = record.id, contentType = "record") {
                            RecordRow(
                                record = record,
                                onClick = { onOpenRecord(record.id) },
                                onLongClick = { onRecordLongPress?.invoke(record) },
                                modifier =
                                    Modifier
                                        // V46:record-row 锚点由 RecordRow 自持(必须落在
                                        // ListItem 合并语义节点上),宿主只传间距。
                                        // V46 D8:行间 4dp → 6dp。相邻卡片间隙 = 2×6 = 12dp
                                        // = 行容器半径(shapes.medium);间隙小于半径一半时
                                        // 圆角会读成波浪边。
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                contextLabel =
                                    packageLabels?.get(record.packageName)
                                        ?.takeIf { it.isNotBlank() },
                            )
                        }
                    }
                }
            }
        }
        when (val append = records.loadState.append) {
            is LoadState.Loading -> item(contentType = "append-loading") {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.loading),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            is LoadState.Error -> item(contentType = "append-error") {
                RetryRow(onRetry = { records.retry() })
            }
            else -> {}
        }
    }
}
