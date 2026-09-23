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
import java.util.concurrent.CopyOnWriteArrayList
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
 *  2. **门控 + 落地 + 审计对账**(Robolectric 真 `android.os.Build`):master/kind 关 ⇒ 字段一字
 *     不动;开 + 画像填了 ⇒ 读字段现值判「落地」,并与其审计记录**一一对应**。
 *     端到端「靶程序读到了什么」仍归 WP-F 设备档判决(JVM 与 ART 的反射与内联语义不同源,
 *     本档绿只是必要非充分)。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProfileBuildHookerTest {

    private class NoopPlatform : Platform {
        override fun register(executable: Executable?, hook: MethodHook): Boolean = true
        override fun log(message: String, throwable: Throwable?) = Unit
    }

    private fun context(switch: HookSwitch, sink: MutableList<MonitoredRecord> = CopyOnWriteArrayList()) =
        ProbeContext(
            engine = HookEngine(NoopPlatform()),
            config = { switch },
            configWatcher = ConfigWatcher(),
            tracker = CryptoContextTracker,
            // 必须 start():未启动的 sink 在 `emit` 首句 `if (!running) return` 就丢弃,
            // 下面两条审计断言会恒绿在空列表上(V49 WP-D2 实测抓到这条假绿形态)。
            sink = RecordSink(
                jsonOf = { record -> sink += record; "" },
                sendJson = { true },
                isConnected = { false },
            ).also { it.start() },
            packageName = "com.gate.test",
        )

    private fun hooker(switch: HookSwitch) = ProfileBuildHooker(context(switch))

    /** 记录经工作线程异步回调 ⇒ 断言前给一个固定的沉淀窗(0 条的断言必须有这个等待,否则恒真)。 */
    private fun settle() = runCatching { Thread.sleep(200) }

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
    fun appliedWritesAreAuditedForExactlyTheFieldsThatLanded() {
        val records = CopyOnWriteArrayList<MonitoredRecord>()
        val switch = HookSwitch(feature = FeatureConfig(deviceProfile = filledProfile))
        val hooker = ProfileBuildHooker(context(switch, records))
        val planned = hooker.plannedWrites(filledProfile)
        hooker.applyOnce()
        settle()

        // 「落地」由字段现值判定,不由记录数判定 —— 两者一一对应才是本用例的主张。
        // (V49 WP-D1 曾断言「JVM 恒 0/11 拒写」,那是在**未启动的 sink** 上数记录数得出的假结论:
        //  sink 未 start 时 `emit` 首句即丢,记录恒空 ⇒ 看着像「一个字段都没写成」。实测启动后
        //  静态字段写入按下述逐字段成立/抛异常,故此处只钉对应关系,不再钉「全有或全无」。)
        val landed = planned.filter { readStatic(it) == it.value }.map { it.fieldName }
        val auditedFields = records.map {
            (it as DeviceEnvRecord).targetProperty?.substringAfterLast('.') ?: "?"
        }
        assertEquals(
            "每条落地的写入恰有一条审计,反之亦然(记录多 = 半途留痕;记录少 = 改了没审计)。" +
                "落地:${landed.sorted()} / 记录:${auditedFields.sorted()}",
            landed.sorted(),
            auditedFields.sorted(),
        )
        records.forEach { record ->
            assertEquals("BLOCKED", (record as DeviceEnvRecord).interventionAction)
        }
        // 钉住「JVM 档确实写进去了」这一半(V49 WP-D2 实测 = 11 项里 10 项落地,唯 `Build.ID` 被拒)。
        // 没有这条,读代码的人会再次误以为「JVM 恒零写入,所以审计无从验证」—— 那正是本文件曾经的注释。
        assertTrue(
            "静态字段写入若在 JVM 上全部失效,本档就退化成纯计划表测试,须改注释而不是留红;" +
                "被拒:${planned.map { it.fieldName }.filterNot { it in landed }}",
            landed.isNotEmpty(),
        )
    }

    /** 反射读回静态字段现值(`Build.X` 与 `Build$VERSION.Y`,int 字段读成字符串比较)。 */
    private fun readStatic(write: ProfileBuildHooker.BuildWrite): String? = runCatching {
        val field = Class.forName(write.owner).getDeclaredField(write.fieldName)
        field.isAccessible = true
        field.get(null)?.toString()
    }.getOrNull()

    /**
     * 审计路径本身可证的那一半(V49 WP-D2 补):空画像 ⇒ 一条记录都不发。
     *
     * 上面那条「全有或全无」在 JVM 档恒落在「全无」分支,单靠它证明不了审计通路;
     * 这一条与反射能否写 `static final` 无关,门控与计划表若错发记录,这里必红。
     */
    @Test
    fun emptyProfileEmitsNothingAtAll() {
        val records = CopyOnWriteArrayList<MonitoredRecord>()
        val switch = HookSwitch(feature = FeatureConfig(deviceProfile = DeviceProfile.EMPTY))
        ProfileBuildHooker(context(switch, records)).applyOnce()
        settle()
        assertEquals("空画像必须零记录(默认开闸的审计侧前提)", 0, records.size)
    }
}
