package com.lioisme.modev.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lioisme.modev.BuildConfig
import com.lioisme.modev.R
import com.lioisme.modev.ui.components.AppIcon
import com.lioisme.modev.ui.components.ModevTopBar
import com.lioisme.modev.ui.components.SettingsInfoItem
import com.lioisme.modev.ui.components.SettingsItem
import com.lioisme.modev.ui.components.SettingsSection

/**
 * 关于页(V22 R2 自设置页 AlertDialog 升级为独立页,对齐 AOSP「设置 → 关于」范式):
 * - 标识区:应用图标(64dp)/ 名称(headlineSmall)/ 版本(bodyMedium)/ 描述
 *   (bodyMedium,复用 app_description 双语键,居中);
 * - 信息卡:开源许可入口(→ LicensesScreen)+ 版本只读行(SettingsInfoItem)。
 * 返回语义 = 系统返回键 / 顶栏返回,由宿主接线 popGuarded。
 */
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onOpenLicenses: () -> Unit,
) {
    val context = LocalContext.current
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            ModevTopBar(
                title = stringResource(R.string.about),
                onBack = onBack,
            )
        },
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(32.dp))
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AppIcon(packageName = context.packageName, size = 64.dp)
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = versionText(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.app_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(32.dp))
            SettingsSection(stringResource(R.string.settings_about_section)) {
                SettingsItem(
                    icon = Icons.Default.Code,
                    title = stringResource(R.string.licenses_title),
                    subtitle = stringResource(R.string.licenses_subtitle, stringResource(R.string.app_name)),
                    onClick = onOpenLicenses,
                )
                SettingsInfoItem(
                    icon = Icons.Default.Info,
                    title = stringResource(R.string.about_version),
                    value = versionText(),
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** 「App vX.Y.Z」版本行(单一事实源,标识区与信息卡共用)。 */
@Composable
private fun versionText(): String =
    stringResource(R.string.version, stringResource(R.string.app_name), BuildConfig.VERSION_NAME)