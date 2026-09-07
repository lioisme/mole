package com.lioisme.modev.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lioisme.modev.R
import com.lioisme.modev.ui.components.ModevTopBar
import com.lioisme.modev.ui.theme.Tokens
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.Library
import com.mikepenz.aboutlibraries.util.withContext

/**
 * 开源许可列表页(V22 R2/T3,aboutlibraries 编译期元数据 + 自建 M3 页面):
 * - 数据 = [Libs.Builder].withContext(context)(约 `res/raw/aboutlibraries.json`,
 *   插件随变体自动生成,含全部直接 + 传递依赖与许可全文,零运行时开销);
 * - 每行 = M3 [ListItem](无容器列表 containerColor = surface):库名 headline +
 *   版本 · 许可名 supporting + chevron;点击 → 详情页(按 index 定位);
 * - 同步小数据量解析(≤ 数百 KB),remember 缓存单次。
 */
@Composable
fun LicensesScreen(
    onBack: () -> Unit,
    onOpenDetail: (Int) -> Unit,
) {
    val context = LocalContext.current
    // aboutlibraries 官方标准入口:打包产物 raw 资源 → 数据模型。
    val libraries = remember(context) {
        runCatching { Libs.Builder().withContext(context).build().libraries }
            .getOrDefault(emptyList())
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            ModevTopBar(
                title = stringResource(R.string.licenses_title),
                onBack = onBack,
            )
        },
    ) { innerPadding ->
        if (libraries.isEmpty()) {
            // V40 T5.4:空态内联(EmptyState 组件退役)——MD3 原语组合。
            val emptyMessage = stringResource(R.string.unknown_error)
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
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
                        Icons.Default.Info,
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
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 16.dp),
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            ) {
                items(libraries, key = { it.uniqueId }) { library ->
                    LicenseRowItem(
                        library = library,
                        onClick = { onOpenDetail(libraries.indexOf(library)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LicenseRowItem(
    library: Library,
    onClick: () -> Unit,
) {
    val licenses = library.licenses.map { it.spdxId ?: it.name ?: "" }.joinToString(", ")
    val subtitle =
        library.artifactVersion
            ?.takeIf { it.isNotBlank() }
            ?.let { stringResource(R.string.licenses_row_format, it, licenses) }
            ?: licenses
    ListItem(
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        headlineContent = {
            Text(
                text = library.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            Icon(
                Icons.Default.ChevronRight,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
        },
        modifier =
            Modifier
                .fillMaxWidth()
                // 测试锚点:许可行 UI 测试用 testTag 定位。
                .testTag("license-row")
                .clickable(onClick = onClick),
    )
}