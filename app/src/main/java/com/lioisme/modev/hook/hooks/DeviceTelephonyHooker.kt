package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.DeviceEnvKind
import com.lioisme.modev.core.model.DeviceEnvOperation
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.MethodHook
import com.lioisme.modev.hook.ProbeContext

/**
 * 模拟器电话信息伪造 Hooker(V34):拦截 TelephonyManager 系列 API,
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
 */
class DeviceTelephonyHooker(
    context: ProbeContext,
) : DeviceEnvBaseHooker(context) {

    override fun install() {
        hookGetDeviceId()
        hookGetImei()
        hookGetSubscriberId()
        hookGetSimSerialNumber()
        hookGetLine1Number()
    }

    private fun hookGetDeviceId() {
        val clazz = runCatching { Class.forName("android.telephony.TelephonyManager") }.getOrNull() ?: return
        val method = runCatching { clazz.getMethod("getDeviceId") }.getOrNull() ?: return
        engine.hook(method, object : MethodHook() {
            override fun before(invocation: Invocation) {
                if (!config().deviceEnvEnabled(DeviceEnvKind.EMULATOR_TELEPHONY)) return
                invocation.setResult(generateImei("device_id"))
                emit(
                    kind = DeviceEnvKind.EMULATOR_TELEPHONY,
                    operation = DeviceEnvOperation.PHONE_DEVICE_ID,
                    interventionAction = ACTION_BLOCKED,
                    targetClass = "TelephonyManager",
                    targetProperty = "deviceId",
                )
            }
        })
    }

    private fun hookGetImei() {
        val clazz = runCatching { Class.forName("android.telephony.TelephonyManager") }.getOrNull() ?: return
        // getImei() 无参
        val methodNoArg = runCatching { clazz.getMethod("getImei") }.getOrNull()
        if (methodNoArg != null) {
            engine.hook(methodNoArg, object : MethodHook() {
                override fun before(invocation: Invocation) {
                    if (!config().deviceEnvEnabled(DeviceEnvKind.EMULATOR_TELEPHONY)) return
                    invocation.setResult(generateImei("imei"))
                    emit(
                        kind = DeviceEnvKind.EMULATOR_TELEPHONY,
                        operation = DeviceEnvOperation.PHONE_IMEI,
                        interventionAction = ACTION_BLOCKED,
                        targetClass = "TelephonyManager",
                        targetProperty = "imei",
                    )
                }
            })
        }
        // getImei(int slotIndex) API 26+
        val methodSlot = runCatching { clazz.getMethod("getImei", Int::class.javaPrimitiveType) }.getOrNull()
        if (methodSlot != null) {
            engine.hook(methodSlot, object : MethodHook() {
                override fun before(invocation: Invocation) {
                    if (!config().deviceEnvEnabled(DeviceEnvKind.EMULATOR_TELEPHONY)) return
                    val slot = invocation.getArg(0) as? Int ?: 0
                    invocation.setResult(generateImei("imei_$slot"))
                    emit(
                        kind = DeviceEnvKind.EMULATOR_TELEPHONY,
                        operation = DeviceEnvOperation.PHONE_IMEI,
                        interventionAction = ACTION_BLOCKED,
                        targetClass = "TelephonyManager",
                        targetProperty = "imei[$slot]",
                    )
                }
            })
        }
    }

    private fun hookGetSubscriberId() {
        val clazz = runCatching { Class.forName("android.telephony.TelephonyManager") }.getOrNull() ?: return
        val method = runCatching { clazz.getMethod("getSubscriberId") }.getOrNull() ?: return
        engine.hook(method, object : MethodHook() {
            override fun before(invocation: Invocation) {
                if (!config().deviceEnvEnabled(DeviceEnvKind.EMULATOR_TELEPHONY)) return
                invocation.setResult(generateImsi())
                emit(
                    kind = DeviceEnvKind.EMULATOR_TELEPHONY,
                    operation = DeviceEnvOperation.PHONE_SUBSCRIBER_ID,
                    interventionAction = ACTION_BLOCKED,
                    targetClass = "TelephonyManager",
                    targetProperty = "subscriberId",
                )
            }
        })
    }

    private fun hookGetSimSerialNumber() {
        val clazz = runCatching { Class.forName("android.telephony.TelephonyManager") }.getOrNull() ?: return
        val method = runCatching { clazz.getMethod("getSimSerialNumber") }.getOrNull() ?: return
        engine.hook(method, object : MethodHook() {
            override fun before(invocation: Invocation) {
                if (!config().deviceEnvEnabled(DeviceEnvKind.EMULATOR_TELEPHONY)) return
                invocation.setResult(generateIccid())
                emit(
                    kind = DeviceEnvKind.EMULATOR_TELEPHONY,
                    operation = DeviceEnvOperation.PHONE_SIM_SERIAL,
                    interventionAction = ACTION_BLOCKED,
                    targetClass = "TelephonyManager",
                    targetProperty = "simSerialNumber",
                )
            }
        })
    }

    private fun hookGetLine1Number() {
        val clazz = runCatching { Class.forName("android.telephony.TelephonyManager") }.getOrNull() ?: return
        val method = runCatching { clazz.getMethod("getLine1Number") }.getOrNull() ?: return
        engine.hook(method, object : MethodHook() {
            override fun before(invocation: Invocation) {
                if (!config().deviceEnvEnabled(DeviceEnvKind.EMULATOR_TELEPHONY)) return
                invocation.setResult(generatePhoneNumber())
                emit(
                    kind = DeviceEnvKind.EMULATOR_TELEPHONY,
                    operation = DeviceEnvOperation.PHONE_LINE1_NUMBER,
                    interventionAction = ACTION_BLOCKED,
                    targetClass = "TelephonyManager",
                    targetProperty = "line1Number",
                )
            }
        })
    }

    /**
     * 基于种子生成确定性假值:同包名+同种子 → 同假值。
     */
    private fun generateImei(seed: String): String {
        val hash = (packageName + seed).hashCode()
        val digits = kotlin.math.abs(hash).toString().take(14)
        return digits + luhnChecksum(digits)
    }

    private fun generateImsi(): String {
        val hash = (packageName + "imsi").hashCode()
        val part = kotlin.math.abs(hash).toString().take(14)
        return "46000${part}"
    }

    private fun generateIccid(): String {
        val hash = (packageName + "iccid").hashCode()
        val part = kotlin.math.abs(hash).toString().take(18)
        return "89860${part}"
    }

    private fun generatePhoneNumber(): String {
        val hash = (packageName + "phone").hashCode()
        val part = kotlin.math.abs(hash).toString().take(8)
        return "138${part}"
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
