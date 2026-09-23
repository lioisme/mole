package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.DeviceEnvKind
import com.lioisme.modev.core.model.DeviceEnvOperation
import com.lioisme.modev.core.model.ProfileField
import com.lioisme.modev.core.model.isSetIn
import com.lioisme.modev.core.model.stringOf
import com.lioisme.modev.hook.ProbeContext

/**
 * 画像强制档 · 电话标识族(V49 WP-D3,基线 `UniquelyIdHook` 的 imei/phoneNum 段)。
 *
 * 覆盖 IMEI 的四条读口 + TAC 派生 + 手机号三条读口;`getSubscriberId`(IMSI)与
 * `getSimSerialNumber`(ICCID)**不在画像字段表里**,仍归条件档 [DeviceTelephonyHooker] ——
 * 所以让位按**方法**而非整族发生(同 D2 电池的键空间形)。
 */
class ProfileTelephonyIdHooker(
    context: ProbeContext,
) : DeviceEnvBaseHooker(context) {

    private fun imei(): String? = valueOf(ProfileField.IMEI)

    private fun phoneNum(): String? = valueOf(ProfileField.PHONE_NUM)

    private fun valueOf(field: ProfileField): String? {
        val profile = config().feature.deviceProfile
        return if (field.isSetIn(profile)) field.stringOf(profile) else null
    }

    override fun install() {
        // 每一项都必须是**该 API 级别上真实存在**的重载:查不到不抛,但会进
        // unresolvedHookPoints(见基类),测试据此钉住「列了个不存在的方法名」= V49-DFC-13 形态。
        for ((method, slotted) in listOf(
            "getDeviceId" to true,
            "getImei" to true,
            "getPrimaryImei" to false,
        )) {
            hookReturn(KIND, OP, TELEPHONY, method) { imei() }
            if (slotted) hookReturn(KIND, OP, TELEPHONY, method, intType) { imei() }
        }
        hookReturn(KIND, OP, TELEPHONY, "getTypeAllocationCode") { imei()?.take(8) }
        hookReturn(KIND, OP, TELEPHONY, "getLine1Number") { phoneNum() }
        hookReturn(KIND, OP, TELEPHONY, "getLine1Number", intType) { phoneNum() }
        // 只有带卡槽参数的形态真实存在(无参重载在公开 API 里没有;写出来就是永不生效的死钩子)。
        hookReturn(KIND, OP, SUB_MANAGER, "getPhoneNumber", intType) { phoneNum() }
        hookReturn(KIND, OP, SUB_INFO, "getNumber") { phoneNum() }
    }

    private companion object {
        val KIND = DeviceEnvKind.PROFILE_TELEPHONY_ID
        val OP = DeviceEnvOperation.TELEPHONY_ID_WRITE
        const val TELEPHONY = "android.telephony.TelephonyManager"
        const val SUB_MANAGER = "android.telephony.SubscriptionManager"
        const val SUB_INFO = "android.telephony.SubscriptionInfo"
        val intType: Class<*> = Int::class.javaPrimitiveType!!
    }
}
