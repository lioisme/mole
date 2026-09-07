package com.lioisme.modev.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.selection.SelectionContainer
import com.lioisme.modev.R
import com.lioisme.modev.ui.components.ModevTopBar
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.Library
import com.mikepenz.aboutlibraries.util.withContext

/**
 * 开源许可详情页(V22 R2/T3):
 * - 按 index 定位库(与列表页同源数据,index = 列表排序序位,路由参数);
 * - 库名 headlineMedium + 版本 + 描述(有则展示);
 * - 每个许可:SPDX 标识(labelLarge primary)+ 完整许可全文
 *   (SelectionContainer 可选中复制,bodySmall);
 * - index 越界/库不存在 → onInvalid 弹栈兜底(与 recordId 同款语义)。
 */
@Composable
fun LicenseDetailScreen(
    index: Int,
    onBack: () -> Unit,
    onInvalid: () -> Unit,
) {
    val context = LocalContext.current
    val libraries = remember(context) {
        runCatching { Libs.Builder().withContext(context).build().libraries }
            .getOrDefault(emptyList())
    }
    val library = libraries.getOrNull(index)
    if (library == null) {
        LaunchedEffect(Unit) { onInvalid() }
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            ModevTopBar(
                title = library.name,
                onBack = onBack,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(bottom = 32.dp),
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            item {
                LicenseHeader(library)
            }
            val licenseList = library.licenses.toList()
            if (licenseList.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.unknown_error),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    )
                }
            } else {
                items(licenseList) { license ->
                    LicenseSection(
                        spdx = license.spdxId ?: license.name ?: "",
                        content = license.licenseContent,
                    )
                }
            }
        }
    }
}

/** 库信息头:名称 / 版本 / 描述(有则展示)。 */
@Composable
private fun LicenseHeader(library: Library) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp)) {
        Text(
            text = library.name,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        library.artifactVersion?.takeIf { it.isNotBlank() }?.let { version ->
            Spacer(Modifier.height(4.dp))
            Text(
                text = version,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        library.description?.takeIf { it.isNotBlank() }?.let { description ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 单许可区块:SPDX 标识 + 全文(可选中复制)。 */
@Composable
private fun LicenseSection(
    spdx: String,
    content: String?,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(12.dp))
        Text(
            text = spdx,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        if (!content.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            SelectionContainer {
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}