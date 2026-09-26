package com.lioisme.modev.i18n

import android.content.Context
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 定向测试(LocaleManager,2026-08-17 V9 语言热切换;2026-08-18 V15 解析修正):
 * resolveTag 优先级(App 内显式选择权威 / 跟随系统时查 per-app locale)/
 * setLanguage 更新 flow + 持久化(重启恢复)/ SYSTEM 恢复系统默认 /
 * refreshForRecreation 重建路径重解析 / wrap 出指定语言资源 / SYSTEM 恒返回 base。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocaleManagerTest {

    private lateinit var app: Context
    private lateinit var originalDefault: Locale

    @Before
    fun setUp() {
        app = RuntimeEnvironment.getApplication()
        originalDefault = Locale.getDefault()
        app.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        LocaleManager.init(app)
    }

    @After
    fun tearDown() {
        Locale.setDefault(originalDefault)
        app.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun resolveTag_explicitPref_isAuthoritative_overSystemState() {
        // V15:App 内显式选择权威,系统 per-app locale 不再覆盖
        // (修复「App 内选英文,重启后回退系统中文」)
        assertEquals(
            LocaleManager.TAG_ZH,
            LocaleManager.resolveTag(prefTag = LocaleManager.TAG_ZH, systemTag = LocaleManager.TAG_EN),
        )
        assertEquals(
            LocaleManager.TAG_EN,
            LocaleManager.resolveTag(prefTag = LocaleManager.TAG_EN, systemTag = null),
        )
    }

    @Test
    fun resolveTag_followSystem_consultsSystemState() {
        // 仅「跟随系统」时采用系统 per-app locale,否则跟随系统默认
        assertEquals(
            LocaleManager.TAG_EN,
            LocaleManager.resolveTag(prefTag = LocaleManager.TAG_SYSTEM, systemTag = LocaleManager.TAG_EN),
        )
        assertEquals(
            LocaleManager.TAG_SYSTEM,
            LocaleManager.resolveTag(prefTag = LocaleManager.TAG_SYSTEM, systemTag = null),
        )
    }

    @Test
    fun setLanguage_updatesFlowAndPersists_acrossReinit() {
        LocaleManager.setLanguage(app, LocaleManager.TAG_ZH)

        assertEquals(LocaleManager.TAG_ZH, LocaleManager.localeFlow.value)
        assertEquals(LocaleManager.TAG_ZH, LocaleManager.currentLanguageTag)
        assertEquals(
            LocaleManager.TAG_ZH,
            app.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getString(KEY_LANG, null),
        )
        assertEquals(Locale.forLanguageTag("zh"), Locale.getDefault())

        // 重启语义:init 从持久化恢复(显式选择不再被系统状态覆盖)
        LocaleManager.init(app)
        assertEquals(LocaleManager.TAG_ZH, LocaleManager.localeFlow.value)
    }

    @Test
    fun setLanguage_system_restoresSystemDefaultLocale() {
        val systemDefault = Locale.getDefault()

        LocaleManager.setLanguage(app, LocaleManager.TAG_ZH)
        assertEquals(Locale.forLanguageTag("zh"), Locale.getDefault())

        LocaleManager.setLanguage(app, LocaleManager.TAG_SYSTEM)
        assertEquals(systemDefault, Locale.getDefault())
        assertEquals(LocaleManager.TAG_SYSTEM, LocaleManager.localeFlow.value)
    }

    @Test
    fun refreshForRecreation_keepsExplicitPref() {
        // 重建路径:显式 App 内选择不受系统变更影响(不产生「变回去」)
        LocaleManager.setLanguage(app, LocaleManager.TAG_ZH)
        LocaleManager.refreshForRecreation(app)
        assertEquals(LocaleManager.TAG_ZH, LocaleManager.localeFlow.value)
        assertEquals(Locale.forLanguageTag("zh"), Locale.getDefault())
    }

    @Test
    fun refreshForRecreation_system_refreshesSystemDefault() {
        LocaleManager.setLanguage(app, LocaleManager.TAG_SYSTEM)

        // 模拟系统语言变更为中文(设备级配置更新)
        RuntimeEnvironment.setQualifiers("zh-rCN")
        LocaleManager.refreshForRecreation(app)
        assertEquals(LocaleManager.TAG_SYSTEM, LocaleManager.localeFlow.value)
        // 系统默认语言已刷新:恢复「跟随系统」落到当前系统语言,而非进程启动旧值
        LocaleManager.setLanguage(app, LocaleManager.TAG_SYSTEM)
        assertEquals("zh", Locale.getDefault().language)
    }

    @Test
    fun wrap_appliesTaggedLocaleResources() {
        LocaleManager.setLanguage(app, LocaleManager.TAG_ZH)

        val wrapped = LocaleManager.wrap(app)
        assertEquals("zh", wrapped.resources.configuration.locales[0]?.language)
    }

    @Test
    fun wrap_systemReturnsBaseUnchanged() {
        LocaleManager.setLanguage(app, LocaleManager.TAG_SYSTEM)
        assertSame(app, LocaleManager.wrap(app))
    }

    private companion object {
        const val PREF_NAME = "modev_locale"
        const val KEY_LANG = "language"
    }
}
