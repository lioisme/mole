package com.lioisme.modev.core.model

import java.util.TimeZone

/**
 * 画像字段校验结论(UI 行内错误文案的唯一映射源,四语资源在 `ui` 侧落地)。
 */
enum class ProfileFieldError {
    /** 数值不可解析。 */
    NUMBER_INVALID,

    /** 数值越界(界由 [ProfileFieldCatalog] 单点声明)。 */
    NUMBER_OUT_OF_RANGE,

    /** PLMN 须为五位或六位纯数字。 */
    SIM_OPERATOR_FORMAT,

    /** SIM 国家码须为两位字母。 */
    SIM_COUNTRY_FORMAT,

    /** IMEI 须为十五位数字且 Luhn 校验位正确。 */
    IMEI_FORMAT,

    /** Android ID 须为十六进制串。 */
    ANDROID_ID_FORMAT,

    /** MAC 地址形态非法。 */
    MAC_FORMAT,

    /** 语言标识形态非法。 */
    LANGUAGE_FORMAT,

    /** 时区标识不在系统时区库内。 */
    TIME_ZONE_UNKNOWN,

    /** Fingerprint 结构缺少分隔符。 */
    FINGERPRINT_STRUCTURE,

    /** 网络类型槽位取值不在允许集内。 */
    NETWORK_VALUE_INVALID,

    /** 截屏策略槽位取值不在允许集内。 */
    SCREENSHOT_VALUE_INVALID,
}

/**
 * 画像字段目录(V47 T1):**41 个字段的行为面唯一事实源**。
 *
 * [ProfileField] 声明静态归属(kind/section/type/基线键),本对象声明行为:
 * 校验、取值界、wire 与 MCP 键派生、分区行序、按类别分组。
 * UI 表单、MCP 声明面、压测期望表三处一律消费本目录,**禁止任何一处手抄字段清单**
 * (对账由 `ProfileFieldCatalogTest` 与 `ProfileFieldPropertyParityTest` 双向锁定)。
 */
object ProfileFieldCatalog {

    /** 分区的字段行序(UI 逐分区渲染,行序 = 本表序)。 */
    val bySection: Map<ProfileSection, List<ProfileField>> =
        ProfileSection.entries.associateWith { section ->
            ProfileField.entries.filter { it.section == section }
        }.filterValues { it.isNotEmpty() }

    /** 干预类别 → 该类别承载的字段(注入侧门控与 UI 分组共用)。 */
    val byKind: Map<ProfileKind, List<ProfileField>> =
        ProfileKind.entries.associateWith { kind ->
            ProfileField.entries.filter { it.kind == kind }
        }

    /** wire 与 MCP 的键名:基线键优先,Modev 超基线项按枚举名派生小驼峰。 */
    fun jsonKeyOf(field: ProfileField): String = field.baselineKey ?: field.name.toCamelCase()

    /** 按 wire/MCP 键反查字段(未知键返回 null ⇒ 调用方须硬失败,禁静默丢弃)。 */
    fun fieldByJsonKey(key: String): ProfileField? =
        ProfileField.entries.firstOrNull { jsonKeyOf(it) == key }

    /** 生效值界:仅数值与浮点字段有界,文本字段返回 null(长度不设限,由 UI 输入框约束)。 */
    fun rangeOf(field: ProfileField): IntRange? = when (field) {
        ProfileField.SDK_INT -> 1..40
        ProfileField.DENSITY_DPI -> ProfileValues.DENSITY_MIN..ProfileValues.DENSITY_MAX
        ProfileField.CPU_CORES -> 1..ProfileValues.CPU_CORES_MAX
        ProfileField.LAC -> 0..65533
        ProfileField.CID -> 0..268435455
        ProfileField.VERSION_CODE -> 0..Int.MAX_VALUE
        ProfileField.BATTERY_LEVEL -> 0..ProfileValues.BATTERY_MAX
        else -> null
    }

