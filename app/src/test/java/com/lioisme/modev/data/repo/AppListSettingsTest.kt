package com.lioisme.modev.data.repo

import android.content.Context
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * AppListSettings 偏好契约(V14 R7/R8):
 * - 默认值:pinScoped=true(置顶默认开)/ onlyScoped=false / showSystem=true;
 * - set → 内存 StateFlow 即时更新(排序流水线消费);
 * - set → SharedPreferences 落盘 → init 重载回读一致(杀进程重启等价)。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppListSettingsTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        // 清空 pref,避免跨用例污染
        context.getSharedPreferences("modev_applist", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @After
    fun tearDown() {
        context.getSharedPreferences("modev_applist", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun `defaults pinScoped true onlyScoped false showSystem true`() {
        AppListSettings.init(context)
        assertTrue(AppListSettings.pinScoped.value)
        assertFalse(AppListSettings.onlyScoped.value)
        assertTrue(AppListSettings.showSystem.value)
        // V17 R1:模块默认隐藏
        assertFalse(AppListSettings.showModules.value)
    }

    @Test
    fun `set pinScoped persists and reloads`() {
        AppListSettings.init(context)
        AppListSettings.setPinScoped(false, context)
        assertFalse(AppListSettings.pinScoped.value)
        // 进程重启等价:重新 init 从 SharedPreferences 回读
        AppListSettings.init(context)
        assertFalse(AppListSettings.pinScoped.value)
        // 还原默认
        AppListSettings.setPinScoped(true, context)
        AppListSettings.init(context)
        assertTrue(AppListSettings.pinScoped.value)
    }

    @Test
    fun `set onlyScoped and showSystem persist and reload`() {
        AppListSettings.init(context)
        AppListSettings.setOnlyScoped(true, context)
        AppListSettings.setShowSystem(false, context)
        assertTrue(AppListSettings.onlyScoped.value)
        assertFalse(AppListSettings.showSystem.value)
        AppListSettings.init(context)
        assertTrue(AppListSettings.onlyScoped.value)
        assertFalse(AppListSettings.showSystem.value)
        // 还原默认
        AppListSettings.setOnlyScoped(false, context)
        AppListSettings.setShowSystem(true, context)
        AppListSettings.init(context)
        assertEquals(false, AppListSettings.onlyScoped.value)
        assertEquals(true, AppListSettings.showSystem.value)
    }

    @Test
    fun `set showModules persists and reloads`() {
        AppListSettings.init(context)
        assertFalse(AppListSettings.showModules.value)
        AppListSettings.setShowModules(true, context)
        assertTrue(AppListSettings.showModules.value)
        // 进程重启等价:重新 init 从 SharedPreferences 回读
        AppListSettings.init(context)
        assertTrue(AppListSettings.showModules.value)
        // 还原默认
        AppListSettings.setShowModules(false, context)
        AppListSettings.init(context)
        assertFalse(AppListSettings.showModules.value)
    }
}