package com.lioisme.modev.ui.settings.profiles

import androidx.annotation.StringRes
import com.lioisme.modev.R
import com.lioisme.modev.core.model.BuiltinDeviceProfiles
import com.lioisme.modev.core.model.ProfileConflict
import com.lioisme.modev.core.model.ProfileField
import com.lioisme.modev.core.model.ProfileFieldError
import com.lioisme.modev.core.model.ProfileKind

/**
 * 画像配置面的**标签映射唯一出口**(V47 WP-5)。
 *
 * 与 [com.lioisme.modev.ui.apps.KindLabelRes] 同族但**独立成表**:干预组标签的事实源是
 * [ProfileKind] 枚举本体(穷尽 `when`,新增组未映射即编译期红),而不是
 * `CategoryTaxonomy` 的 (type,id) 对 —— 画像参数面在 WP-3 原子登记完成之前
 * 不属于 taxonomy 观测面,借道会把本包与那条在途改造耦上。
 *
 * 本文件**零 Compose、零 Android 框架**(只有 [StringRes] 纯注解),故 JVM 直测
 * (`ProfileLabelsTest` 锁四张表的穷尽性与「不得回落 unknown_error」)。
 */

/**
 * 分区标题的**组装**格式(V49 R-1):本地化名 + 来源 API 标识。
 *
 * API 名(`android.os.Build` / `Settings.Secure` 之类)是**代码标识符不是文案** ⇒ 不进四语表,
 * 由 [ProfileKind.sourceApi] 以参数注入。这样「每个分区都点名它的底层来源」这条验收既成立,
 * 又不会为了 20×4 条同一串 ASCII 而养一份不可维护的翻译。
 */
@StringRes
internal fun profileSectionTitleRes(): Int = R.string.profile_section_title

/**
 * 注入侧**已接管**的画像族名单(V49 D-3:kind 登记与钩子实现同一提交原子落 ⇒
 * `DeviceEnvKind` 里出现 `PROFILE_` 项 ≡ 该族在靶程序真的生效)。
 *
 * 派生而非写死:接管边界这句诚实话每接一批就会过期一次,写死的散文必然变成谎报
 * (AGENTS #30 明令「撤掉它 = 撒谎」的那条边界,同样禁止它朝着另一个方向过期)。
 */
internal fun takenOverProfileFamilies(res: android.content.res.Resources): String {
    val kinds = com.lioisme.modev.core.model.DeviceEnvKind.entries
        .filter { it.name.startsWith("PROFILE_") }
    if (kinds.isEmpty()) return res.getString(R.string.profile_takeover_none)
    return kinds.joinToString(", ") { res.getString(com.lioisme.modev.ui.components.deviceEnvKindLabelRes(it)) }
}

/**
 * 底层来源族标签(UI 分区标题的本地化半段 + 实时生效预览 chips 共用同一键)。
 *
 * 一个概念一个键(#31②):V48 的 `profile_section_*` 十条与 `profile_kind_*` 十八条是
 * **同一条分类轴的两套名字**(section ≡ kind ≡ 钩子族),故随 `ProfileSection` 一起退役。
 * 新增类别未映射即编译期红(穷尽 `when`)。
 */
@StringRes
internal fun profileKindLabelRes(kind: ProfileKind): Int = when (kind) {
    ProfileKind.PROFILE_BUILD_IDENTITY -> R.string.profile_kind_build_identity
    ProfileKind.PROFILE_BUILD_VERSION -> R.string.profile_kind_build_version
    ProfileKind.PROFILE_DENSITY -> R.string.profile_kind_density
    ProfileKind.PROFILE_CPU -> R.string.profile_kind_cpu
    ProfileKind.PROFILE_ANDROID_ID -> R.string.profile_kind_android_id
    ProfileKind.PROFILE_TELEPHONY_ID -> R.string.profile_kind_telephony_id
    ProfileKind.PROFILE_SIM -> R.string.profile_kind_sim
    ProfileKind.PROFILE_NETWORK -> R.string.profile_kind_network
    ProfileKind.PROFILE_WIFI -> R.string.profile_kind_wifi
    ProfileKind.PROFILE_CELL -> R.string.profile_kind_cell
    ProfileKind.PROFILE_LOCATION -> R.string.profile_kind_location
    ProfileKind.PROFILE_WIFI_LOCATION_HIDE -> R.string.profile_kind_wifi_location_hide
    ProfileKind.PROFILE_CELL_LOCATION_HIDE -> R.string.profile_kind_cell_location_hide
    ProfileKind.PROFILE_LOCALE -> R.string.profile_kind_locale
    ProfileKind.PROFILE_TIME_ZONE -> R.string.profile_kind_time_zone
    ProfileKind.PROFILE_APP_VERSION -> R.string.profile_kind_app_version
    ProfileKind.PROFILE_BATTERY -> R.string.profile_kind_battery
    ProfileKind.PROFILE_SCREENSHOT -> R.string.profile_kind_screenshot
    ProfileKind.PROFILE_BLANK_CONTENT -> R.string.profile_kind_blank_content
    ProfileKind.PROFILE_APP_LIST -> R.string.profile_kind_blank_app_list
}

