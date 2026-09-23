package com.lioisme.modev.ui.settings.profiles

import com.lioisme.modev.R
import com.lioisme.modev.core.model.BuiltinDeviceProfiles
import com.lioisme.modev.core.model.ProfileConflict
import com.lioisme.modev.core.model.ProfileField
import com.lioisme.modev.core.model.ProfileFieldCatalog
import com.lioisme.modev.core.model.ProfileFieldError
import com.lioisme.modev.core.model.ProfileKind
import com.lioisme.modev.core.model.ProfileValueType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 画像标签表门禁(V47 WP-5,与 [com.lioisme.modev.ui.apps.KindLabelResExhaustiveTest] 同族):
 * 三张表(类别 = 分区 / 参数 / 校验结论)必须**逐项有自有标签**且不落兜底文案。
 *
 * 「一参数一行」的可视配置页依赖这一点:任何一个新参数若忘了映射标签,
 * 屏上就会出现两行同名或一行「未知」——那是配置面最贵的静默失效。
 *
 * V49 R-1 起分区标签就是类别标签(一条轴一套名字,`profile_section_*` 十条已退役),
 * 只剩 `profile_section_title` 一条**格式串**承担「本地化名 + 来源 API」的组装。
 */
class ProfileLabelsTest {

    @Test
    fun everyInterventionGroupHasItsOwnLabel() {
        val resolved = ProfileKind.entries.map(::profileKindLabelRes)
        assertEquals(
            "分区即类别 ⇒ 20 个类别必须 20 个互异标签(重复 = 屏上两段同名)",
            ProfileKind.entries.size,
            resolved.distinct().size,
        )
        assertFalse(R.string.unknown_error in resolved)
    }

    @Test
    fun everyProfileFieldHasItsOwnLabel() {
        val resolved = ProfileField.entries.map(::profileFieldLabelRes)
        assertEquals(
            "一行一参数的前提是标签互不相同(碰撞 = 屏上两行同名)",
            ProfileField.entries.size,
            resolved.distinct().size,
        )
        assertFalse(R.string.unknown_error in resolved)
    }

    @Test
    fun everyFieldAndKindPairingIsCoherent() {
        // 字段所属类别必须有标签(类别表按字段遍历同样穷尽)。
        ProfileField.entries.forEach { field ->
            assertTrue(R.string.unknown_error != profileFieldLabelRes(field))
            assertTrue(R.string.unknown_error != profileKindLabelRes(field.kind))
        }
    }

    @Test
    fun everyValidationErrorHasItsOwnMessage() {
        val resolved = ProfileFieldError.entries
            .map(::profileFieldErrorRes)
        assertEquals(
            ProfileFieldError.entries.size,
            resolved.distinct().size,
        )
        assertFalse(R.string.unknown_error in resolved)
    }

    @Test
    fun everyConflictKindHasItsOwnMessage() {
        val resolved = ProfileConflict.entries.map(::profileConflictLabelRes)
        assertEquals(ProfileConflict.entries.size, resolved.distinct().size)
        assertFalse(R.string.unknown_error in resolved)
    }

    /**
     * 枚举槽标签必须覆盖目录反推出的**每一个**合法槽位。
     *
     * 未覆盖时 UI 回落裸数字(不崩),但那意味着新槽位在屏上变成一行天书 ——
     * 故此处硬闸:目录加槽位而表没跟上,本测试即红。
     */
    @Test
    fun slotLabels_coverEveryCatalogDerivedSlot() {
        enumFields().forEach { field ->
            val slots = (-10..40).filter { ProfileFieldCatalog.validate(field, "$it") == null }
            assertTrue("${field.name} 应有合法槽位", slots.isNotEmpty())
            slots.forEach { slot ->
                assertTrue(
                    "${field.name} 槽位 $slot 缺标签(UI 会回落裸数字)",
                    profileSlotLabelRes(field, slot) != 0,
                )
            }
        }
    }

    @Test
    fun nonSlotFieldsHaveNoSlotLabel() {
        ProfileField.entries.filter { it !in enumFields() }.forEach { field ->
            (-10..40).forEach { slot -> assertEquals(0, profileSlotLabelRes(field, slot)) }
        }
    }

    /** 未知槽位一律 0(= 由调用方回落裸数字),禁静默复用别的槽标签。 */
    @Test
    fun unknownSlotsFallBackToNoLabel() {
        assertEquals(0, profileSlotLabelRes(ProfileField.NETWORK_TYPE, 99))
        assertEquals(0, profileSlotLabelRes(ProfileField.SCREENSHOTS_FLAG, -7))
    }

    @Test
    fun builtinSeedIdsResolveToRealNames() {
        BuiltinDeviceProfiles.BUILTINS.forEach { spec ->
            assertTrue(
                "内置档 ${spec.seedId} 缺展示名",
                builtinProfileNameRes(spec.seedId) != R.string.unknown_error,
            )
        }
        assertEquals(R.string.unknown_error, builtinProfileNameRes("no-such-seed"))
    }

    @Test
    fun builtinNamesAreDistinctFromTheLegacyConstantTable() {
        // 三档内置画像各自一枚资源键(与旧 SpoofProfiles 三常量档无共用行,防两义)。
        val names = BuiltinDeviceProfiles.BUILTINS.map { builtinProfileNameRes(it.seedId) }
        assertEquals(names.size, names.distinct().size)
    }

    private fun enumFields(): List<ProfileField> =
        ProfileField.entries.filter {
            it.type == ProfileValueType.NETWORK_TYPE || it.type == ProfileValueType.SCREENSHOT_MODE
        }
}
