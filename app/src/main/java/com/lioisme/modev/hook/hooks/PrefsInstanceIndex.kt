package com.lioisme.modev.hook.hooks

/**
 * SharedPreferences 实例索引:
 * OPEN 构造器时刻登记 impl→路径、edit 时刻登记 editor→路径,
 * 后续 get/put 事件经接收者实例反查配置文件路径。
 *
 * 被动红线:仅以 `System.identityHashCode` 键控的有界 LRU 承载,
 * 绝不反射读取 SharedPreferencesImpl 内部字段(mFile 等灰名单禁触);
 * 容量逐出后路径尽力回填为 null(记录仍有效,仅缺路径语境)。
 */
class PrefsInstanceIndex(private val capacity: Int = DEFAULT_CAPACITY) {

    /** impl 实例 → 配置文件绝对路径。 */
    private val implToPath = lru()

    /** editor 实例 → 配置文件绝对路径(edit 时由 impl 解析固化)。 */
    private val editorToPath = lru()

    fun rememberImpl(impl: Any?, path: String) {
        if (impl == null || path.isEmpty()) return
        put(implToPath, impl, path)
    }

    fun rememberEditor(editor: Any?, impl: Any?) {
        if (editor == null || impl == null) return
        pathOf(impl)?.let { put(editorToPath, editor, it) }
    }

    fun pathOf(impl: Any?): String? = impl?.let { get(implToPath, it) }

    fun pathOfEditor(editor: Any?): String? = editor?.let { get(editorToPath, it) }

    /** access-order LinkedHashMap(capacity 上限,同步访问,镜像 SessionThrottle 模式)。 */
    private fun lru(): MutableMap<Any, String> =
        object : LinkedHashMap<Any, String>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Any, String>): Boolean =
                size > capacity
        }

    @Synchronized
    private fun put(map: MutableMap<Any, String>, key: Any, value: String) {
        map[key] = value
    }

    @Synchronized
    private fun get(map: MutableMap<Any, String>, key: Any): String? = map[key]

    companion object {
        /** 默认容量(与 SessionThrottle 同量级;进程内活跃 prefs 文件远低于此)。 */
        const val DEFAULT_CAPACITY = 256
    }
}
