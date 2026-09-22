package com.lioisme.modev.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 画像标准取值集回归(V48 R-3):钉的是三张表**自身的一致性**,以及
 * release↔SDK 这一对**必须成对可查**的事实 —— 选 Android 14 却写 API 31 的画像,
 * 靶程序读到的是一台从不存在的设备,那本身就是一枚检测点。
 */
class ProfileValueSetsTest {

    @Test
    fun releaseAndSdkColumnsAgreeAndAdvanceMonotonically() {
        val releases = ProfileValueSets.androidReleases()
        val sdks = ProfileValueSets.sdkLevels().map { it.toInt() }
        assertEquals("两列必须等长(同一张表)", releases.size, sdks.size)
        assertEquals("release 不可重复", releases.size, releases.distinct().size)
        assertEquals("SDK 不可重复", sdks.size, sdks.distinct().size)
        sdks.zipWithNext { lower, higher ->
            assertTrue("API 必须递增:$lower → $higher", higher > lower)
        }
    }

    /** 每个 release 都要能查回它自己的 SDK(成对写唯一依赖这一步)。 */
    @Test
    fun everyReleaseResolvesToItsOwnSdk() {
        ProfileValueSets.androidReleases().forEachIndexed { index, release ->
            assertEquals(
                "$release 反查到的 SDK 与表不同行",
                ProfileValueSets.sdkLevels()[index],
                ProfileValueSets.sdkForRelease(release),
            )
        }
        assertNull("认不出的 release 不得猜一个 SDK", ProfileValueSets.sdkForRelease("99"))
    }

    /** 不预告不存在的系统版本:表尾必须停在机能理解的最高 API。 */
    @Test
    fun tableEndsAtTheHighestKnownApi() {
        assertEquals("36", ProfileValueSets.sdkLevels().last())
        assertEquals("16", ProfileValueSets.androidReleases().last())
    }

    @Test
    fun releaseLabelShowsTheApiAlongsideTheMarketName() {
        assertEquals("14 (API 34)", ProfileValueSets.releaseLabel("14"))
        assertEquals("unknown", ProfileValueSets.releaseLabel("unknown"))
    }

    /** 铁律:三张表的每个候选值都必须过自己字段的校验器。 */
    @Test
    fun everyCandidatePassesFieldValidation() {
        val byField = mapOf(
            ProfileField.ANDROID_VERSION to ProfileValueSets.androidReleases(),
            ProfileField.SDK_INT to ProfileValueSets.sdkLevels(),
            ProfileField.DENSITY_DPI to ProfileValueSets.densities(),
            ProfileField.CPU_CORES to ProfileValueSets.cpuCores(),
        )
        byField.forEach { (field, values) ->
            assertTrue("$field 表为空", values.isNotEmpty())
            values.forEach { value ->
                assertNull(
                    "$field 拒绝了自己的候选值 $value",
                    ProfileFieldCatalog.validate(field, value),
                )
            }
        }
    }

    @Test
    fun numericTablesAreAscendingByValue() {
        listOf(ProfileValueSets.densities(), ProfileValueSets.cpuCores()).forEach { table ->
            val numbers = table.map { it.toInt() }
            assertEquals("必须按数值升序(选择器要能扫)", numbers.sorted(), numbers)
        }
    }
}
