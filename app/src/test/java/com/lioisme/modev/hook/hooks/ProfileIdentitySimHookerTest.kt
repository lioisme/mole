package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.DeviceProfile
import com.lioisme.modev.core.model.HookSwitch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 画像强制档 · android_id / 电话标识 / SIM 三族的行为回归。
 *
 * 除了「填了改、没填不改、关闸不改」这两层,D3 特别钉三件事:
 *  1. **每个钩点都解析得到真实平台方法**(`unresolvedHookPoints` 恒空)——
 *     V49-DFC-13 的形态就是「反射查一个不存在的方法 ⇒ 被 getOrNull 咽掉 ⇒ 那条通路自始没装钩,
 *     而文档说已掩蔽」。列名单不验证 = 再来一次。
 *  2. **让位按方法而非整族**:画像填 IMEI 不牵连 IMSI/ICCID(画像里没有那两项)。
 *  3. **MCC/MNC 是派生值**:非法运营商码 ⇒ 派生不产出(不猜一个 MCC 出来)。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProfileIdentitySimHookerTest {

    private val harness = DeviceHookerHarness()

    private val profile = DeviceProfile(
        androidId = "5f8a1b2c3d4e5f60",
        imei = "356938035643809",
        phoneNum = "13800001111",
        simOperator = "46001",
        simOperatorName = "中国联通",
        simCountry = "CN",
    )

    private fun installIdentity(tweak: (HookSwitch) -> HookSwitch = { it }) {
        val ctx = harness.context(harness.switchWith(profile, tweak))
        ProfileAndroidIdHooker(ctx).install()
        ProfileTelephonyIdHooker(ctx).install()
        ProfileSimHooker(ctx).install()
    }

    // ── 1. 钩点存在性 ───────────────────────────────────────────

    @Test
    fun everyDeclaredHookPoint_resolvesToARealMethod() {
        val ctx = harness.context(harness.switchWith(profile))
        val androidId = ProfileAndroidIdHooker(ctx)
        val telephony = ProfileTelephonyIdHooker(ctx)
        val sim = ProfileSimHooker(ctx)
        androidId.install(); telephony.install(); sim.install()

        assertEquals(
            "画像族列出的每个 类#方法 都必须真实存在(查不到 = 那条通路永远不生效,即 V49-DFC-13 形态)",
            emptySet<String>(),
            androidId.unresolvedHookPoints + telephony.unresolvedHookPoints + sim.unresolvedHookPoints,
        )
    }

    // ── 2. android_id 四条读口 ──────────────────────────────────

    @Test
    fun androidId_coversSecureAndSystemOnBothReadPaths() {
        installIdentity()
        for (table in listOf(SECURE, SYSTEM)) {
            val plain = harness.hookOf(table, "getString")
            val hit = FakeCall(args = listOf(null, "android_id"), value = "deadbeef")
            plain.after(hit)
            assertEquals(listOf<Any>("5f8a1b2c3d4e5f60"), hit.rewrites)

            val other = FakeCall(args = listOf(null, "location_mode"), value = "3")
            plain.after(other)
            assertTrue("其他 Setting 键必须零动作($table.getString)", other.rewrites.isEmpty())

            val forUser = harness.hookOf(table, "getStringForUser")
            val hit2 = FakeCall(args = listOf(null, "android_id", 0), value = "")
            forUser.after(hit2)
            assertEquals(listOf<Any>("5f8a1b2c3d4e5f60"), hit2.rewrites)
        }
    }

    @Test
    fun androidId_closedGateOrEmptyProfileMeansZeroAction() {
        val ctx = harness.context(
            harness.switchWith(profile, { it.copy(profileAndroidId = false) }),
        )
        ProfileAndroidIdHooker(ctx).install()
        val call = FakeCall(args = listOf(null, "android_id"), value = "real")
        harness.hookOf(SECURE, "getString").after(call)
        assertTrue("kind 关 ⇒ 零 setResult", call.rewrites.isEmpty())

        val empty = DeviceHookerHarness()
        val ctx2 = empty.context(empty.switchWith(DeviceProfile.EMPTY))
        ProfileAndroidIdHooker(ctx2).install()
        val call2 = FakeCall(args = listOf(null, "android_id"), value = "real")
        empty.hookOf(SECURE, "getString").after(call2)
        assertTrue("画像未填 ⇒ 零 setResult", call2.rewrites.isEmpty())
    }

    @Test
    fun androidId_conditionalTierYieldsToTheProfileValue() {
        val filled = harness.context(harness.switchWith(profile))
        EmulatorNetworkHooker(filled).install()
        val withProfile = FakeCall(args = listOf(null, "android_id"), value = "unknown")
        harness.hookOf(SECURE, "getString").after(withProfile)
        assertTrue("画像填了 android_id ⇒ 条件档在该键上让位", withProfile.rewrites.isEmpty())

        val plain = DeviceHookerHarness()
        val ctx = plain.context(plain.switchWith(DeviceProfile.EMPTY, { it.copy(emulatorNetwork = true) }))
        EmulatorNetworkHooker(ctx).install()
        val emulatorValue = FakeCall(args = listOf(null, "android_id"), value = "unknown")
        plain.hookOf(SECURE, "getString").after(emulatorValue)
        assertEquals("画像没填时条件档照旧掩蔽脏值", 1, emulatorValue.rewrites.size)
        val masked = emulatorValue.rewrites.single() as String
        assertFalse(
            "掩蔽值自身不得命中本仓的模拟器 MAC/标识判据(GAP-07 同族病):$masked",
            VIRTUALIZED_OUIS.any { masked.lowercase().startsWith(it) },
        )
    }

    @Test
    fun wifiMac_maskedValueIsNeverAVirtualizedOui() {
        val ctx = harness.context(harness.switchWith(DeviceProfile.EMPTY, { it.copy(emulatorNetwork = true) }))
        EmulatorNetworkHooker(ctx).install()
        val call = FakeCall(value = "02:42:ac:11:22:33")
        harness.hookOf("android.net.wifi.WifiInfo", "getMacAddress").after(call)
        assertEquals("Docker OUI 必须被掩蔽", 1, call.rewrites.size)
        val masked = call.rewrites.single() as String
        assertFalse(
            "掩蔽值自身不得再吐虚拟化 OUI(V49-DFC-10 / GAP-07):$masked",
            VIRTUALIZED_OUIS.any { masked.lowercase().startsWith(it) },
        )
        val first = masked.substring(0, 2).toInt(16)
        assertEquals("本地管理位须置位:$masked", 0b10, first and 0b10)
        assertEquals("须仍为单播地址:$masked", 0, first and 0b01)
    }

    // ── 3. 电话标识 ─────────────────────────────────────────────

    @Test
    fun telephonyId_writesImeiTacAndPhoneAcrossEverySlot() {
        installIdentity()
        for (method in listOf("getDeviceId", "getImei", "getPrimaryImei")) {
            val call = FakeCall(args = if (method == "getImei") listOf(0) else emptyList(), value = "null")
            harness.hookOf(TELEPHONY, method).after(call)
            assertEquals(listOf<Any>(profile.imei), call.rewrites)
        }
        val tac = FakeCall(value = "null")
        harness.hookOf(TELEPHONY, "getTypeAllocationCode").after(tac)
        assertEquals("TAC 恒取 IMEI 前八位(派生,不另填)", listOf<Any>(profile.imei.take(8)), tac.rewrites)

        val line = FakeCall(value = "null")
        harness.hookOf(TELEPHONY, "getLine1Number").after(line)
        assertEquals(listOf<Any>(profile.phoneNum), line.rewrites)
        val info = FakeCall(value = "null")
        harness.hookOf(SUB_INFO, "getNumber").after(info)
        assertEquals("SubscriptionInfo.getNumber 同写", listOf<Any>(profile.phoneNum), info.rewrites)
    }

    @Test
    fun telephonyId_yieldIsPerMethodNotPerFamily() {
        // 画像只填 IMEI:手机号读口归条件档,IMSI/ICCID 更是画像表里根本没有的字段。
        val partial = DeviceProfile(imei = "356938035643809")
        val ctx = harness.context(harness.switchWith(partial, { it.copy(emulatorTelephony = true) }))
        DeviceTelephonyHooker(ctx).install()
        ProfileTelephonyIdHooker(ctx).install()

        val imei = FakeCall(value = "null")
        harness.hookOf(TELEPHONY, "getImei").after(imei)
        assertEquals("IMEI 由画像写,条件档不再重复写(值只有一处来源)", listOf<Any>(partial.imei), imei.rewrites)
        assertEquals(1, imei.rewrites.size)

        val imsi = FakeCall(value = "realImsi")
        harness.hookOf(TELEPHONY, "getSubscriberId").before(imsi)
        assertEquals("IMSI 不在画像字段表 ⇒ 条件档继续独占", 1, imsi.rewrites.size)

        val line1 = FakeCall(value = "realPhone")
        harness.hookOf(TELEPHONY, "getLine1Number").before(line1)
        assertEquals("画像没填手机号 ⇒ 条件档仍掩蔽", 1, line1.rewrites.size)
    }

    // ── 4. SIM 族与派生 ─────────────────────────────────────────

    @Test
    fun sim_operatorNameCountryAndDerivedMccMnc() {
        installIdentity()
        for (method in listOf("getSimOperator", "getNetworkOperator")) {
            val call = FakeCall(value = "46000")
            harness.hookOf(TELEPHONY, method).after(call)
            assertEquals(listOf<Any>("46001"), call.rewrites)
        }
        for (method in listOf("getSimOperatorName", "getNetworkOperatorName")) {
            val call = FakeCall(value = "CHN-Unicom")
            harness.hookOf(TELEPHONY, method).after(call)
            assertEquals(listOf<Any>("中国联通"), call.rewrites)
        }
        for (method in listOf("getSimCountryIso", "getNetworkCountryIso")) {
            val call = FakeCall(value = "us")
            harness.hookOf(TELEPHONY, method).after(call)
            assertEquals("国家码小写落值(平台上报形态)", listOf<Any>("cn"), call.rewrites)
        }
        // 原值必须与画像值**不同**:相等时钩子按幂等短路(不改写、不留痕),那是设计而非缺陷。
        val mcc = FakeCall(value = 202)
        harness.hookOf(SUB_INFO, "getMcc").after(mcc)
        assertEquals(listOf<Any>(460), mcc.rewrites)
        val mnc = FakeCall(value = 99)
        harness.hookOf(SUB_INFO, "getMnc").after(mnc)
        assertEquals("\"46001\" ⇒ MNC 串 \"01\" ⇒ int 1", listOf<Any>(1), mnc.rewrites)
    }

    /**
     * 基线那份 `CellIdentity` mcc/mnc 名单在 Modev 里是**故意不抄**的:四个取口都不在公开 SDK
     * 表面上,普通靶程序也调不到 ⇒ 留在钩点名单里就是永远装不上的死钩子(V49-DFC-13 形态)。
     * 这里反向钉住「不存在」:哪天它进了公开 API,就该重新评估那一族钩点。
     */
    @Test
    fun cellIdentityMccAccessors_areNotPublicApi_soTheyAreDeliberatelyNotHooked() {
        for (name in listOf(
            "getMobileCountryCode",
            "getMobileNetworkCode",
            "getMobileCountryCodeString",
            "getMobileNetworkCodeString",
        )) {
            val found = runCatching {
                Class.forName("android.telephony.CellIdentity").getMethod(name)
            }.isSuccess
            assertFalse("CellIdentity#$name 出现在公开 API ⇒ 需要重新评估基站身份族", found)
        }
    }

    @Test
    fun sim_illegalOperatorCodeDerivesNothing() {
        val broken = DeviceProfile(simOperator = "46")
        val ctx = harness.context(harness.switchWith(broken))
        ProfileSimHooker(ctx).install()

        val operator = FakeCall(value = "46000")
        harness.hookOf(TELEPHONY, "getSimOperator").after(operator)
        assertEquals("运营商码本身照用户所填写", listOf<Any>("46"), operator.rewrites)
        val mcc = FakeCall(value = 460)
        harness.hookOf(SUB_INFO, "getMcc").after(mcc)
        assertTrue("派生需 5~6 位纯数字才成立 ⇒ 不足位不猜 MCC", mcc.rewrites.isEmpty())
    }

    @Test
    fun audits_areOncePerMethodNotPerCall() {
        installIdentity()
        repeat(3) {
            harness.hookOf(TELEPHONY, "getSimOperator").after(FakeCall(value = "46000"))
        }
        harness.awaitAudit("getSimOperator")
        assertEquals(1, harness.audits("getSimOperator").size)
        harness.settle()
        assertEquals("反复调用不得追加记录", 1, harness.audits("getSimOperator").size)
    }

    private companion object {
        const val SECURE = "android.provider.Settings\$Secure"
        const val SYSTEM = "android.provider.Settings\$System"
        const val TELEPHONY = "android.telephony.TelephonyManager"
        const val SUB_INFO = "android.telephony.SubscriptionInfo"

        /** 本仓判为「虚拟化」的 MAC 前缀(测试侧独立复述,用来验掩蔽值不会自打)。 */
        val VIRTUALIZED_OUIS = listOf("00:00:00", "08:00:27", "52:54:00", "02:42:ac")
    }
}
