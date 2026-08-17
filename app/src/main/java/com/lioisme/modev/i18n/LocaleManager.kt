package com.lioisme.modev.i18n

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.core.app.LocaleManagerCompat
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 应用语言管理(2026-08-17 V9 热切换改造,对齐 ThemeSettingsManager 范式):
 * 切换语言 = 更新 [localeFlow] + 持久化,UI 经 [localeFlow] 收集后以
 * 包装 Context 覆写 LocalContext 即时重组——**零 Activity 重建**,导航栈/
 * 搜索态/滚动位全部保留(与主题切换同体验)。
 *
 * 启动时 [init] 优先取系统 per-app locale 状态(系统面板设置过),否则回落
 * App 内 preference(App 内选择过),否则跟随系统([TAG_SYSTEM])。
 * [wrap] 出包装 Context 双路径复用:进程启动 attachBaseContext(首帧语言)
 * 与组合内热切换(remember(tag) 命中缓存,零新建、零副作用——**不在组合内
 * 写 Locale.setDefault / createConfigurationContext**,语言切换的全部副作用
 * 收敛于 [init] 与 [setLanguage])。
 * [Locale.setDefault] 同步 java.text 消费者(时间格式化/名称 Collator);
 * [TAG_SYSTEM] 恢复进程启动时系统默认,不残留旧语言。
 *
 * 线程契约:跨线程读(Application.init 写 / wrap 各线程读),[currentLanguageTag]
 * 与 [localeFlow] 以 StateFlow 保证一致性与可见性;包装 Context 缓存以
 * ConcurrentHashMap 保证并发安全(进程生命周期内按标签只建一次)。
 */
object LocaleManager {
    private const val PREF_NAME = "modev_locale"
    private const val KEY_LANG = "language"

    /** 语言标签常量(SettingsScreen / 持久化 / 比较共用,禁止散落魔法字符串)。 */
    const val TAG_SYSTEM = "system"
    const val TAG_EN = "en"
    const val TAG_ZH = "zh"

    private val _localeFlow = MutableStateFlow(TAG_SYSTEM)

    /** 内存态单一事实源:语言切换唯一驱动源,UI 收集即热更。 */
    val localeFlow: StateFlow<String> = _localeFlow.asStateFlow()

    /** 进程启动时系统默认(随系统语言),[TAG_SYSTEM] 选择时恢复,不残留旧语言。 */
    private var systemDefaultLocale: Locale = Locale.getDefault()

    /** 包装 Context 缓存(标签 → 配置级资源):切换热路径命中,零 createConfigurationContext。 */
    private val wrappedContexts = ConcurrentHashMap<String, Context>()

    val currentLanguageTag: String
        get() = _localeFlow.value

    fun init(context: Context) {
        systemDefaultLocale = Locale.getDefault()
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val prefTag = prefs.getString(KEY_LANG, TAG_SYSTEM) ?: TAG_SYSTEM
        val tag = resolveTag(prefTag, systemAppLocalesTag(context))
        _localeFlow.value = tag
        Locale.setDefault(resolveLocale(tag))
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
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return null
        return runCatching {
            val locales = LocaleManagerCompat.getApplicationLocales(context)
            if (locales.isEmpty) null
            else locales.toLanguageTags().split(',')[0].takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    /**
     * App 内语言选择(SettingsScreen 语言面板唯一入口):
     * 持久化 + [localeFlow] 更新 + [Locale.setDefault] 同步,即时生效、
     * 重启保持;**不写系统 per-app locale**(写入会触发系统重建 Activity,
     * V9 决策 A:纯热切换)。
     */
    fun setLanguage(
        context: Context,
        languageTag: String,
    ) {
        _localeFlow.value = languageTag
        Locale.setDefault(resolveLocale(languageTag))
        context
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANG, languageTag)
            .apply()
    }

    /** 标签 → Locale([TAG_SYSTEM] = 进程启动时系统默认)。 */
    private fun resolveLocale(languageTag: String): Locale =
        if (languageTag == TAG_SYSTEM) systemDefaultLocale else Locale.forLanguageTag(languageTag)

    /**
     * 配置包装(启动 attachBaseContext 与组合内热切换共用):
     * **纯函数,零副作用**——不写 Locale.setDefault(副作用收敛于
     * [init]/[setLanguage]),标签命中 [wrappedContexts] 缓存即返回,
     * 组合内 remember(tag) 切换路径不产生任何平台开销。
     */
    fun wrap(base: Context): Context {
        val tag = _localeFlow.value
        if (tag == TAG_SYSTEM) return base
        return wrappedContexts.getOrPut(tag) {
            val locale = Locale.forLanguageTag(tag)
            val config = Configuration(base.resources.configuration)
            // setLocales 自 API 24 可用(minSdk 26 = O,恒有 createConfigurationContext)。
            config.setLocales(LocaleList(locale))
            base.createConfigurationContext(config)
        }
    }
}