    /**
     * 编辑态文本校验:空串恒通过(空 = 取消干预,不是错误),非空须形态与域同时合法。
     * 返回 null 表示合法。
     */
    fun validate(field: ProfileField, raw: String): ProfileFieldError? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        return when (field) {
            ProfileField.SIM_OPERATOR ->
                if (text.length in 5..6 && text.all { it.isDigit() }) null else ProfileFieldError.SIM_OPERATOR_FORMAT

            ProfileField.SIM_COUNTRY ->
                if (text.length == 2 && text.all { it.isLetter() }) null else ProfileFieldError.SIM_COUNTRY_FORMAT

            ProfileField.IMEI -> if (isLuhnValidImei(text)) null else ProfileFieldError.IMEI_FORMAT

            ProfileField.ANDROID_ID -> if (isHexOfLength(text, 16)) null else ProfileFieldError.ANDROID_ID_FORMAT

            ProfileField.WIFI_BSSID, ProfileField.WIFI_MAC_ADDRESS ->
                if (isMacAddress(text)) null else ProfileFieldError.MAC_FORMAT

            ProfileField.LANGUAGE -> if (isLanguageTag(text)) null else ProfileFieldError.LANGUAGE_FORMAT

            ProfileField.TIME_ZONE -> if (isKnownTimeZone(text)) null else ProfileFieldError.TIME_ZONE_UNKNOWN

            ProfileField.FINGERPRINT ->
                if (text.contains('/') && text.contains(':')) null else ProfileFieldError.FINGERPRINT_STRUCTURE

            ProfileField.LONGITUDE ->
                checkDoubleInRange(text, -180.0..180.0)

            ProfileField.LATITUDE ->
                checkDoubleInRange(text, -90.0..90.0)

            ProfileField.NETWORK_TYPE -> {
                val slot = text.toIntOrNull() ?: return ProfileFieldError.NETWORK_VALUE_INVALID
                if (slot in ProfileValues.NET_NONE..ProfileValues.NET_MOBILE_5G) {
                    null
                } else {
                    ProfileFieldError.NETWORK_VALUE_INVALID
                }
            }

            ProfileField.SCREENSHOTS_FLAG -> {
                val slot = text.toIntOrNull() ?: return ProfileFieldError.SCREENSHOT_VALUE_INVALID
                if (slot in ProfileValues.SCREENSHOT_UNHOOK..ProfileValues.SCREENSHOT_ALLOW) {
                    null
                } else {
                    ProfileFieldError.SCREENSHOT_VALUE_INVALID
                }
            }

            ProfileField.SDK_INT, ProfileField.DENSITY_DPI, ProfileField.CPU_CORES,
            ProfileField.LAC, ProfileField.CID, ProfileField.VERSION_CODE,
            ProfileField.BATTERY_LEVEL,
            -> {
                val parsed = text.toIntOrNull()
                    ?: return ProfileFieldError.NUMBER_INVALID
                val bounds = rangeOf(field)
                if (bounds != null && parsed !in bounds) ProfileFieldError.NUMBER_OUT_OF_RANGE else null
            }

            ProfileField.RANDOM_OFFSET, ProfileField.WIFI_LOCATION_HIDE, ProfileField.CELL_LOCATION_HIDE,
            ProfileField.PASS_CONTACTS, ProfileField.PASS_PHOTO, ProfileField.PASS_VIDEO,
            ProfileField.PASS_AUDIO, ProfileField.PASS_APPLICATIONS,
            -> if (text.toBooleanStrictOrNull() != null) null else ProfileFieldError.NUMBER_INVALID

            // 自由文本身份字段:非空即生效,形态不设硬校验(跨字段自洽性由一致性闸单独裁决)。
            ProfileField.BRAND, ProfileField.MANUFACTURER, ProfileField.MODEL, ProfileField.PRODUCT,
            ProfileField.DEVICE, ProfileField.BOARD, ProfileField.HARDWARE, ProfileField.BUILD_ID,
            ProfileField.ANDROID_VERSION, ProfileField.WIFI_SSID, ProfileField.SIM_OPERATOR_NAME,
            ProfileField.PHONE_NUM, ProfileField.VERSION_NAME,
            -> null
        }
    }

    /** 是否随机源参与「一键随机」派生。 */
    fun isRandomizable(field: ProfileField): Boolean = field in RANDOMIZABLE

    /**
     * 一键随机覆盖的字段集(基线 `applyRandom` 同族:同一台设备 + 同一 Android 世代 +
     * 同一运营商整组同源派生,非逐字段独立抽样)。
     * 恒不随机:主板与硬件代号、DPI、语言、基站参数、应用版本、截屏策略、
     * 空白通行证五开关与两个定位隐藏开关、坐标抖动开关。
     */
    val RANDOMIZABLE: Set<ProfileField> = setOf(
        ProfileField.BRAND,
        ProfileField.MANUFACTURER,
        ProfileField.MODEL,
        ProfileField.DEVICE,
        ProfileField.PRODUCT,
        ProfileField.BUILD_ID,
        ProfileField.FINGERPRINT,
        ProfileField.ANDROID_VERSION,
        ProfileField.SDK_INT,
        ProfileField.NETWORK_TYPE,
        ProfileField.WIFI_SSID,
        ProfileField.WIFI_BSSID,
        ProfileField.WIFI_MAC_ADDRESS,
        ProfileField.SIM_OPERATOR,
        ProfileField.SIM_OPERATOR_NAME,
        ProfileField.SIM_COUNTRY,
        ProfileField.ANDROID_ID,
        ProfileField.IMEI,
        ProfileField.PHONE_NUM,
        ProfileField.LONGITUDE,
        ProfileField.LATITUDE,
        ProfileField.TIME_ZONE,
        ProfileField.BATTERY_LEVEL,
    )

    private fun checkDoubleInRange(text: String, bounds: ClosedFloatingPointRange<Double>): ProfileFieldError? {
        val parsed = text.toDoubleOrNull() ?: return ProfileFieldError.NUMBER_INVALID
        return if (parsed in bounds) null else ProfileFieldError.NUMBER_OUT_OF_RANGE
    }

    /** 十五位数字 + Luhn 校验位(基线 `randomIMEI` 的合法性判据)。 */
    fun isLuhnValidImei(text: String): Boolean {
        if (text.length != 15 || !text.all { it.isDigit() }) return false
        var sum = 0
        for (index in text.indices) {
            val digit = text[index] - '0'
            sum += if ((text.length - index) % 2 == 0) {
                val doubled = digit * 2
                if (doubled > 9) doubled - 9 else doubled
            } else {
                digit
            }
        }
        return sum % 10 == 0
    }

    /** 十六进制串形态(长度由调用方给定;Android ID 取十六位)。 */
    fun isHexOfLength(text: String, length: Int): Boolean =
        text.length == length && text.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }

    /** 冒号分隔的六位十六进制组(大小写均可)。 */
    fun isMacAddress(text: String): Boolean {
        val groups = text.split(':')
        if (groups.size != 6) return false
        return groups.all { it.length == 2 && isHexOfLength(it, 2) }
    }

    /**
     * 语言标识:基线 `toLocaleOrNull` 的分段规则同集
     * (两段或三段,首段二至三字母,后段可携连字符变体或四字母书写系统)。
     */
    fun isLanguageTag(text: String): Boolean {
        if (text.isBlank() || text.any { it.isWhitespace() || it == '/' }) return false
        return text.matches(LANGUAGE_TAG_PATTERN)
    }

    /** 时区标识须在系统时区库内(基线 `ZoneId.of` 失败即放弃的同口径,零 Android 依赖实现)。 */
    fun isKnownTimeZone(text: String): Boolean =
        text.isNotEmpty() && TimeZone.getAvailableIDs().any { it == text }

    private fun String.toCamelCase(): String = buildString {
        var upperNext = false
        for ((index, ch) in this@toCamelCase.withIndex()) {
            if (ch == '_') {
                upperNext = true
                continue
            }
            append(
                when {
                    index == 0 -> ch.lowercaseChar()
                    upperNext -> ch.uppercaseChar().also { upperNext = false }
                    else -> ch.lowercaseChar()
                },
            )
        }
    }

    private val LANGUAGE_TAG_PATTERN =
        Regex("^[a-z]{2,3}(?:[-_](?:[A-Za-z]{4}|[A-Z]{2}))?(?:[-_][A-Za-z0-9]{1,8})*$")
}
