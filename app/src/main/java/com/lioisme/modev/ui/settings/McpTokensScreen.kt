package com.lioisme.modev.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lioisme.modev.R
import com.lioisme.modev.core.model.McpTokenEntry
import com.lioisme.modev.ui.common.formatDay
import com.lioisme.modev.ui.common.rememberEffectiveLocale
import com.lioisme.modev.ui.components.ModevConfirmDialog
import com.lioisme.modev.ui.components.ModevInputSheet
import com.lioisme.modev.ui.components.ModevTopBar
import com.lioisme.modev.ui.components.SettingsToggleItem
import com.lioisme.modev.ui.components.TipIconButton
import com.lioisme.modev.ui.theme.Tokens
import kotlinx.coroutines.launch

/**
 * MCP 访问令牌管理页(V39 R3;V40 T6 重设计:高级/简约/优美,三层信息架构):
 * ①认证状态卡(开关 + 一句话状态:认证态/令牌数/空集全拒警示,单卡定调);
 * ②令牌卡列表(每枚一卡:名称 headline + mono 值/创建日 supporting +
 * 复制/显隐/撤销三动作依危险度排序,撤销 error 色并间距分离);
 * ③空态(内联 Icon+Text,按认证态区分文案)。
 *
 * 契约保留:令牌值宿主本地秘密(显隐/复制仅本页消费,恒不进 MCP 工具输出与
 * 诊断快照);`mcpTokenSetUpdater` 原地生效不重启不踢会话;撤销最后一枚不停用
 * 认证(空集 = 全拒,状态卡可见);legacy 迁移条目显示「导入」;新建走
 * [ModevInputSheet]、撤销走 [ModevConfirmDialog](V39 契约);本页无搜索 →
 * Appbar actions 仅「新建」一枚(排他规范合规)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun McpTokensScreen(
    viewModel: McpTokensViewModel,
    onBack: () -> Unit,
) {
    val tokenAuth by viewModel.tokenAuth.collectAsStateWithLifecycle()
    val tokens by viewModel.tokens.collectAsStateWithLifecycle()

    var addSheetOpen by rememberSaveable { mutableStateOf(false) }
    var revokeTarget by remember { mutableStateOf<McpTokenEntry?>(null) }
    var visibleIds by remember { mutableStateOf(setOf<String>()) }

    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val locale = rememberEffectiveLocale()

    fun copyToken(entry: McpTokenEntry) {
        clipboard.setText(AnnotatedString(entry.value))
        scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.token_copied)) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            ModevTopBar(
                title = stringResource(R.string.mcp_tokens_title),
                onBack = onBack,
                actions = {
                    TipIconButton(
                        tooltip = stringResource(R.string.tokens_add),
                        onClick = { addSheetOpen = true },
                        modifier = Modifier.testTag("tokens-add"),
                    ) {
                        Icon(
                            Icons.Default.Key,
                            contentDescription = stringResource(R.string.tokens_add),
                        )
                    }
                },
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
            Spacer(Modifier.height(8.dp))
            // ①认证状态卡:开关 + 状态行(认证态 · 令牌数 · 空集全拒警示)。
            Card(
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tokens-auth-card"),
            ) {
                SettingsToggleItem(
                    icon = Icons.Default.Key,
                    title = stringResource(R.string.mcp_token_auth),
                    subtitle = if (tokenAuth) {
                        TOKEN_AUTH_BEARER_SUBTITLE
                    } else {
                        stringResource(R.string.mcp_token_auth_off_hint)
                    },
                    checked = tokenAuth,
                    modifier = Modifier.testTag("token-auth-toggle"),
                    onCheckedChange = viewModel::setTokenAuth,
                )
                AuthStatusLine(
                    authEnabled = tokenAuth,
                    tokenCount = tokens.size,
                )
            }
            Spacer(Modifier.height(24.dp))
            // ②令牌卡列表 / ③空态。
            Text(
                text = pluralStringResource(R.plurals.mcp_tokens_count, tokens.size, tokens.size),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            )
            if (tokens.isEmpty()) {
                TokenEmptyState(authEnabled = tokenAuth)
            } else {
                tokens.forEachIndexed { index, entry ->
                    TokenCard(
                        entry = entry,
                        visible = entry.id in visibleIds,
                        localeTag = locale,
                        onToggleVisible = {
                            visibleIds =
                                if (entry.id in visibleIds) {
                                    visibleIds - entry.id
                                } else {
                                    visibleIds + entry.id
                                }
                        },
                        onCopy = { copyToken(entry) },
                        onRevoke = { revokeTarget = entry },
                    )
                    if (index != tokens.lastIndex) {
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (addSheetOpen) {
        ModevInputSheet(
            title = stringResource(R.string.tokens_add),
            label = stringResource(R.string.tokens_add_label_hint),
            initialValue = "",
            hint = null,
            validate = { true },
            onSubmit = { label ->
                viewModel.addToken(label)
                addSheetOpen = false
                scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.token_created)) }
            },
            onDismiss = { addSheetOpen = false },
            modifier = Modifier.testTag("token-add-sheet"),
        )
    }

    revokeTarget?.let { target ->
        ModevConfirmDialog(
            title = stringResource(R.string.danger_title),
            message = stringResource(R.string.token_revoke_confirm_msg),
            onConfirm = {
                viewModel.revokeToken(target.id)
                revokeTarget = null
            },
            onDismiss = { revokeTarget = null },
            confirmTestTag = "token-revoke-confirm",
        )
    }
}

/**
 * 认证状态行(V40 T6):语义色圆点(M3 Badge)+ 一句话状态——
 * 开 = 「已启用 · N 枚令牌」;开且零令牌 = 空集全拒警示(error 色,可从 UI
 * 直接理解撤销最后一枚不停用语义);关 = 「已停用 · 请求无需令牌」。
 * 资源形态(V41 T1):三个状态键均为自含文案(「On ·/已启用 ·」前缀由各语言
 * 资源字面承载)——`tokens_status_active` 仅含单个 `%d` 占位符,**传参必须
 * 单参 tokenCount**;传参与占位符错配会在组合期抛 IllegalFormatConversionException
 * (V41 V1.1 闪退根因,三态回归由 McpTokensScreenStateTest 锁定)。
 */
