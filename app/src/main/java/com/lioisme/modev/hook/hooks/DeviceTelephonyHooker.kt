package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.DeviceEnvKind
import com.lioisme.modev.core.model.DeviceEnvOperation
import com.lioisme.modev.core.model.ProfileField
import com.lioisme.modev.core.model.activeSpoofProfile
import com.lioisme.modev.core.model.isSetIn
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.ProbeContext
import com.lioisme.modev.hook.SharedMethodBroadcast

/**
 * 模拟器电话信息伪造 Hooker(V34,**条件档**):拦截 TelephonyManager 系列 API,
 * 返回基于包名哈希的稳定假值,防止模拟器检测框架通过电话信息识别环境。
 *
 * 覆盖 API:
 * - getDeviceId():15 位 IMEI 格式假值
 * - getImei()/getImei(slot):双卡 IMEI 假值
 * - getSubscriberId():IMSI 格式假值
 * - getSimSerialNumber():ICCID 格式假值
 * - getLine1Number():手机号格式假值
 *
 * 假值生成策略:包名哈希确定性映射(同包名始终产生同假值,防不一致检测)。
 *
 * V49 WP-D3 两处收口:
 *  1. **与画像强制档共享读口**:`getDeviceId`/`getImei*`/`getLine1Number` 也是
 *     [ProfileTelephonyIdHooker] 的钩点 ⇒ 全部改经 [SharedMethodBroadcast.subscribe]
 *     (二次注册在 Vector 上静默失声),并且**按方法让位**:画像填了 IMEI 就不许本档再写
 *     IMEI 读口,填了手机号就不写 `getLine1Number`。
 *  2. IMSI 与 ICCID **不在画像字段表里**,本档继续独占 —— 让位粒度必须是方法而不是整族。
 */
