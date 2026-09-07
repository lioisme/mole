package com.lioisme.modev.ui.records

import androidx.annotation.StringRes
import com.lioisme.modev.R
import com.lioisme.modev.core.model.RecordType

/**
 * 记录类型 → 资源映射(V40 T4 双维度记录浏览:按类型全局列表与数据管理页
 * 共享单一事实源,取代 DataSettingsScreen 私有副本):
 * - 计数行标题(数据管理页类型计数区);
 * - 按类型清空确认文案(全局类型列表「清空本类」= 清空该类型全部应用,
 *   与单应用列表的 danger_clear_page_records_msg 区分)。
 * 新增 RecordType 补齐两处 when(编译器穷尽性强制)。
 */

/** 类型计数行标题(全 10 类型;既有 5 键保留,新增 5 键同构命名)。 */
@StringRes
internal fun recordTypeCountLabelRes(type: RecordType): Int = when (type) {
    RecordType.CRYPTO -> R.string.settings_records_algorithm
    RecordType.DATABASE -> R.string.settings_records_database
    RecordType.BEHAVIOR -> R.string.settings_records_behavior
    RecordType.PREFS -> R.string.settings_records_prefs
    RecordType.WEBVIEW -> R.string.settings_records_webview
    RecordType.NETWORK -> R.string.settings_records_network
    RecordType.DEVICE_ENV -> R.string.settings_records_device_env
    RecordType.UI_INTERACTION -> R.string.settings_records_ui_interaction
    RecordType.SYSTEM -> R.string.settings_records_system
    RecordType.PROCESS_CONTROL -> R.string.settings_records_process_control
}

/** 按类型清空确认文案(语义 = 该类型 × 全部应用,危险确认)。 */
@StringRes
internal fun recordTypeClearMsgRes(type: RecordType): Int = when (type) {
    RecordType.CRYPTO -> R.string.danger_clear_algorithm_msg
    RecordType.DATABASE -> R.string.danger_clear_database_msg
    RecordType.BEHAVIOR -> R.string.danger_clear_behavior_msg
    RecordType.PREFS -> R.string.danger_clear_prefs_msg
    RecordType.WEBVIEW -> R.string.danger_clear_webview_msg
    RecordType.NETWORK -> R.string.danger_clear_network_msg
    RecordType.DEVICE_ENV -> R.string.danger_clear_device_env_msg
    RecordType.UI_INTERACTION -> R.string.danger_clear_ui_interaction_msg
    RecordType.SYSTEM -> R.string.danger_clear_system_msg
    RecordType.PROCESS_CONTROL -> R.string.danger_clear_process_control_msg
}

/** 全局类型列表搜索占位文案(复用单应用列表既有 per-type 搜索键)。 */
@StringRes
internal fun recordTypeSearchPlaceholderRes(type: RecordType): Int = when (type) {
    RecordType.CRYPTO -> R.string.algorithm_records_search
    RecordType.DATABASE -> R.string.database_records_search
    RecordType.BEHAVIOR -> R.string.behavior_records_search
    RecordType.PREFS -> R.string.prefs_records_search
    RecordType.WEBVIEW -> R.string.webview_records_search
    RecordType.NETWORK -> R.string.network_records_search
    RecordType.DEVICE_ENV -> R.string.device_records_search
    RecordType.UI_INTERACTION -> R.string.ui_interaction_records_search
    RecordType.SYSTEM -> R.string.system_records_search
    RecordType.PROCESS_CONTROL -> R.string.process_control_records_search
}
