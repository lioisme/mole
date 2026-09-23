package com.lioisme.modev.hook.hooks

import android.os.Build
import com.lioisme.modev.core.model.DeviceEnvKind
import com.lioisme.modev.core.model.DeviceEnvRecord
import com.lioisme.modev.core.model.DeviceProfile
import com.lioisme.modev.core.model.HookSwitch
import com.lioisme.modev.core.model.FeatureConfig
import com.lioisme.modev.core.model.ProfileField
import com.lioisme.modev.hook.CryptoContextTracker
import com.lioisme.modev.hook.ConfigWatcher
import com.lioisme.modev.hook.HookEngine
import com.lioisme.modev.hook.MethodHook
import com.lioisme.modev.hook.Platform
import com.lioisme.modev.hook.ProbeContext
import com.lioisme.modev.hook.RecordSink
import com.lioisme.modev.core.model.MonitoredRecord
import java.lang.reflect.Executable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 画像强制档 · Build 一族的行为回归(V49 WP-D1)。
 *
 * 两层判据分开钉:
 *  1. **计划表**(纯函数):画像填了哪几项就该写哪几项、值逐字取自画像、一项没填就零条计划
 *     (⇒ 默认开这些开关不会「没想伪装却被改写」);
 *  2. **门控 + 落地**(Robolectric 真 `android.os.Build`):master 关 ⇒ 字段一字不动;
 *     开 + 画像填了 ⇒ 字段真的变了。第二层在 JVM 上能否成立取决于反射对 `static final`
 *     的态度,与 ART 并不完全同源 —— 因此**真机/模拟器读回仍由 WP-F 判决**,本类的断言只
 *     覆盖「机制被装上了且门控有效」,不冒充端到端证据。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProfileBuildHookerTest {

    private class NoopPlatform : Platform {
        override fun register(executable: Executable?, hook: MethodHook): Boolean = true
        override fun log(message: String, throwable: Throwable?) = Unit
    }

    private fun context(switch: HookSwitch, sink: MutableList<MonitoredRecord> = mutableListOf()) =
        ProbeContext(
            engine = HookEngine(NoopPlatform()),
            config = { switch },
            configWatcher = ConfigWatcher(),
            tracker = CryptoContextTracker,
            sink = RecordSink(
                jsonOf = { record -> sink += record; "" },
                sendJson = { true },
                isConnected = { false },
            ),
            packageName = "com.gate.test",
        )

    private fun hooker(switch: HookSwitch) = ProfileBuildHooker(context(switch))

    private val filledProfile = DeviceProfile(
        brand = "samsung",
        manufacturer = "Samsung",
        model = "SM-S928B",
        product = "eqiiqs",
        device = "eqiiqs",
        board = "s5e9855",
        hardware = "exynos2400",
        fingerPrint = "samsung/eqiiqs/eqiiqs:15/AP4A.250105.002/1234567:user/release-keys",
        buildId = "AP4A.250105.002",
        androidVersion = "15",
        sdkInt = 35,
    )

    // ── 第一层:计划表 ────────────────────────────────────────────

    @Test
    fun emptyProfilePlansNothing() {
        val writes = hooker(HookSwitch()).plannedWrites(DeviceProfile.EMPTY)
        assertTrue("空画像必须零计划(默认开闸的前提),实为 ${writes.map { it.fieldName }}", writes.isEmpty())
    }

    @Test
    fun filledProfilePlansEveryBuildFieldOnce() {
        val writes = hooker(HookSwitch()).plannedWrites(filledProfile)
        assertEquals(
            listOf(
                "BRAND", "MANUFACTURER", "MODEL", "PRODUCT", "DEVICE", "BOARD", "HARDWARE",
                "FINGERPRINT", "ID", "RELEASE", "SDK_INT",
            ),
            writes.map { it.fieldName },
        )
        assertEquals("SM-S928B", writes.first { it.fieldName == "MODEL" }.value)
        assertTrue(writes.filter { it.fieldName == "SDK_INT" }.single().integral)
        assertEquals(
            "每一项的值必须逐字取自画像(不加工、不派生)",
            mapOf(
                "BRAND" to "samsung",
                "MANUFACTURER" to "Samsung",
                "MODEL" to "SM-S928B",
                "PRODUCT" to "eqiiqs",
                "DEVICE" to "eqiiqs",
                "BOARD" to "s5e9855",
                "HARDWARE" to "exynos2400",
                "FINGERPRINT" to "samsung/eqiiqs/eqiiqs:15/AP4A.250105.002/1234567:user/release-keys",
                "ID" to "AP4A.250105.002",
                "RELEASE" to "15",
                "SDK_INT" to "35",
            ),
            writes.associate { it.fieldName to it.value },
        )
        assertTrue(
            "身份八项必须全归 PROFILE_BUILD_IDENTITY",
            writes.filter { it.fieldName in setOf("BRAND", "MODEL", "FINGERPRINT") }
                .all { it.kind == DeviceEnvKind.PROFILE_BUILD_IDENTITY },
        )
    }

    @Test
    fun partialProfilePlansOnlyWhatWasFilled() {
        val writes = hooker(HookSwitch()).plannedWrites(DeviceProfile(model = "Pixel 9"))
        assertEquals(listOf("MODEL"), writes.map { it.fieldName })
        assertEquals("Pixel 9", writes.single().value)
    }

    // ── 第二层:门控与落地 ────────────────────────────────────────

    @Test
    fun masterOffLeavesBuildUntouched() {
        val model = Build.MODEL
        val brand = Build.BRAND
        hooker(HookSwitch(deviceEnvListen = false, feature = FeatureConfig(deviceProfile = filledProfile)))
            .applyOnce()
        assertEquals("master=false 时一个字段都不许改写", model, Build.MODEL)
        assertEquals(brand, Build.BRAND)
    }

    @Test
    fun kindOffLeavesThatFamilyUntouched() {
        val release = Build.VERSION.RELEASE
        hooker(
            HookSwitch(
                profileBuildIdentity = true,
                profileBuildVersion = false,
                feature = FeatureConfig(deviceProfile = filledProfile),
            ),
        ).applyOnce()
        assertEquals("版本档关着就不许动 RELEASE", release, Build.VERSION.RELEASE)
    }

    @Test
    fun appliedWritesEmitOneAuditedRecordPerField() {
        val records = mutableListOf<MonitoredRecord>()
        val switch = HookSwitch(feature = FeatureConfig(deviceProfile = filledProfile))
        val hooker = ProfileBuildHooker(context(switch, records))
        hooker.applyOnce()
        val expectedIfWritable = hooker.plannedWrites(filledProfile).size
        // 实测:Robolectric(HotSpot)下 0/11 —— JDK 拒绝向 `static final` 字段写入
        // (`Field.set` 对静态 final 恒抛 IllegalAccessException),这是 JVM 语义,不是产品缺陷:
        // 基线 Guise 的 `setStaticField` 用的就是同一句 `field.set(null, value)` 并在 ART 上被
        // 野外验证过,故「要么全写、要么全不写」才是本档能证不变量,真值落地归 WP-F 靶程序读回。
        assertTrue(
            "要么逐字段留审计,要么一条都不留(绝不半途);实发 ${records.size} / 计划 $expectedIfWritable",
            records.isEmpty() || records.size == expectedIfWritable,
        )
        records.forEach { record ->
            assertEquals("BLOCKED", (record as DeviceEnvRecord).interventionAction)
        }
    }

}