class DeviceTelephonyHooker(
    context: ProbeContext,
) : DeviceEnvBaseHooker(context) {

    /** 画像是否已把该字段接管走(V49 R-D 的按键空间形;判据就是画像那一项填没填)。 */
    private fun forcedByProfile(field: ProfileField): Boolean =
        field.isSetIn(config().feature.deviceProfile)

    private fun maskActive(field: ProfileField): Boolean =
        featureOn(DeviceEnvKind.EMULATOR_TELEPHONY) && !forcedByProfile(field)

    override fun install() {
        hookGetDeviceId()
        hookGetImei()
        hookGetSubscriberId()
        hookGetSimSerialNumber()
        hookGetLine1Number()
    }

    private fun hookGetDeviceId() {
        val clazz = runCatching { Class.forName("android.telephony.TelephonyManager") }.getOrNull() ?: return
        val method = runCatching { clazz.getMethod("getDeviceId") }.getOrNull()
        broadcast.subscribe(
            method,
            object : SharedMethodBroadcast.Sub {
                override fun before(invocation: Invocation) {
                    if (!maskActive(ProfileField.IMEI)) return
                    invocation.setResult(generateImei("device_id"))
                    emit(
                        kind = DeviceEnvKind.EMULATOR_TELEPHONY,
                        operation = DeviceEnvOperation.PHONE_DEVICE_ID,
                        interventionAction = ACTION_BLOCKED,
                        targetClass = "TelephonyManager",
                        targetProperty = "deviceId",
                    )
                }
            },
        )
    }

    private fun hookGetImei() {
        val clazz = runCatching { Class.forName("android.telephony.TelephonyManager") }.getOrNull() ?: return
        for ((method, property) in listOf(
            runCatching { clazz.getMethod("getImei") }.getOrNull() to "imei",
            runCatching { clazz.getMethod("getImei", Int::class.javaPrimitiveType) }.getOrNull() to "imei[slot]",
        )) {
            if (method == null) continue
            broadcast.subscribe(
                method,
                object : SharedMethodBroadcast.Sub {
                    override fun before(invocation: Invocation) {
                        if (!maskActive(ProfileField.IMEI)) return
                        val slot = invocation.getArg(0) as? Int ?: 0
                        invocation.setResult(generateImei(if (slot == 0) "imei" else "imei_$slot"))
                        emit(
                            kind = DeviceEnvKind.EMULATOR_TELEPHONY,
                            operation = DeviceEnvOperation.PHONE_IMEI,
                            interventionAction = ACTION_BLOCKED,
                            targetClass = "TelephonyManager",
                            targetProperty = property,
                        )
                    }
                },
            )
        }
    }

    private fun hookGetSubscriberId() {
        val clazz = runCatching { Class.forName("android.telephony.TelephonyManager") }.getOrNull() ?: return
        val method = runCatching { clazz.getMethod("getSubscriberId") }.getOrNull()
        broadcast.subscribe(
            method,
            object : SharedMethodBroadcast.Sub {
                override fun before(invocation: Invocation) {
                    if (!featureOn(DeviceEnvKind.EMULATOR_TELEPHONY)) return
                    invocation.setResult(generateImsi())
                    emit(
                        kind = DeviceEnvKind.EMULATOR_TELEPHONY,
                        operation = DeviceEnvOperation.PHONE_SUBSCRIBER_ID,
                        interventionAction = ACTION_BLOCKED,
                        targetClass = "TelephonyManager",
                        targetProperty = "subscriberId",
                    )
                }
            },
        )
    }

    private fun hookGetSimSerialNumber() {
        val clazz = runCatching { Class.forName("android.telephony.TelephonyManager") }.getOrNull() ?: return
        val method = runCatching { clazz.getMethod("getSimSerialNumber") }.getOrNull()
        broadcast.subscribe(
            method,
            object : SharedMethodBroadcast.Sub {
                override fun before(invocation: Invocation) {
                    if (!featureOn(DeviceEnvKind.EMULATOR_TELEPHONY)) return
                    invocation.setResult(generateIccid())
                    emit(
                        kind = DeviceEnvKind.EMULATOR_TELEPHONY,
                        operation = DeviceEnvOperation.PHONE_SIM_SERIAL,
                        interventionAction = ACTION_BLOCKED,
                        targetClass = "TelephonyManager",
                        targetProperty = "simSerialNumber",
                    )
                }
            },
        )
    }

    private fun hookGetLine1Number() {
        val clazz = runCatching { Class.forName("android.telephony.TelephonyManager") }.getOrNull() ?: return
        val method = runCatching { clazz.getMethod("getLine1Number") }.getOrNull()
        broadcast.subscribe(
            method,
            object : SharedMethodBroadcast.Sub {
                override fun before(invocation: Invocation) {
                    if (!maskActive(ProfileField.PHONE_NUM)) return
                    invocation.setResult(generatePhoneNumber())
                    emit(
                        kind = DeviceEnvKind.EMULATOR_TELEPHONY,
                        operation = DeviceEnvOperation.PHONE_LINE1_NUMBER,
                        interventionAction = ACTION_BLOCKED,
                        targetClass = "TelephonyManager",
                        targetProperty = "line1Number",
                    )
                }
            },
        )
    }

    /**
     * 基于种子生成确定性假值:同包名+同种子 → 同假值。
     * V37 T2.3:前缀取自生效伪装画像(内置档位 + 用户覆写),同包恒定。
     */
    private fun generateImei(seed: String): String {
        val profile = config().activeSpoofProfile()
        val hash = (packageName + seed).hashCode()
        val digits = (profile.imeiPrefix + kotlin.math.abs(hash).toString()).take(14)
        return digits + luhnChecksum(digits)
    }

    private fun generateImsi(): String {
        val profile = config().activeSpoofProfile()
        val hash = (packageName + "imsi").hashCode()
        val part = kotlin.math.abs(hash).toString().take(15 - profile.imsiPrefix.length)
        return "${profile.imsiPrefix}$part"
    }

    private fun generateIccid(): String {
        val profile = config().activeSpoofProfile()
        val hash = (packageName + "iccid").hashCode()
        val part = kotlin.math.abs(hash).toString().take(20 - profile.iccidPrefix.length)
        return "${profile.iccidPrefix}$part"
    }

    private fun generatePhoneNumber(): String {
        val profile = config().activeSpoofProfile()
        val hash = (packageName + "phone").hashCode()
        val part = kotlin.math.abs(hash).toString().take(11 - profile.phonePrefix.length)
        return "${profile.phonePrefix}$part"
    }

    private fun luhnChecksum(num: String): Int {
        var sum = 0
        var alt = false
        for (i in num.lastIndex downTo 0) {
            var n = num[i] - '0'
            if (alt) {
                n *= 2
                if (n > 9) n -= 9
            }
            sum += n
            alt = !alt
        }
        return (10 - (sum % 10)) % 10
    }

    companion object {
        private const val ACTION_BLOCKED = "BLOCKED"
    }
}