/** 参数字段标签(一行一参数,41 项全覆盖)。 */
@StringRes
internal fun profileFieldLabelRes(field: ProfileField): Int = when (field) {
    ProfileField.BRAND -> R.string.profile_field_brand
    ProfileField.MANUFACTURER -> R.string.profile_field_manufacturer
    ProfileField.MODEL -> R.string.profile_field_model
    ProfileField.PRODUCT -> R.string.profile_field_product
    ProfileField.DEVICE -> R.string.profile_field_device
    ProfileField.BOARD -> R.string.profile_field_board
    ProfileField.HARDWARE -> R.string.profile_field_hardware
    ProfileField.BUILD_ID -> R.string.profile_field_build_id
    ProfileField.FINGERPRINT -> R.string.profile_field_fingerprint
    ProfileField.ANDROID_VERSION -> R.string.profile_field_android_version
    ProfileField.SDK_INT -> R.string.profile_field_sdk_int
    ProfileField.DENSITY_DPI -> R.string.profile_field_density_dpi
    ProfileField.CPU_CORES -> R.string.profile_field_cpu_cores
    ProfileField.NETWORK_TYPE -> R.string.profile_field_network_type
    ProfileField.WIFI_SSID -> R.string.profile_field_wifi_ssid
    ProfileField.WIFI_BSSID -> R.string.profile_field_wifi_bssid
    ProfileField.WIFI_MAC_ADDRESS -> R.string.profile_field_wifi_mac
    ProfileField.SIM_OPERATOR -> R.string.profile_field_sim_operator
    ProfileField.SIM_OPERATOR_NAME -> R.string.profile_field_sim_operator_name
    ProfileField.SIM_COUNTRY -> R.string.profile_field_sim_country
    ProfileField.ANDROID_ID -> R.string.profile_field_android_id
    ProfileField.IMEI -> R.string.profile_field_imei
    ProfileField.PHONE_NUM -> R.string.profile_field_phone_num
    ProfileField.LAC -> R.string.profile_field_lac
    ProfileField.CID -> R.string.profile_field_cid
    ProfileField.LONGITUDE -> R.string.profile_field_longitude
    ProfileField.LATITUDE -> R.string.profile_field_latitude
    ProfileField.RANDOM_OFFSET -> R.string.profile_field_random_offset
    ProfileField.WIFI_LOCATION_HIDE -> R.string.profile_field_wifi_location_hide
    ProfileField.CELL_LOCATION_HIDE -> R.string.profile_field_cell_location_hide
    ProfileField.LANGUAGE -> R.string.profile_field_language
    ProfileField.TIME_ZONE -> R.string.profile_field_time_zone
    ProfileField.VERSION_CODE -> R.string.profile_field_version_code
    ProfileField.VERSION_NAME -> R.string.profile_field_version_name
    ProfileField.BATTERY_LEVEL -> R.string.profile_field_battery_level
    ProfileField.SCREENSHOTS_FLAG -> R.string.profile_field_screenshot_mode
    ProfileField.PASS_CONTACTS -> R.string.profile_field_pass_contacts
    ProfileField.PASS_PHOTO -> R.string.profile_field_pass_photo
    ProfileField.PASS_VIDEO -> R.string.profile_field_pass_video
    ProfileField.PASS_AUDIO -> R.string.profile_field_pass_audio
    ProfileField.PASS_APPLICATIONS -> R.string.profile_field_pass_applications
}

