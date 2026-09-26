package com.lioisme.modev.ui.common

import android.content.res.Configuration
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * effectiveLocale 契约(V16 R5 语言切换修复):
 * 以包过 Context 的 resources.configuration 为单一事实源(热切换下
 * LocalConfiguration 陈旧,包过 Context 才反映 App 内显式语言 / per-app locale);
 * 配置无 locale 时回退 Locale.getDefault。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EffectiveLocaleTest {

    @Test
    fun readsLocaleFromWrappedConfiguration() {
        val base = RuntimeEnvironment.getApplication()
        val wrapped = base.createConfigurationContext(
            Configuration().apply { setLocale(Locale.CHINESE) },
        )
        assertEquals(Locale.CHINESE, wrapped.effectiveLocale())
    }

    @Test
    fun readsLocaleFromLocaleListConfiguration() {
        val base = RuntimeEnvironment.getApplication()
        val wrapped = base.createConfigurationContext(
            Configuration().apply {
                setLocales(android.os.LocaleList(Locale.US, Locale.CHINESE))
            },
        )
        // locales[0] = 首选语言(与 LocaleManager.wrap 的 setLocales 同路径)
        assertEquals(Locale.US, wrapped.effectiveLocale())
    }

    @Test
    fun fallsBackToDefault_whenConfigurationHasNoLocale() {
        val base = RuntimeEnvironment.getApplication()
        val plain = base.createConfigurationContext(Configuration())
        val expected = plain.resources.configuration.locales[0] ?: Locale.getDefault()
        assertEquals(expected, plain.effectiveLocale())
    }
}
