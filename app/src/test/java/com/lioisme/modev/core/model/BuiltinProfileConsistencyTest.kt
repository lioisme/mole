package com.lioisme.modev.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V47 T2 内置三档一致性门禁(R-2 的「经典画像」必须是**可信且自洽**的一组值)。
 *
 * 动因是现网实锤过的风险:品牌与机型跨厂混搭(如 MANUFACTURER=Xiaomi 配 MODEL=Pixel 7 Pro)
 * 会被高级反篡改直接冻结,而「画像内部矛盾」正是 DIY 与内置档共同的最高频错误。
 * 本类把「fingerprint 与三元组/版本/Build ID 互推一致」从口头要求变成断言。
 *
 * 口径要诚实:本门禁锁的是**画像内部自洽**与**取值合法**,
 * 不承诺字面量与某台实物设备逐位相同(那需要真机采集,改值只需动 BUILTINS)。
 */
class BuiltinProfileConsistencyTest {

    private val identityFields: Set<ProfileField> = setOf(
        ProfileField.BRAND,
        ProfileField.MANUFACTURER,
        ProfileField.MODEL,
        ProfileField.PRODUCT,
        ProfileField.DEVICE,
        ProfileField.BOARD,
        ProfileField.HARDWARE,
        ProfileField.BUILD_ID,
        ProfileField.FINGERPRINT,
        ProfileField.ANDROID_VERSION,
        ProfileField.SDK_INT,
        ProfileField.DENSITY_DPI,
        ProfileField.CPU_CORES,
    )

    @Test
    fun `catalog ships exactly three distinct classic devices`() {
        assertEquals(3, BuiltinDeviceProfiles.BUILTINS.size)
        assertEquals(
            BuiltinDeviceProfiles.BUILTINS.size,
            BuiltinDeviceProfiles.BUILTINS.map { it.seedId }.toSet().size,
        )
        assertEquals(
            "三档指纹必须互不相同(否则收养逻辑会把两档认成一档)",
            BuiltinDeviceProfiles.BUILTINS.size,
            BuiltinDeviceProfiles.BUILTINS.map { ProfileCodec.fingerprint(it.profile) }.toSet().size,
        )
        assertEquals(
            BuiltinDeviceProfiles.BUILTINS.size,
            BuiltinDeviceProfiles.BUILTINS.map { it.profile.brand }.toSet().size,
        )
    }

    @Test
    fun `every builtin fills exactly the identity fields and nothing else`() {
        for (spec in BuiltinDeviceProfiles.BUILTINS) {
            val filled = ProfileField.entries.filter { it.isSetIn(spec.profile) }.toSet()
            assertEquals(
                "${spec.seedId} 内置档只应填机型身份 13 项(标识符/SIM/定位/隐私属使用现场," +
                    "写进机型档会连带改掉用户真实环境,且固定 IMEI 会成跨应用关联指纹)",
                identityFields,
                filled,
            )
        }
    }

    @Test
    fun `every builtin value passes catalog validation`() {
        for (spec in BuiltinDeviceProfiles.BUILTINS) {
            assertNull("${spec.seedId} 含非法取值:${ProfileCodec.firstInvalid(spec.profile)}", ProfileCodec.firstInvalid(spec.profile))
        }
    }

    @Test
    fun `fingerprint is derived-consistent with the identity triple and version`() {
        for (spec in BuiltinDeviceProfiles.BUILTINS) {
            val p = spec.profile
            val head = "${p.brand}/${p.product}/${p.device}:"
            assertTrue(
                "${spec.seedId} fingerprint 前缀与 brand/product/device 三元组矛盾:${p.fingerPrint}",
                p.fingerPrint.startsWith(head),
            )
            assertTrue(
                "${spec.seedId} fingerprint 段与 androidVersion/buildId 矛盾:${p.fingerPrint}",
                p.fingerPrint.contains(":${p.androidVersion}/${p.buildId}/"),
            )
        }
    }

    @Test
    fun `release string and sdk level agree on the same android generation`() {
        // 只锁本仓实际用到的配对;新增档位若用了新世代,必须在此登记,否则视为未核验组合。
        val knownPairs = mapOf("14" to setOf(34))
        for (spec in BuiltinDeviceProfiles.BUILTINS) {
            val allowed = knownPairs[spec.profile.androidVersion]
            assertTrue(
                "${spec.seedId} 的 release/sdk 组合未在门禁登记:${spec.profile.androidVersion}/${spec.profile.sdkInt}",
                allowed != null && spec.profile.sdkInt in allowed,
            )
        }
    }

    @Test
    fun `model product and device are distinct concepts and non blank`() {
        for (spec in BuiltinDeviceProfiles.BUILTINS) {
            val p = spec.profile
            assertTrue(p.model.isNotBlank() && p.product.isNotBlank() && p.device.isNotBlank())
            assertNotEquals(
                "${spec.seedId} MODEL 与 PRODUCT 同值(两者语义不同,基线明确拒绝互相猜测)",
                p.model,
                p.product,
            )
        }
    }

    @Test
    fun `density and core counts sit inside declared ranges`() {
        for (spec in BuiltinDeviceProfiles.BUILTINS) {
            val p = spec.profile
            assertTrue(
                "${spec.seedId} densityDpi 越界:${p.densityDpi}",
                p.densityDpi in ProfileValues.DENSITY_MIN..ProfileValues.DENSITY_MAX,
            )
            assertTrue(
                "${spec.seedId} cpuCores 越界:${p.cpuCores}",
                p.cpuCores in 1..ProfileValues.CPU_CORES_MAX,
            )
        }
    }

    @Test
    fun `seed ids are stable keys and entry ids are prefixed`() {
        BuiltinDeviceProfiles.BUILTINS.forEach { spec ->
            assertTrue(spec.entryId.startsWith(BuiltinDeviceProfiles.ID_PREFIX))
            assertEquals(BuiltinDeviceProfiles.idFor(spec.seedId), spec.entryId)
            assertEquals(spec, BuiltinDeviceProfiles.specFor(spec.seedId))
        }
        assertNull(BuiltinDeviceProfiles.specFor("no-such-device"))
    }

    @Test
    fun `builtin content version is at least one for every spec`() {
        BuiltinDeviceProfiles.BUILTINS.forEach {
            assertTrue("${it.seedId} 版本必须从 1 起", it.version >= 1)
        }
    }
}