/** 校验结论文案(sheet 行内错误与保存闸共用一份)。 */
@StringRes
internal fun profileFieldErrorRes(error: ProfileFieldError): Int = when (error) {
    ProfileFieldError.NUMBER_INVALID -> R.string.profile_error_number_invalid
    ProfileFieldError.NUMBER_OUT_OF_RANGE -> R.string.profile_error_number_out_of_range
    ProfileFieldError.SIM_OPERATOR_FORMAT -> R.string.profile_error_sim_operator_format
    ProfileFieldError.SIM_COUNTRY_FORMAT -> R.string.profile_error_sim_country_format
    ProfileFieldError.IMEI_FORMAT -> R.string.profile_error_imei_format
    ProfileFieldError.ANDROID_ID_FORMAT -> R.string.profile_error_android_id_format
    ProfileFieldError.MAC_FORMAT -> R.string.profile_error_mac_format
    ProfileFieldError.LANGUAGE_FORMAT -> R.string.profile_error_language_format
    ProfileFieldError.TIME_ZONE_UNKNOWN -> R.string.profile_error_time_zone_unknown
    ProfileFieldError.FINGERPRINT_STRUCTURE -> R.string.profile_error_fingerprint_structure
    ProfileFieldError.NETWORK_VALUE_INVALID -> R.string.profile_error_network_value_invalid
    ProfileFieldError.SCREENSHOT_VALUE_INVALID -> R.string.profile_error_screenshot_value_invalid
}

/** 配置矛盾文案(内联渲染,不弹阻断式对话框)。 */
@StringRes
internal fun profileConflictLabelRes(conflict: ProfileConflict): Int = when (conflict) {
    ProfileConflict.COORDINATE_HALF_SET -> R.string.profile_conflict_coordinate_half_set
    ProfileConflict.OFFSET_WITHOUT_COORDINATES -> R.string.profile_conflict_offset_without_coordinates
    ProfileConflict.APP_LIST_WITHOUT_CONTENT -> R.string.profile_conflict_app_list_without_content
}

/**
 * 枚举槽字段的槽位标签。
 *
 * **未知槽位返回 0**(= 无资源),调用方回落渲染十进制数字:槽集由
 * `ProfileFieldCatalog.validate` 反推,目录若加槽位,UI 不能因此崩或漏行 ——
 * 宁可临时显示裸数字,也不在 UI 里留第二张槽表。
 */
@StringRes
internal fun profileSlotLabelRes(field: ProfileField, slot: Int): Int = when (field) {
    ProfileField.NETWORK_TYPE -> when (slot) {
        -1 -> R.string.profile_slot_network_hide
        0 -> R.string.profile_slot_unhook
        1 -> R.string.profile_slot_network_wifi
        2 -> R.string.profile_slot_network_2g
        3 -> R.string.profile_slot_network_3g
        4 -> R.string.profile_slot_network_4g
        5 -> R.string.profile_slot_network_5g
        else -> 0
    }

    ProfileField.SCREENSHOTS_FLAG -> when (slot) {
        -1 -> R.string.profile_slot_unhook
        0 -> R.string.profile_slot_screenshot_deny
        1 -> R.string.profile_slot_screenshot_allow
        else -> 0
    }

    else -> 0
}

/**
 * 内置档展示名(**按 seedId 取资源**,`DeviceProfileEntry.name` 对内置行恒为空串)。
 * 未知 seedId 回落 [R.string.unknown_error](理论上不可达:账本只补 [BuiltinDeviceProfiles.BUILTINS])。
 */
@StringRes
internal fun builtinProfileNameRes(seedId: String): Int = when (seedId) {
    BuiltinDeviceProfiles.PIXEL_7_PRO -> R.string.feature_profile_pixel7pro
    BuiltinDeviceProfiles.XIAOMI_13 -> R.string.feature_profile_xiaomi13
    BuiltinDeviceProfiles.GALAXY_S23_ULTRA -> R.string.feature_profile_galaxy_s23ultra
    BuiltinDeviceProfiles.PIXEL_6 -> R.string.feature_profile_pixel6
    BuiltinDeviceProfiles.PIXEL_6A -> R.string.feature_profile_pixel6a
    BuiltinDeviceProfiles.PIXEL_8 -> R.string.feature_profile_pixel8
    BuiltinDeviceProfiles.XIAOMI_14_ULTRA -> R.string.feature_profile_xiaomi14ultra
    BuiltinDeviceProfiles.GALAXY_S24_ULTRA -> R.string.feature_profile_galaxys24ultra
    BuiltinDeviceProfiles.ONEPLUS_11 -> R.string.feature_profile_oneplus11
    else -> R.string.unknown_error
}
