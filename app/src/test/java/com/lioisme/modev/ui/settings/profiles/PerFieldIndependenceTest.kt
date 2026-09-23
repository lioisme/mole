package com.lioisme.modev.ui.settings.profiles

import com.lioisme.modev.core.model.DeviceProfile
import com.lioisme.modev.core.model.ProfileField
import com.lioisme.modev.core.model.ProfileFieldCatalog
import com.lioisme.modev.core.model.ProfileFieldDeviation
import com.lioisme.modev.core.model.legalSample
import com.lioisme.modev.core.model.stringOf
import com.lioisme.modev.core.model.withText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「一项就是一项」独立性闸(V49 R-2 第一层的数据面证据)。
 *
 * 需求原文反对的是**只能在一个入口依次设置参数**:如果写 A 会顺带脏 B、或空画像下只填 A
 * 存不下去,那 41 项就仍是「一整张档」而不是 41 个可独立定制的配置。
 * 本类逐字段(41 条)钉四件事:
 *  1. 只填这一项 → 可保存;
 *  2. 快照里只有这一项非缺省(其余恒为「不干预」);
 *  3. 每应用覆写层反推出来恒是**恰一条**(幽灵偏差 = 恢复默认行永不归零的前置症状);
 *  4. 该覆写合回空画像能逐字重现第一步的快照(两条方向互逆,合并/编辑两侧不会各说各话)。
 *
 * 设备面证据(靶程序真读到)属 WP-D/WP-F,不在本类射程内。
 */
class PerFieldIndependenceTest {

    private fun textsOf(profile: DeviceProfile) =
        ProfileField.entries.associateWith { it.stringOf(profile) }

    @Test
    fun everyField_aloneIsSavableAndIsTheOnlyThingItTouches() {
        for (field in ProfileField.entries) {
            val sample = legalSample(field)
            val state = ProfileEditorState(initial = DeviceProfile.EMPTY)
            state.update(field, sample)

            assertTrue("$field 单填即应可保存", state.canSave)
            val snapshot = state.snapshot()
            assertEquals("$field 的值必须原样落进去", sample, field.stringOf(snapshot))

            val touched = ProfileField.entries.filter { it.stringOf(snapshot) != it.stringOf(DeviceProfile.EMPTY) }
            assertEquals(
                "写 $field 却脏了别项:${touched.map { it.name }}",
                listOf(field),
                touched,
            )
        }
    }

    @Test
    fun everyField_aloneProducesExactlyOnePerAppDeviationAndRoundTrips() {
        val bound = DeviceProfile.EMPTY
        for (field in ProfileField.entries) {
            val merged = field.withText(bound, legalSample(field))
            val deviations = ProfileFieldDeviation.between(bound, textsOf(merged))

            assertEquals(
                "$field 的覆写应恰一条:${deviations?.keys}",
                mapOf(ProfileFieldCatalog.jsonKeyOf(field) to legalSample(field)),
                deviations,
            )
            assertEquals(
                "apply ∘ between 必须恒等(否则屏上所见 != 下发所得)",
                merged,
                ProfileFieldDeviation.apply(bound, deviations).profile,
            )
        }
    }
}
