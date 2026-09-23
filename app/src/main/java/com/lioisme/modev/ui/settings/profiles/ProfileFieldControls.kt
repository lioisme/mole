package com.lioisme.modev.ui.settings.profiles

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lioisme.modev.R
import com.lioisme.modev.core.model.ProfileField
import com.lioisme.modev.core.model.ProfileFieldCatalog
import com.lioisme.modev.core.model.ProfileFieldError
import com.lioisme.modev.core.model.ProfileValueType
import com.lioisme.modev.ui.components.ChoiceOption
import com.lioisme.modev.ui.components.ModevChoiceSheet
import com.lioisme.modev.ui.components.ModevInputSheet
import com.lioisme.modev.ui.components.ModevSearchableChoiceSheet
import com.lioisme.modev.ui.components.SHEET_MAX_VISIBLE_ROWS
import com.lioisme.modev.ui.apps.ToggleListItem
import com.lioisme.modev.ui.common.rememberLocaleAwareResources
import com.lioisme.modev.ui.theme.Tokens

/**
 * 画像字段的**共享控件族**(V49 R-2 抽出):一行一参数 + 该行的编辑弹层。
 *
 * 抽出来的理由不是「复用代码」,而是 R-2 的第二条验收要求**两处必须是同一套逻辑**:
 * 画像编辑器与每应用「画像微调」区若各画一份行/各写一份弹层,就会出现
 * 「库里能选、应用页只能打字」这类口径分叉(本仓 V48-DFC-03 的同族病:同一状态两处呈现)。
 * 两屏都吃同一个 [ProfileEditorView] 快照与同一组回调 ⇒ 控件、校验、枚举表、
 * 「改为手动填写」逃生口天然同源。
 *
 * 机型库 / 运营商库 / Android 版本三个**成对选择器不在本文件**:它们是「画像库正文」这一层
 * 的捷径,不属于「一个字段一个动作」的通路(R-2 明令成对捷径不得成为唯一入口)。
 */

/** 一行的全部呈现值(由 [ProfileEditorView] 快照派生,组合侧以值为参数)。 */
internal data class ProfileFieldSpec(
    val field: ProfileField,
    val label: String,
    val value: String,
    val filled: Boolean,
    val error: String?,
    val randomizable: Boolean,
    val bool: Boolean,
    val boolChecked: Boolean,
    /** 本应用覆写态下的「画像原值」提示(画像编辑器里恒为 null)。 */
    val followHint: String? = null,
    /** 该项当前是否由本应用覆写(决定行尾「还原为画像值」按钮是否出现;编辑器恒 false)。 */
    val overridden: Boolean = false,
) {
    companion object {
        fun of(
            res: android.content.res.Resources,
            field: ProfileField,
            view: ProfileEditorView,
            followHint: String? = null,
            overridden: Boolean = false,
        ): ProfileFieldSpec {
            val text = view.texts[field].orEmpty()
            val bool = field.type == ProfileValueType.BOOL
            return ProfileFieldSpec(
                field = field,
                label = res.getString(profileFieldLabelRes(field)),
                value = when {
                    text.isEmpty() -> res.getString(R.string.profile_unset)
                    field.type == ProfileValueType.NETWORK_TYPE ||
                        field.type == ProfileValueType.SCREENSHOT_MODE ->
                        slotLabel(res, field, text.toIntOrNull() ?: 0)

                    else -> text
                },
                filled = text.isNotEmpty(),
                error = view.errors[field]?.let { res.getString(profileFieldErrorRes(it)) },
                randomizable = ProfileFieldCatalog.isRandomizable(field) && !bool,
                bool = bool,
                boolChecked = text.toBooleanStrictOrNull() == true,
                followHint = followHint,
                overridden = overridden,
            )
        }
    }
}