@Composable
internal fun AuthStatusLine(authEnabled: Boolean, tokenCount: Int) {
    val dotColor =
        if (authEnabled) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("tokens-auth-status"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        androidx.compose.material3.Badge(containerColor = dotColor)
        Text(
            text =
                if (authEnabled) {
                    if (tokenCount == 0) {
                        stringResource(R.string.tokens_status_deny_all)
                    } else {
                        stringResource(R.string.tokens_status_active, tokenCount)
                    }
                } else {
                    stringResource(R.string.tokens_status_off)
                },
            style = MaterialTheme.typography.bodySmall,
            color =
                if (authEnabled && tokenCount == 0) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
    }
}

/**
 * 令牌卡(V40 T6):名称 + 掩码/明文值 + 创建日 supporting;trailing 动作依
 * 危险度排序「复制 → 显隐 → 撤销」,撤销 error 色且与左邻间距分离;
 * 掩码形态 = 前 4 后 4 摘要(`MDEV••••••a1b2`,提升辨识度且不泄露全值);
 * 卡整体点击 = 显隐切换(affordance 保留)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TokenCard(
    entry: McpTokenEntry,
    visible: Boolean,
    localeTag: java.util.Locale,
    onToggleVisible: () -> Unit,
    onCopy: () -> Unit,
    onRevoke: () -> Unit,
) {
    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("token-row-${entry.id}")
            .clickable(onClick = onToggleVisible),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    entry.label.ifBlank {
                        stringResource(R.string.token_unnamed, entry.value.take(6))
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                val valueText =
                    if (visible) {
                        entry.value
                    } else {
                        maskValue(entry.value)
                    }
                val createdText =
                    if (entry.createdAt <= 0L) {
                        stringResource(R.string.token_created_legacy)
                    } else {
                        formatDay(entry.createdAt, localeTag)
                    }
                Text(
                    text = "$valueText · $createdText",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = if (visible) FontFamily.Monospace else FontFamily.Default,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            TokenAction(Icons.Default.ContentCopy, R.string.diagnostics_detail_copy, onCopy) {
                Modifier.testTag("token-copy-${entry.id}")
            }
            TokenAction(
                icon = if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                tooltipRes = if (visible) R.string.token_hide else R.string.token_show,
                onClick = onToggleVisible,
            ) {
                Modifier
            }
            Spacer(Modifier.width(12.dp))
            TokenAction(Icons.Default.Delete, R.string.token_revoke, onRevoke, errorTint = true) {
                Modifier.testTag("token-revoke-${entry.id}")
            }
            Spacer(Modifier.width(4.dp))
        }
    }
}

/** trailing 动作图标按钮(统一 20dp 图标;[errorTint] = 撤销语义 error 色)。 */
@Composable
private fun TokenAction(
    icon: ImageVector,
    tooltipRes: Int,
    onClick: () -> Unit,
    errorTint: Boolean = false,
    tag: () -> androidx.compose.ui.Modifier,
) {
    TipIconButton(
        tooltip = stringResource(tooltipRes),
        onClick = onClick,
        modifier = tag(),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint =
                if (errorTint) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            modifier = Modifier.size(20.dp),
        )
    }
}

/** 掩码:前 4 + 6 圆点 + 后 4(32 位 hex 摘要形态;短值全掩)。 */
internal fun maskValue(value: String): String =
    if (value.length > 8) {
        value.take(4) + "••••••" + value.takeLast(4)
    } else {
        "•".repeat(value.length)
    }

/** 空态(V40 T6 内联,MD3 原语):图标圆底 + 主文,认证态区分文案。 */
@Composable
private fun TokenEmptyState(authEnabled: Boolean) {
    val message =
        stringResource(if (authEnabled) R.string.tokens_empty_auth_on else R.string.tokens_empty)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp)
                .wrapContentSize(Alignment.Center)
                .semantics { stateDescription = message },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
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
                Icons.Default.Key,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
                    .copy(alpha = Tokens.emptyIconAlpha),
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
    }
}
