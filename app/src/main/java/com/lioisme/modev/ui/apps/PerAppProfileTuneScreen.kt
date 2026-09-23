package com.lioisme.modev.ui.apps

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lioisme.modev.R
import com.lioisme.modev.core.model.ProfileField
import com.lioisme.modev.core.model.ProfileFieldCatalog
import com.lioisme.modev.core.model.ProfileValueType
import com.lioisme.modev.core.model.stringOf
import com.lioisme.modev.ui.components.HorizontalDivider16
import com.lioisme.modev.ui.components.ModevTopBar
import com.lioisme.modev.ui.components.SettingsSection
import com.lioisme.modev.ui.common.rememberLocaleAwareResources
import com.lioisme.modev.ui.settings.profiles.ProfileFieldEditSheet
import com.lioisme.modev.ui.settings.profiles.ProfileFieldRow
import com.lioisme.modev.ui.settings.profiles.ProfileFieldSpec
import com.lioisme.modev.ui.settings.profiles.profileKindLabelRes
import com.lioisme.modev.ui.settings.profiles.profileSectionTitleRes
import com.lioisme.modev.ui.settings.profiles.slotLabel
import com.lioisme.modev.ui.theme.Dimens

/**
 * 每应用「画像微调」页(V49 R-2 第二层)。
 *
 * 存在的理由:**改一项不该动整张画像**。绑定页只有一个入口(选一张档),要「这台机器别的都按档,
 * 只把型号换掉」就得改档 —— 而档是共享的,一改就波及所有绑它的包。本页把 41 项**逐项**摊开,
 * 每行一个独立动作(改 / 清 / 随机 / 还原),没有任何顺序要求。
 *
 * 三处刻意的设计:
 *  1. **值源就是下发源** —— 屏上画的是 `settings.effectiveSwitchFlow` 展开后的生效快照,
 *     不是「画像正文 + 另一份覆写表」两次拼接;所以「屏上显示的值」与「靶程序拿到的值」同源;
 *  2. **还原 ≠ 清除** —— 还原是删掉这条覆写(交回画像),清除是把这项写成空(显式不伪装);
 *     缺前者用户只能重新手打画像原值,缺后者则「绑了档但这项不要」无法表达;
 *  3. 行控件与画像编辑器**共用同一组件**([ProfileFieldRow] / [ProfileFieldEditSheet]),
 *     两屏不会出现「库里能选、这里只能打字」的口径分叉。
 */
@Composable
fun PerAppProfileTuneScreen(
    viewModel: PerAppFeatureSettingsViewModel,
    onBack: () -> Unit,
) {
    val res = rememberLocaleAwareResources()
    val view by viewModel.tunedView.collectAsStateWithLifecycle()
    val overrides by viewModel.tunedOverrides.collectAsStateWithLifecycle()
    val bound by viewModel.boundProfileFlow.collectAsStateWithLifecycle()
    var editingField by remember { mutableStateOf<ProfileField?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            ModevTopBar(
                title = stringResource(R.string.profile_tune_title),
                onBack = onBack,
            )
        },
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.pagePadding),
        ) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = buildString {
                    append(viewModel.packageName)
                    append("  ·  ")
                    append(
                        if (overrides.isEmpty()) {
                            res.getString(R.string.profile_tune_hint_none)
                        } else {
                            res.getQuantityString(
                                R.plurals.profile_tune_hint_n,
                                overrides.size,
                                overrides.size,
                            )
                        },
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (overrides.isEmpty()) FontWeight.Normal else FontWeight.Medium,
                modifier = Modifier
                    .padding(bottom = Dimens.sectionGapDense)
                    .testTag("profile-tune-status"),
            )

            view.rowsByKind.forEach { (kind, fields) ->
                SettingsSection(
                    title = res.getString(
                        profileSectionTitleRes(),
                        res.getString(profileKindLabelRes(kind)),
                        kind.sourceApi,
                    ),
                    modifier = Modifier.testTag("profile-tune-section-${kind.name}"),
                ) {
                    fields.forEachIndexed { index, field ->
                        if (index > 0) HorizontalDivider16()
                        val overridden = ProfileFieldCatalog.jsonKeyOf(field) in overrides
                        ProfileFieldRow(
                            spec = ProfileFieldSpec.of(
                                res = res,
                                field = field,
                                view = view,
                                // 覆写行点名画像原值,用户才看得出「这行是我改的、还原会回到哪」。
                                followHint = if (overridden) {
                                    res.getString(
                                        R.string.profile_tune_following,
                                        displayValue(res, field, field.stringOf(bound)),
                                    )
                                } else {
                                    null
                                },
                                overridden = overridden,
                            ),
                            onOpen = { editingField = field },
                            onToggleBool = { value ->
                                viewModel.setProfileField(field, value.toString())
                            },
                            onClear = { viewModel.setProfileField(field, "") },
                            onRandomize = { viewModel.randomizeProfileField(field) },
                            onRevert = { viewModel.revertProfileField(field) },
                        )
                    }
                }
                Spacer(Modifier.height(Dimens.sectionGap))
            }
            Spacer(Modifier.height(Dimens.pageBottom))
        }
    }

    editingField?.let { field ->
        ProfileFieldEditSheet(
            field = field,
            view = view,
            onCommit = { text ->
                viewModel.setProfileField(field, text)
                editingField = null
            },
            onDismiss = { editingField = null },
        )
    }
}

/** 画像原值的呈现形态:空白 = 「未设置」,槽型字段走槽标签(与行内大字同一套呈现)。 */
private fun displayValue(res: android.content.res.Resources, field: ProfileField, raw: String): String {
    val text = raw.trim()
    if (text.isEmpty()) return res.getString(R.string.profile_unset)
    return if (field.type == ProfileValueType.NETWORK_TYPE || field.type == ProfileValueType.SCREENSHOT_MODE) {
        slotLabel(res, field, text.toIntOrNull() ?: 0)
    } else {
        text
    }
}