/** 一行一参数:标签 + 当前值(或未设置)+ 就近随机 / 清除;BOOL 行走内 Switch。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileFieldRow(
    spec: ProfileFieldSpec,
    onOpen: () -> Unit,
    onToggleBool: (Boolean) -> Unit,
    onClear: () -> Unit,
    onRandomize: () -> Unit,
    onRevert: () -> Unit = {},
) {
    val res = rememberLocaleAwareResources()
    if (spec.bool) {
        ToggleListItem(
            label = spec.label,
            checked = spec.boolChecked,
            onCheckedChange = onToggleBool,
            testTag = "profile-toggle-${spec.field.name}",
        )
        return
    }
    ListItem(
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        headlineContent = {
            Text(
                spec.label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Column {
                Text(
                    spec.value,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = if (spec.filled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = Tokens.emptyIconHintAlpha)
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("profile-value-${spec.field.name}"),
                )
                // 覆写态下点名所绑画像的原值:用户要能看出「这项我改了」而不是「档就是这样」。
                spec.followHint?.let { hint ->
                    Text(
                        hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("profile-follow-${spec.field.name}"),
                    )
                }
                if (spec.error != null) {
                    Text(
                        spec.error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("profile-error-${spec.field.name}"),
                    )
                }
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (spec.randomizable) {
                    TipIconButtonRow(
                        tooltip = res.getString(R.string.profile_randomize_one),
                        onClick = onRandomize,
                        testTag = "profile-randomize-${spec.field.name}",
                    ) {
                        Icon(
                            Icons.Filled.Calculate,
                            contentDescription = res.getString(R.string.profile_randomize_one),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                if (spec.overridden) {
                    // 还原 ≠ 清除:清除是「这项我不要」(写空),还原是「交回画像决定」(删偏差)。
                    // 两者语义不同,缺一个就是半功能 —— 用户改完想退回去只能重新手打画像原值。
                    TipIconButtonRow(
                        tooltip = res.getString(R.string.profile_revert_to_profile),
                        onClick = onRevert,
                        testTag = "profile-revert-${spec.field.name}",
                    ) {
                        Icon(
                            Icons.Filled.Undo,
                            contentDescription = res.getString(R.string.profile_revert_to_profile),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                if (spec.filled) {
                    TipIconButtonRow(
                        tooltip = res.getString(R.string.profile_clear_one),
                        onClick = onClear,
                        testTag = "profile-clear-${spec.field.name}",
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = res.getString(R.string.profile_clear_one),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("profile-row-${spec.field.name}")
            .clickable(onClick = onOpen),
    )
}

/**
 * 单字段编辑弹层(V49 R-2 从画像编辑器原样搬来,逻辑零改写):
 * 枚举域 → 可搜列表且「改为手动填写」恒留;槽型 → 选项表;其余 → 输入条(实时校验)。
 * BOOL 行由行内 Switch 承担,调用方不会为它开本弹层。
 */
@Composable
internal fun ProfileFieldEditSheet(
    field: ProfileField,
    view: ProfileEditorView,
    onCommit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val res = rememberLocaleAwareResources()
    var manualEntryFor by remember { mutableStateOf<ProfileField?>(null) }
    val enumerated =
        if (manualEntryFor == field) null else com.lioisme.modev.core.model.ProfileOptionSource.optionsFor(field)
    val label = stringResource(profileFieldLabelRes(field))

    if (enumerated != null) {
        ModevSearchableChoiceSheet(
            title = label,
            options = buildList {
                add(
                    ChoiceOption(
                        value = MANUAL_ENTRY,
                        label = res.getString(R.string.profile_picker_manual),
                        testTag = "profile-option-${field.name}-manual",
                    ),
                )
                addAll(
                    enumerated.map { value ->
                        ChoiceOption(
                            value = value,
                            label = value,
                            testTag = "profile-option-${field.name}-$value",
                        )
                    },
                )
            },
            selected = view.texts[field],
            placeholder = stringResource(R.string.profile_device_search_hint),
            emptyText = stringResource(R.string.profile_device_no_match),
            truncatedText = { total ->
                res.getString(R.string.profile_device_truncated, SHEET_MAX_VISIBLE_ROWS, total)
            },
            onSelect = { value ->
                if (value == MANUAL_ENTRY) {
                    manualEntryFor = field
                } else {
                    onCommit(value)
                }
            },
            onDismiss = {
                manualEntryFor = null
                onDismiss()
            },
            modifier = Modifier.testTag("profile-enum-${field.name}"),
        )
    } else {
        when (field.type) {
            // 布尔项 = 行内 Switch,无弹层(契约 #20 开关行形态;置 false 即取消该项干预)。
            ProfileValueType.BOOL -> Unit

            ProfileValueType.NETWORK_TYPE, ProfileValueType.SCREENSHOT_MODE -> ModevChoiceSheet(
                title = label,
                options = ProfileEditorState.acceptedSlots(field).map { slot ->
                    ChoiceOption(
                        value = slot,
                        label = slotLabel(res, field, slot),
                        testTag = "profile-slot-${field.name}-$slot",
                    )
                },
                selected = view.texts[field]?.toIntOrNull()
                    ?: ProfileEditorState.unhookSlotOf(field),
                onSelect = { slot ->
                    // 「不干预」槽必须写成空串而不是槽号(与 ProfileEditorState.writeSlot 同一归一):
                    // 写槽号会让一项明明没开却被算成「已填」,行上的清除态与偏差计数随之分叉。
                    onCommit(
                        if (slot == ProfileEditorState.unhookSlotOf(field)) "" else slot.toString(),
                    )
                },
                onDismiss = onDismiss,
                modifier = Modifier.testTag("profile-choice-${field.name}"),
            )

            else -> ModevInputSheet(
                title = label,
                label = label,
                initialValue = view.texts[field].orEmpty(),
                hint = fieldHint(res, field),
                keyboardOptions = KeyboardOptions(
                    keyboardType = when (field.type) {
                        ProfileValueType.INT -> KeyboardType.Number
                        ProfileValueType.DOUBLE -> KeyboardType.Decimal
                        else -> KeyboardType.Text
                    },
                ),
                digitOnly = field.type == ProfileValueType.INT,
                maxLength = maxInputLengthOf(field),
                liveError = { text -> liveFieldError(res, field, text) },
                validate = { true },
                onSubmit = onCommit,
                onDismiss = onDismiss,
                modifier = Modifier.testTag("profile-input-${field.name}"),
                inputTestTag = "profile-input-field",
                confirmTestTag = "profile-input-confirm",
            )
        }
    }
}

