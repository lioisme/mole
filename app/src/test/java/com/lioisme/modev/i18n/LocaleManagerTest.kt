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
 * 瀹氬悜娴嬭瘯(LocaleManager,2026-08-17 V9 璇█鐑垏鎹?:
 * resolveTag 涓夋簮浼樺厛绾?/ setLanguage 鏇存柊 flow + 鎸佷箙鍖?閲嶅惎鎭㈠)/
 * SYSTEM 鎭㈠杩涚▼鍚姩绯荤粺榛樿 / wrap 鍑烘寚瀹氳瑷€璧勬簮 / SYSTEM 鎭掕繑鍘?context銆? */
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
    fun resolveTag_systemStateWins_overPref() {
        assertEquals(
            LocaleManager.TAG_EN,
            LocaleManager.resolveTag(prefTag = LocaleManager.TAG_ZH, systemTag = LocaleManager.TAG_EN),
        )
        assertEquals(
            LocaleManager.TAG_ZH,
            LocaleManager.resolveTag(prefTag = LocaleManager.TAG_ZH, systemTag = null),
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

        // 閲嶅惎璇箟:init 浠庢寔涔呭寲鎭㈠(绯荤粺 per-app locale 鏈缃?銆?        LocaleManager.init(app)
        assertEquals(LocaleManager.TAG_ZH, LocaleManager.localeFlow.value)
    }

    @Test
    fun setLanguage_system_restoresProcessDefaultLocale() {
        val systemDefault = Locale.getDefault()

        LocaleManager.setLanguage(app, LocaleManager.TAG_ZH)
        assertEquals(Locale.forLanguageTag("zh"), Locale.getDefault())

        LocaleManager.setLanguage(app, LocaleManager.TAG_SYSTEM)
        assertEquals(systemDefault, Locale.getDefault())
        assertEquals(LocaleManager.TAG_SYSTEM, LocaleManager.localeFlow.value)
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
