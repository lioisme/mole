package com.lioisme.modev.ui.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lioisme.modev.core.model.CryptoKind
import com.lioisme.modev.core.model.HookSwitch
import com.lioisme.modev.ui.components.SwitchRow

/**
 * 目标应用详情页:八类算法开关 + 堆栈开关 + 连接状态 + 清空日志。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailScreen(
    viewModel: AppDetailViewModel,
    onBack: () -> Unit,
) {
    val switch by viewModel.switchState.collectAsStateWithLifecycle()
    val connected by viewModel.connected.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(viewModel.packageName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusChip(connected)
                Spacer(Modifier.width(12.dp))
                Text(
                    if (connected) "目标进程在线,配置修改即时生效" else "目标进程未连接,配置将在其启动时推送",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()

            SectionTitle("算法捕获")
            SwitchRow(
                label = "加解密 Cipher",
                description = "AES / DES / RSA / SM4,记录密钥、IV 与明密文",
                checked = switch.cipher,
                onCheckedChange = { on -> viewModel.setSwitch { it.copy(cipher = on) } },
            )
            SwitchRow(
                label = "摘要 MessageDigest",
                description = "MD5 / SHA-1 / SHA-256 / SM3",
                checked = switch.messageDigest,
                onCheckedChange = { on -> viewModel.setSwitch { it.copy(messageDigest = on) } },
            )
            SwitchRow(
                label = "HMAC Mac",
                description = "HMAC-MD5 / HMAC-SHA1 / HMAC-SHA256",
                checked = switch.mac,
                onCheckedChange = { on -> viewModel.setSwitch { it.copy(mac = on) } },
            )
            SwitchRow(
                label = "签名 Signature",
                description = "RSA / ECDSA 的 sign 与 verify",
                checked = switch.signature,
                onCheckedChange = { on -> viewModel.setSwitch { it.copy(signature = on) } },
            )
            SwitchRow(
                label = "密钥参数构造",
                description = "SecretKeySpec / IvParameterSpec / GCMParameterSpec / PBEKeySpec",
                checked = switch.keyParam,
                onCheckedChange = { on -> viewModel.setSwitch { it.copy(keyParam = on) } },
            )
            SwitchRow(
                label = "密钥生成",
                description = "KeyGenerator / KeyPairGenerator",
                checked = switch.keyGeneration,
                onCheckedChange = { on -> viewModel.setSwitch { it.copy(keyGeneration = on) } },
            )
            SwitchRow(
                label = "密钥还原",
                description = "KeyFactory / SecretKeyFactory",
                checked = switch.keyFactory,
                onCheckedChange = { on -> viewModel.setSwitch { it.copy(keyFactory = on) } },
            )
            SwitchRow(
                label = "Base64 编码",
                description = "android.util.Base64(默认关闭,噪声较大)",
                checked = switch.base64,
                onCheckedChange = { on -> viewModel.setSwitch { it.copy(base64 = on) } },
            )

            HorizontalDivider()
            SectionTitle("记录选项")
            SwitchRow(
                label = "记录调用堆栈",
                description = "定位调用来源,便于还原算法链路",
                checked = switch.logStack,
                onCheckedChange = { on -> viewModel.setSwitch { it.copy(logStack = on) } },
            )

            Spacer(Modifier.height(16.dp))
            Surface(
                onClick = viewModel::clearLogs,
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                    Text(
                        "清空该应用的日志",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StatusChip(connected: Boolean) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (connected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
    ) {
        Text(
            if (connected) "● 在线" else "○ 离线",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (connected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

private val HookSwitch.cipher: Boolean get() = enabled(CryptoKind.CIPHER)
private val HookSwitch.messageDigest: Boolean get() = enabled(CryptoKind.MESSAGE_DIGEST)
private val HookSwitch.mac: Boolean get() = enabled(CryptoKind.MAC)
private val HookSwitch.signature: Boolean get() = enabled(CryptoKind.SIGNATURE)
private val HookSwitch.keyParam: Boolean get() = enabled(CryptoKind.KEY_PARAM)
private val HookSwitch.keyGeneration: Boolean get() = enabled(CryptoKind.KEY_GENERATION)
private val HookSwitch.keyFactory: Boolean get() = enabled(CryptoKind.KEY_FACTORY)
private val HookSwitch.base64: Boolean get() = enabled(CryptoKind.BASE64)