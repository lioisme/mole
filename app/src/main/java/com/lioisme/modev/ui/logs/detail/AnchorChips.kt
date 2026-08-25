package com.lioisme.modev.ui.logs.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lioisme.modev.R
import com.lioisme.modev.core.model.CryptoRecord
import com.lioisme.modev.core.model.SqlRecord

/**
 * 详情锚点导航(V24 G3 新增能力):按记录实际内容动态生成的 FilterChip 行,
 * 点击平滑滚动至目标区块——长记录(大数据/多密钥字段)的分区到达成本从
 * 反复滑动降为一次点击。标签复用 section_* 资源(零新增 i18n);
 * 标签清单在 remember 外由纯函数构建,滚动路径零派生计算。
 */

/** 锚点:key = LazyColumn item 索引簿记键(与屏幕装配侧严格同源);labelRes 复用区块标题资源。 */
data class DetailAnchor(val key: String, val labelRes: Int)

/** 算法记录锚点:数据流(双侧齐备)/ 密钥材料(任一字段)/ 参数 / 调用栈。 */
fun cryptoAnchors(record: CryptoRecord): List<DetailAnchor> {
    val anchors = mutableListOf<DetailAnchor>()
    if (record.input != null && record.output != null) {
        anchors += DetailAnchor("flow", R.string.section_zone_data_flow)
    }
    val hasKeyMaterial = record.key != null || record.keyAlias != null || record.iv != null ||
        record.salt != null || record.aad != null || record.publicKey != null
    if (hasKeyMaterial) anchors += DetailAnchor("keys", R.string.section_zone_key_material)
    if (!record.params.isNullOrEmpty()) anchors += DetailAnchor("params", R.string.section_params)
    if (record.stack.isNotEmpty()) anchors += DetailAnchor("stack", R.string.section_stack)
    return anchors
}

/** SQL 记录锚点:语句 / 路径 / 绑定参数 / 结果行 / 结果文本 / 参数 / 调用栈。 */
fun sqlAnchors(record: SqlRecord): List<DetailAnchor> {
    val anchors = mutableListOf<DetailAnchor>()
    if (record.sql != null) anchors += DetailAnchor("sql", R.string.section_sql)
    if (record.dbPath != null) anchors += DetailAnchor("dbpath", R.string.section_db_path)
    if (!record.bindArgs.isNullOrEmpty()) anchors += DetailAnchor("bindargs", R.string.section_bind_args)
    if (!record.rows.isNullOrEmpty()) anchors += DetailAnchor("rows", R.string.section_result_rows)
    if (record.resultText != null) anchors += DetailAnchor("result", R.string.section_result_text)
    if (!record.params.isNullOrEmpty()) anchors += DetailAnchor("params", R.string.section_params)
    if (record.stack.isNotEmpty()) anchors += DetailAnchor("stack", R.string.section_stack)
    return anchors
}

/**
 * 锚点芯片行(LazyRow + FilterChip,MD3 组件直排):
 * @param onAnchorClick 锚点 key → 屏幕装配层按簿记索引 animateScrollToItem。
 */
@Composable
internal fun DetailAnchorRow(
    anchors: List<DetailAnchor>,
    onAnchorClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (anchors.isEmpty()) return
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 2.dp),
        modifier = modifier.fillMaxWidth().testTag("detail-anchors"),
    ) {
        items(anchors.size, key = { anchors[it].key }) { index ->
            val anchor = anchors[index]
            FilterChip(
                selected = false,
                onClick = { onAnchorClick(anchor.key) },
                label = { Text(stringResource(anchor.labelRes), maxLines = 1) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            )
        }
    }
}
