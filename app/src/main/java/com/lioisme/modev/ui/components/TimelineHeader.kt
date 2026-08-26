package com.lioisme.modev.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * 时间线组头(V17 R3 秒级分组):居中胶囊(rounded 8dp + surfaceVariant 底色),
 * labelSmall onSurfaceVariant;文本由 [com.lioisme.modev.ui.common.timelineLabel]
 * 决策(跨日带日期前缀,同日仅秒级时间)。
 */
@Composable
fun TimelineHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.testTag("timeline-header"),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}