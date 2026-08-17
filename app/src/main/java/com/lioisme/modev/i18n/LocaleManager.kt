package com.lioisme.modev.i18n

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.core.app.LocaleManagerCompat
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * 应用语言管理(2026-08-15 UI 重构 Phase 5/6,移植自 LIDE LocaleManager)。
 *
 * 双路径(单一事实源均为 [currentLanguageTag],与系统 per-app locale 状态双向同步):
 * - **API 33+(Android 13)**:经平台 `android.app.LocaleManager` 的
 *   `setApplicationLocales`(core 的 [LocaleManagerCompat] 仅提供读取
 *   `getApplicationLocales`)写入系统 per-app locale——系统设置「语言偏好」
 *   面板与 App 内选择双向生效、框架自动重建 Activity(不手动 recreate);
 * - **API < 33**:wrap 注入 Configuration([wrap],`createConfigurationContext`
 *   自 API 26 起,24-25 回落 updateConfiguration)+ 调用方 `recreate()`。
 *
 * 启动时 [init] 优先取系统 per-app locale 状态(系统面板设置过),否则回落
 * App 内 preference(App 内选择过),否则跟随系统([TAG_SYSTEM])。
 *
 * 线程契约:跨线程读(Application.init 写 / wrap 各线程读),[currentLanguageTag]
 * 以 [Volatile] 保证可见性。
 */
object LocaleManager {
    private const val PREF_NAME = "modev_locale"
    private const val KEY_LANG = "language"

    /** 语言标签常量(SettingsScreen / 持久化 / 比较共用,禁止散落魔法字符串)。 */
    const val TAG_SYSTEM = "system"
    const val TAG_EN = "en"
    const val TAG_ZH = "zh"

    @Volatile
    var currentLanguageTag: String = TAG_SYSTEM
        private set

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val prefTag = prefs.getString(KEY_LANG, TAG_SYSTEM) ?: TAG_SYSTEM
        currentLanguageTag = resolveTag(prefTag, systemAppLocalesTag(context))
    }

    /**
     * 语言标签解析(纯函数):系统 per-app locale 状态优先(用户经系统面板
     * 设置过),否则 preference(App 内设置过),否则跟随系统。
     * 系统状态为空列表 = 未设置(跟随系统)。
     */
    internal fun resolveTag(
        prefTag: String,
        systemTag: String?,
    ): String = systemTag ?: prefTag

    /** 读取系统 per-app locale 状态的首个语言标签(API < 33 或未设置为 null)。 */
    private fun systemAppLocalesTag(context: Context): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        val locales = LocaleManagerCompat.getApplicationLocales(context)
        if (locales.isEmpty) return null
        val tags = locales.toLanguageTags()
        return tags.split(',')[0].takeIf { it.isNotBlank() }
    }

    /**
     * App 内语言选择(SettingsScreen 语言面板唯一入口):
     * - API 33+:写入系统 per-app locale(框架自动重建 Activity);
     * - API < 33:仅持久化,由调用方执行 recreate()(wrap 生效)。
     * 两种路径均即时持久化,重启保持。
     */
    fun setLanguage(
        context: Context,
        languageTag: String,
    ) {
        currentLanguageTag = languageTag
        context
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANG, languageTag)
            .apply()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // 空列表 = 清空 per-app locale(跟随系统)。
            val locales =
                if (languageTag == TAG_SYSTEM) {
                    LocaleListCompat.getEmptyLocaleList()
                } else {
                    LocaleListCompat.forLanguageTags(languageTag)
                }
            // core 的 LocaleManagerCompat 无写入 API(仅 getApplicationLocales),
            // 写入直调平台 LocaleManager(API 33+ 经 SDK 守卫)。
            val manager = context.getSystemService(LocaleManager::class.java) ?: return
            @Suppress("UNCHECKED_CAST")
            manager.setApplicationLocales(locales.unwrap() as LocaleList)
        }
    }

    /**
     * 配置包装(API < 33 路径;33+ 由系统 per-app locale 提供资源,本方法
     * 仍作为显式防御——按 currentLanguageTag 出资源,成本一次 createConfigurationContext)。
     */
    fun wrap(base: Context): Context {
        val tag = currentLanguageTag
        if (tag == TAG_SYSTEM) return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        // setLocales 自 API 24 可用(minSdk 26 = O,恒有 createConfigurationContext)。
        config.setLocales(LocaleList(locale))
        return base.createConfigurationContext(config)
    }
}