package com.lioisme.modev.core.model

/**
 * SharedPreferences 监听操作:具体 API 动作,[kind] 归属到类别。
 * kind = 用户开关粒度([HookSwitch.prefsEnabled] 门控),
 * operation = 列表元行/详情页展示粒度。
 */
enum class PrefsOperation(
    val kind: PrefsKind,
) {
    /** 配置文件载入完成(SharedPreferencesImpl 构造;路径/mode 入 path/params)。 */
    OPEN(PrefsKind.OPEN),

    /** getString。 */
    GET_STRING(PrefsKind.GET),

    /** getInt。 */
    GET_INT(PrefsKind.GET),

    /** getLong。 */
    GET_LONG(PrefsKind.GET),

    /** getFloat。 */
    GET_FLOAT(PrefsKind.GET),

    /** getBoolean。 */
    GET_BOOLEAN(PrefsKind.GET),

    /** getStringSet(集合值序列化为 JSON 数组)。 */
    GET_STRING_SET(PrefsKind.GET),

    /** getAll(全量快照,值域序列化受 maxBytesPerField 钳制)。 */
    GET_ALL(PrefsKind.GET),

    /** contains(仅记录存在性,不产值)。 */
    CONTAINS(PrefsKind.GET),

    /** putString。 */
    PUT_STRING(PrefsKind.PUT),

    /** putInt。 */
    PUT_INT(PrefsKind.PUT),

    /** putLong。 */
    PUT_LONG(PrefsKind.PUT),

    /** putFloat。 */
    PUT_FLOAT(PrefsKind.PUT),

    /** putBoolean。 */
    PUT_BOOLEAN(PrefsKind.PUT),

    /** putStringSet。 */
    PUT_STRING_SET(PrefsKind.PUT),

    /** 单键删除(editor.remove)。 */
    REMOVE(PrefsKind.REMOVE),

    /** 全量清空(editor.clear)。 */
    CLEAR(PrefsKind.CLEAR),
}
