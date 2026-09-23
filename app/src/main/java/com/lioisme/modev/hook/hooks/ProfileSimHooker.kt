package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.DeviceEnvKind
import com.lioisme.modev.core.model.DeviceEnvOperation
import com.lioisme.modev.core.model.ProfileField
import com.lioisme.modev.core.model.isSetIn
import com.lioisme.modev.core.model.stringOf
import com.lioisme.modev.hook.ProbeContext

/**
 * 画像强制档 · SIM 运营商族(V49 WP-D3,基线 `SimHook` 同族)。
 *
 * 三个画像字段撑起整族:运营商码(`MCCMNC`,5~6 位数字)、运营商名、国家码(ISO 3166 两字母)。
 * **MCC/MNC 不另立字段** —— 它们由运营商码 `take(3)` / `drop(3)` 派生(基线同式),
 * 派生唯一落在本类的 [mccMnc];让用户能单独改 MCC 就会造出「MCC=310 而运营商码=46001」这种
 * 从不存在的运营商。
 *
 * `getCarrierName` / `getDisplayName` 返回 `CharSequence`,`String` 即是它的一个实现,
 * 直接 setResult 安全(调用方拿到的是同一形状的不可变值)。
 *
 * 六类 `CellIdentity` 的取口名与 `SubscriptionInfo` **不同**(`getMobileCountryCode` 系而非
 * `getMcc` 系),照抄基线那份 `getMcc` 名单会在基站类上静默空装 —— 因此这里按各自真实签名分列,
 * 并由 `unresolvedHookPoints` 恒空的断言兜底(V49-DFC-13 的同一族病)。
 */
class ProfileSimHooker(
    context: ProbeContext,
) : DeviceEnvBaseHooker(context) {

    private fun profile() = config().feature.deviceProfile

    private fun text(field: ProfileField): String? =
        if (field.isSetIn(profile())) field.stringOf(profile()) else null

    /** 运营商码合法才派生(基线判据逐字照搬:5~6 位纯数字)。 */
    private fun mccMnc(): Pair<String, String>? {
        val operator = text(ProfileField.SIM_OPERATOR) ?: return null
        if (operator.length !in 5..6 || operator.any { !it.isDigit() }) return null
        return operator.take(3) to operator.drop(3)
    }

    override fun install() {
        for (method in listOf("getSimOperator", "getNetworkOperator")) {
            hookReturn(KIND, OP, TELEPHONY, method) { text(ProfileField.SIM_OPERATOR) }
        }
        for (method in listOf("getSimOperatorName", "getNetworkOperatorName")) {
            hookReturn(KIND, OP, TELEPHONY, method) { text(ProfileField.SIM_OPERATOR_NAME) }
        }
        for (method in listOf("getSimCountryIso", "getNetworkCountryIso")) {
            hookReturn(KIND, OP, TELEPHONY, method) { text(ProfileField.SIM_COUNTRY)?.lowercase() }
        }

        // SubscriptionInfo(活动订阅;carrierName/displayName 是 CharSequence)
        hookReturn(KIND, OP, SUB_INFO, "getCountryIso") { text(ProfileField.SIM_COUNTRY)?.lowercase() }
        hookReturn(KIND, OP, SUB_INFO, "getCarrierName") { text(ProfileField.SIM_OPERATOR_NAME) }
        hookReturn(KIND, OP, SUB_INFO, "getDisplayName") { text(ProfileField.SIM_OPERATOR_NAME) }
        hookReturn(KIND, OP, SUB_INFO, "getMccString") { mccMnc()?.first }
        hookReturn(KIND, OP, SUB_INFO, "getMncString") { mccMnc()?.second }
        hookReturn(KIND, OP, SUB_INFO, "getMcc") { mccMnc()?.first?.toIntOrNull() }
        hookReturn(KIND, OP, SUB_INFO, "getMnc") {
            // MNC 可能是 2 位("01" -> 1);非法数字 ⇒ null ⇒ 不改写(不猜)。
            mccMnc()?.second?.toIntOrNull()
        }

        // **基站身份类(CellIdentity 与六个子类)不钩**:四个 mcc/mnc 取口是 @SystemApi/@hide,
        // 公开 android.jar 里不存在(sdk 34 反射解析恒抛 NoSuchMethodException,本文件首版实测),
        // 普通靶程序也调不到 ⇒ 钩了就是零读者的死代码(V49-DFC-13 同一形态)。
        // 要覆盖系统级读取方,须连同 hidden-API 豁免另批定档。
    }

    private companion object {
        val KIND = DeviceEnvKind.PROFILE_SIM
        val OP = DeviceEnvOperation.SIM_WRITE
        const val TELEPHONY = "android.telephony.TelephonyManager"
        const val SUB_INFO = "android.telephony.SubscriptionInfo"
    }
}