/** 枚举列表里「改为手动填写」那一项的哨兵值(非用户可见文本;画像编辑器同一常量)。 */
internal const val MANUAL_ENTRY = "__manual__"

/** 槽位呈现名(网络类型与截屏策略两型;无对应资源时回落槽号)。 */
internal fun slotLabel(res: android.content.res.Resources, field: ProfileField, slot: Int): String {
    @StringRes val labelRes = profileSlotLabelRes(field, slot)
    return if (labelRes == 0) slot.toString() else res.getString(labelRes)
}

/** 输入期实时校验:结论 → 四语文案(null = 当前输入合法)。 */
internal fun liveFieldError(
    res: android.content.res.Resources,
    field: ProfileField,
    text: String,
): String? = ProfileFieldCatalog.validate(field, text)?.let { res.getString(profileFieldErrorRes(it)) }

/** 输入框提示:数值项给有效域,有形态要求的给形态要求,自由文本不给噪音。 */
internal fun fieldHint(res: android.content.res.Resources, field: ProfileField): String? {
    ProfileFieldCatalog.rangeOf(field)?.let { range ->
        return res.getString(R.string.profile_range_hint, "${range.first}-${range.last}")
    }
    @StringRes val shapeRes = shapeHintResOf(field)
    return if (shapeRes == 0) null else res.getString(shapeRes)
}

/** 该参数的形态要求(目录里确有格式校验的字段才有;其余返回 0 = 无提示)。 */
@StringRes
private fun shapeHintResOf(field: ProfileField): Int = when (field) {
    ProfileField.SIM_OPERATOR -> R.string.profile_error_sim_operator_format
    ProfileField.SIM_COUNTRY -> R.string.profile_error_sim_country_format
    ProfileField.IMEI -> R.string.profile_error_imei_format
    ProfileField.ANDROID_ID -> R.string.profile_error_android_id_format
    ProfileField.WIFI_BSSID, ProfileField.WIFI_MAC_ADDRESS -> R.string.profile_error_mac_format
    ProfileField.LANGUAGE -> R.string.profile_error_language_format
    ProfileField.TIME_ZONE -> R.string.profile_error_time_zone_unknown
    ProfileField.FINGERPRINT -> R.string.profile_error_fingerprint_structure
    ProfileField.LONGITUDE, ProfileField.LATITUDE -> R.string.profile_error_number_out_of_range
    else -> 0
}

/** 输入长度上限:数值项取有效域上界的位数(电池 3、小区标识 9),文本项不限。 */
private fun maxInputLengthOf(field: ProfileField): Int? =
    ProfileFieldCatalog.rangeOf(field)?.last?.toString()?.length

/** 行内图标动作(编辑器与每应用微调区共用形态)。 */
@Composable
private fun TipIconButtonRow(
    tooltip: String,
    onClick: () -> Unit,
    testTag: String,
    content: @Composable () -> Unit,
) {
    com.lioisme.modev.ui.components.TipIconButton(
        tooltip = tooltip,
        onClick = onClick,
        modifier = Modifier.testTag(testTag),
        content = content,
    )
}
