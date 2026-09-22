package com.lioisme.modev.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V47 T3 画像计划编译器:最小安装面、互斥裁决、矛盾自检。
 *
 * 这里最重要的一条是「空画像 = 零组」的冻结断言:它是「开关全关即零副作用」
 * 这条 FeatureGate 红线在画像域的等价物 —— 一旦有人把某组改成无条件装配,
 * 本测试立即红,而不是等到目标应用出现无法归因的行为差异。
 */
class ProfileHookPlanTest {

    private fun build(vararg pairs: Pair<ProfileField, String>): DeviceProfile =
        pairs.fold(DeviceProfile.EMPTY) { acc, (field, value) -> field.withText(acc, value) }

    @Test
    fun `empty profile compiles to zero groups`() {
        assertEquals(emptyList<ProfileKind>(), ProfileHookPlan.activeGroups(DeviceProfile.EMPTY))
        assertEquals(0, DeviceProfile.EMPTY.filledCount)
        assertFalse(ProfileHookPlan.adaptiveMaskShouldYield(DeviceProfile.EMPTY))
    }

    @Test
    fun `each group activates from any one of its fields`() {
        for (kind in ProfileKind.entries) {
            val field = ProfileFieldCatalog.byKind.getValue(kind).first()
            val sample = build(field to sampleFor(field))
            val active = ProfileHookPlan.activeGroups(sample)
            if (kind in MASK_OVERRIDE_KINDS) {
                // 两类「隐藏」开关会压制同族伪装组,单字段激活断言只看自身是否在列。
                assertTrue("$kind 未激活:$active", kind in active)
            } else {
                assertEquals("单字段 ${field.name} 应恰好激活一个组", listOf(kind), active)
            }
        }
    }

    @Test
    fun `hide wi-fi location suppresses wifi identity spoofing`() {
        val sample = build(
            ProfileField.WIFI_SSID to "CafeNet",
            ProfileField.WIFI_BSSID to "02:00:00:00:00:02",
            ProfileField.WIFI_LOCATION_HIDE to "true",
        )
        val active = ProfileHookPlan.activeGroups(sample)
        assertTrue(active.contains(ProfileKind.PROFILE_WIFI_LOCATION_HIDE))
        assertFalse("伪装与隐藏同时在场 = 同一批 API 被两套实现写", ProfileKind.PROFILE_WIFI in active)
    }

    @Test
    fun `hide cell location suppresses cell parameter spoofing`() {
        val sample = build(
            ProfileField.LAC to "45",
            ProfileField.CID to "123",
            ProfileField.CELL_LOCATION_HIDE to "true",
        )
        val active = ProfileHookPlan.activeGroups(sample)
        assertTrue(active.contains(ProfileKind.PROFILE_CELL_LOCATION_HIDE))
        assertFalse(ProfileKind.PROFILE_CELL in active)
    }

    @Test
    fun `hide switch alone still activates without any spoof field`() {
        val sample = build(ProfileField.WIFI_LOCATION_HIDE to "true")
        assertEquals(listOf(ProfileKind.PROFILE_WIFI_LOCATION_HIDE), ProfileHookPlan.activeGroups(sample))
    }

    @Test
    fun `explicit build identity makes the adaptive conditional mask yield`() {
        // 只填非 Build 族字段 ⇒ 条件档继续负责画像留空的部分。
        assertFalse(ProfileHookPlan.adaptiveMaskShouldYield(build(ProfileField.WIFI_SSID to "Net")))
        assertFalse(ProfileHookPlan.adaptiveMaskShouldYield(DeviceProfile.EMPTY))
        // 任一 Build 族字段被显式改写 ⇒ 强制档在场,条件档让位。
        assertTrue(ProfileHookPlan.adaptiveMaskShouldYield(build(ProfileField.BRAND to "google")))
        assertTrue(ProfileHookPlan.adaptiveMaskShouldYield(build(ProfileField.SDK_INT to "34")))
    }

    @Test
    fun `half-set coordinates are reported as a conflict`() {
        assertEquals(
            listOf(ProfileConflict.COORDINATE_HALF_SET),
            ProfileHookPlan.conflicts(build(ProfileField.LONGITUDE to "13.4")),
        )
        assertTrue(ProfileHookPlan.conflicts(build(ProfileField.LONGITUDE to "13.4", ProfileField.LATITUDE to "52.5")).isEmpty())
    }

    @Test
    fun `offset without coordinates is reported`() {
        assertEquals(
            listOf(ProfileConflict.OFFSET_WITHOUT_COORDINATES),
            ProfileHookPlan.conflicts(build(ProfileField.RANDOM_OFFSET to "true")),
        )
        assertTrue(
            ProfileHookPlan.conflicts(
                build(ProfileField.RANDOM_OFFSET to "true", ProfileField.LATITUDE to "52.5", ProfileField.LONGITUDE to "13.4"),
            ).isEmpty(),
        )
    }

    @Test
    fun `app list pass without any content pass is flagged as likely unintended`() {
        assertEquals(
            listOf(ProfileConflict.APP_LIST_WITHOUT_CONTENT),
            ProfileHookPlan.conflicts(build(ProfileField.PASS_APPLICATIONS to "true")),
        )
        assertTrue(
            ProfileHookPlan.conflicts(
                build(ProfileField.PASS_APPLICATIONS to "true", ProfileField.PASS_CONTACTS to "true"),
            ).isEmpty(),
        )
    }

    @Test
    fun `groups are emitted in enum declaration order so consumers stay stable`() {
        val sample = build(
            ProfileField.PASS_CONTACTS to "true",
            ProfileField.BRAND to "google",
            ProfileField.LAC to "45",
        )
        val active = ProfileHookPlan.activeGroups(sample)
        assertEquals(active.sortedBy { it.ordinal }, active)
    }

    /** 给该字段一个**必然通过校验且必然生效**的样本值。 */
    private fun sampleFor(field: ProfileField): String = when (field.type) {
        ProfileValueType.BOOL -> "true"
        ProfileValueType.INT -> "1"
        ProfileValueType.DOUBLE -> "13.4"
        ProfileValueType.NETWORK_TYPE -> "1"
        ProfileValueType.SCREENSHOT_MODE -> "1"
        ProfileValueType.TEXT -> when (field) {
            ProfileField.SIM_OPERATOR -> "46000"
            ProfileField.SIM_COUNTRY -> "CN"
            ProfileField.IMEI -> "490171581696172"
            ProfileField.ANDROID_ID -> "a1b2c3d4e5f60718"
            ProfileField.WIFI_BSSID, ProfileField.WIFI_MAC_ADDRESS -> "02:00:00:00:00:02"
            ProfileField.LANGUAGE -> "zh_CN"
            ProfileField.TIME_ZONE -> "Asia/Shanghai"
            ProfileField.FINGERPRINT -> "google/raven/raven:14/UP1A.231105.001/10754064:user/release-keys"
            else -> "sample"
        }
    }

    private companion object {
        /** 会压制同族伪装组的两个「隐藏」开关。 */
        val MASK_OVERRIDE_KINDS = setOf(
            ProfileKind.PROFILE_WIFI_LOCATION_HIDE,
            ProfileKind.PROFILE_CELL_LOCATION_HIDE,
        )
    }
}
