package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.DeviceEnvKind
import com.lioisme.modev.core.model.DeviceEnvOperation
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.MethodHook
import com.lioisme.modev.hook.ProbeContext

/**
 * 模拟器电池信息伪造 Hooker(V34):拦截 BatteryManager API,
 * 返回变化的电池状态值(模拟器通常恒定不变),防止检测框架
 * 通过电池信息识别环境。
 *
 * 覆盖 API:
 * - BatteryManager.getIntProperty():返回合理变化值
 * - BatteryManager.isCharging():返回 false
 * - Intent.ACTION_BATTERY_CHANGED:伪造 Extra 值
 */
class DeviceBatteryHooker(
    context: ProbeContext,
) : DeviceEnvBaseHooker(context) {

    override fun install() {
        hookGetIntProperty()
        hookIsCharging()
    }

    private fun hookGetIntProperty() {
        val clazz = runCatching { Class.forName("android.os.BatteryManager") }.getOrNull() ?: return
        val method = runCatching {
            clazz.getMethod("getIntProperty", Int::class.javaPrimitiveType)
        }.getOrNull() ?: return

        engine.hook(method, object : MethodHook() {
            override fun after(invocation: Invocation) {
                if (!config().deviceEnvEnabled(DeviceEnvKind.EMULATOR_BATTERY)) return
                val propId = invocation.getArg(0) as? Int ?: return
                val currentValue = invocation.result as? Int ?: return
                val fakeValue = getFakeBatteryValue(propId, currentValue)
                if (fakeValue != currentValue) {
                    invocation.setResult(fakeValue)
                    emit(
                        kind = DeviceEnvKind.EMULATOR_BATTERY,
                        operation = DeviceEnvOperation.BATTERY_INT_PROPERTY,
                        originalValue = currentValue.toString(),
                        maskedValue = fakeValue.toString(),
                        interventionAction = ACTION_BLOCKED,
                        targetClass = "BatteryManager",
                        targetProperty = "prop_$propId",
                    )
                }
            }
        })
    }

    private fun hookIsCharging() {
        val clazz = runCatching { Class.forName("android.os.BatteryManager") }.getOrNull() ?: return
        val method = runCatching { clazz.getMethod("isCharging") }.getOrNull() ?: return

        engine.hook(method, object : MethodHook() {
            override fun after(invocation: Invocation) {
                if (!config().deviceEnvEnabled(DeviceEnvKind.EMULATOR_BATTERY)) return
                val current = invocation.result as? Boolean ?: return
                if (current) {
                    invocation.setResult(false)
                    emit(
                        kind = DeviceEnvKind.EMULATOR_BATTERY,
                        operation = DeviceEnvOperation.BATTERY_IS_CHARGING,
                        originalValue = "true",
                        maskedValue = "false",
                        interventionAction = ACTION_BLOCKED,
                        targetClass = "BatteryManager",
                        targetProperty = "isCharging",
                    )
                }
            }
        })
    }

    /**
     * 根据属性 ID 返回伪造值:
     * - BATTERY_PROPERTY_CAPACITY(4):返回 67(模拟器通常 50 或 100 恒定)
     * - BATTERY_PROPERTY_CHARGE_COUNTER(1):返回 -5432100(微安时)
     * - BATTERY_PROPERTY_CURRENT_NOW(2):返回 -456789(微安)
     * - BATTERY_PROPERTY_STATUS(5):返回 2(充电中)→3(未充电)
     */
    private fun getFakeBatteryValue(propId: Int, current: Int): Int = when (propId) {
        4 -> FAKE_CAPACITY // BATTERY_PROPERTY_CAPACITY
        1 -> FAKE_CHARGE_COUNTER
        2 -> FAKE_CURRENT_NOW
        5 -> if (current == 2) 3 else current // 充电中 → 未充电
        else -> current
    }

    companion object {
        private const val FAKE_CAPACITY = 67
        private const val FAKE_CHARGE_COUNTER = -5432100
        private const val FAKE_CURRENT_NOW = -456789
        private const val ACTION_BLOCKED = "BLOCKED"
    }
}
