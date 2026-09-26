package com.lioisme.modev.ui.detail.markdown

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 目录(TOC)导航行:
 *
 * 定位决策(D2.1):目录条目是**导航控件**而非信息区块——Appbar 下的信息内容
 * 100% 由 markdown 承载;目录行以原生可点击 Text 承载(标签 = 与区块标题同源
 * 的本地化资源,样式 = 链接语义:primary Medium),点击直连屏幕锚点簿记滚动。
 *
 * 工程依据:渲染库的 markdown 链接在语义层暴露 0×0 bounds 的 LinkTestMarker
 * 节点(Robolectric 实测),坐标点击不可达且依赖库内 listener 捕获时序;
 * 导航属交互路径,可靠性优先,不经过该脆弱链路。
 */
@Composable
fun RecordTocContent(
    entries: List<TocEntry>,
    onAnchorClick: (String) -> Unit,
) {
    if (entries.isEmpty()) return
    Column(Modifier.fillMaxWidth()) {
        entries.forEach { entry ->
            Text(
                text = entry.label,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight,
                ),
                maxLines = 1,
                // U2〔P2〕TOC 行:加 Role.Button 语义(TalkBack 正确朗读为按钮),
                // 触控高度提到 ≥48dp(裸 clickable + 6dp padding 仅 ~32dp 可见区,
                // 低于 M3 48dp 最小触控);clickable 默认已扩 48dp 触摸目标,
                // 此处把可见内边距提到 14dp 使视觉高度也达 48dp。
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClick = { onAnchorClick(entry.key) })
                    .padding(vertical = 14.dp)
                    .testTag("toc-${entry.key}"),
            )
        }
    }
}
