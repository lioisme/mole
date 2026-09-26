package com.lioisme.modev.ui.settings

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
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
import com.lioisme.modev.ui.components.ModevValueDialog
import com.lioisme.modev.ui.components.SettingsToggleItem
import com.lioisme.modev.ui.components.TipIconButton
import com.lioisme.modev.ui.theme.Tokens
import kotlinx.coroutines.launch

/**
 * MCP 访问令牌管理页(V39 R3;V40 T6 三层信息架构;**V46 R3 动作菜单化**):
 * ①认证状态卡(开关 + 一句话状态:认证态/令牌数/空集全拒警示,单卡定调);
 * ②令牌卡列表(每枚一卡:名称 headline + 掩码值·创建日 supporting +
 *   trailing **唯一「更多」按钮** → 查看 / 重命名 / 删除三项菜单,删除项 error 色;
 *   **行体不承载点击** —— 明文只经查看对话框上屏,故不留「看着可点却无动作」的 affordance);
 * ③空态(内联 Icon+Text,按认证态区分文案)。
 *
 * 契约保留:令牌值宿主本地秘密(**行内恒掩码,明文仅 [ModevValueDialog] 一处上屏**;
 * 恒不进 MCP 工具输出与诊断快照);`mcpTokenSetUpdater` 原地生效不重启不踢会话
 * (重命名只动 label、值集合内容不变 ⇒ 会话零影响);删除最后一枚不停用
 * 认证(空集 = 全拒,状态卡可见);legacy 迁移条目显示「导入」;新建与重命名走
 * [ModevInputSheet]、查看走 [ModevValueDialog]、删除走 [ModevConfirmDialog];
 * 本页无搜索 → Appbar actions 仅「新建」一枚(排他规范合规)。
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
    // V46 R3:三动作各自的目标态(取代旧 visibleIds 显隐集合)。
    var viewTarget by remember { mutableStateOf<McpTokenEntry?>(null) }
    var renameTarget by remember { mutableStateOf<McpTokenEntry?>(null) }
    var deleteTarget by remember { mutableStateOf<McpTokenEntry?>(null) }

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
                        localeTag = locale,
                        onView = { viewTarget = entry },
                        onRename = { renameTarget = entry },
                        onDelete = { deleteTarget = entry },
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

    // V46 R3:查看 = 只读值对话框(唯一明文上屏处);复制后关窗,明文不常驻。
    viewTarget?.let { target ->
        ModevValueDialog(
            title = stringResource(R.string.token_value_title),
            value = target.value,
            onCopy = {
                copyToken(target)
                viewTarget = null
            },
            onDismiss = { viewTarget = null },
            modifier = Modifier.testTag("token-view-dialog"),
            valueTestTag = "token-view-value",
            copyTestTag = "token-view-copy",
            closeTestTag = "token-view-close",
        )
    }

    // 重命名 = 底部输入 sheet(强约束 #4);预填旧名,允许提交空名 = 清除名称
    // (与「新建令牌 label 可选」对称,空名回落 token_unnamed 显示形态)。
    renameTarget?.let { target ->
        ModevInputSheet(
            title = stringResource(R.string.token_rename),
            label = stringResource(R.string.tokens_add_label_hint),
            initialValue = target.label,
            hint = null,
            validate = { true },
            onSubmit = { label ->
                viewModel.setTokenLabel(target.id, label)
                renameTarget = null
                scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.token_renamed)) }
            },
            onDismiss = { renameTarget = null },
            modifier = Modifier.testTag("token-rename-sheet"),
            inputTestTag = "token-rename-input",
            confirmTestTag = "token-rename-confirm",
        )
    }

    deleteTarget?.let { target ->
        ModevConfirmDialog(
            title = stringResource(R.string.danger_title),
            message = stringResource(R.string.token_delete_confirm_msg),
            confirmTextRes = R.string.token_delete,
            onConfirm = {
                viewModel.revokeToken(target.id)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
            confirmTestTag = "token-delete-confirm",
        )
    }
}

/**
 * 认证状态行:语义色圆点(M3 Badge)+ 一句话状态——
 * 开 = 「已启用 · N 枚令牌」;开且零令牌 = 空集全拒警示(error 色,可从 UI
 * 直接理解撤销最后一枚不停用语义);关 = 「已停用 · 请求无需令牌」。
 * 资源形态:三个状态键均为自含文案(「On ·/已启用 ·」前缀由各语言
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
 * 令牌卡:行体不承载点击(D4),三动作恒由「更多」菜单驱动;
 * 值恒掩码(D3)—— 明文只经 [ModevValueDialog] 上屏。掩码串长度固定(前 4 +
 * 六点 + 后 4),与创建日同排因此不会随内容长短被截断。
 * 菜单序 = 查看 → 重命名 → 删除(危险度递进),删除项整项 error 语义。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TokenCard(
    entry: McpTokenEntry,
    localeTag: java.util.Locale,
    onView: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("token-row-${entry.id}"),
    ) {
        val createdText =
            if (entry.createdAt <= 0L) {
                stringResource(R.string.token_created_legacy)
            } else {
                formatDay(entry.createdAt, localeTag)
            }
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            headlineContent = {
                Text(
                    entry.label.ifBlank {
                        stringResource(R.string.token_unnamed, entry.value.take(6))
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            supportingContent = {
                Text(
                    text = "${maskValue(entry.value)} · $createdText",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            trailingContent = {
                // Box 锚定:菜单挂在「更多」按钮处(与 AppsScreen 筛选菜单同构)。
                Box {
                    TipIconButton(
                        tooltip = stringResource(R.string.token_more),
                        onClick = { menuExpanded = true },
                        modifier = Modifier.testTag("token-more-${entry.id}"),
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.token_more),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.token_view)) },
                            leadingIcon = {
                                Icon(Icons.Default.Visibility, null, modifier = Modifier.size(20.dp))
                            },
                            onClick = {
                                menuExpanded = false
                                onView()
                            },
                            modifier = Modifier.testTag("token-menu-view"),
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.token_rename)) },
                            leadingIcon = {
                                Icon(Icons.Default.Edit, null, modifier = Modifier.size(20.dp))
                            },
                            onClick = {
                                menuExpanded = false
                                onRename()
                            },
                            modifier = Modifier.testTag("token-menu-rename"),
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(R.string.token_delete),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Delete,
                                    null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp),
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            },
                            modifier = Modifier.testTag("token-menu-delete"),
                        )
                    }
                }
            },
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
