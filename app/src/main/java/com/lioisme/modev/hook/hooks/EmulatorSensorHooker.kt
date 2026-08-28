package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.DeviceEnvKind
import com.lioisme.modev.core.model.DeviceEnvOperation
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.MethodHook
import com.lioisme.modev.hook.ProbeContext

/**
 * 模拟器传感器检测 Hooker(V31):拦截 SensorManager.getDefaultSensor/getSensorList,
 * 仅记录检测事件,不干预返回值。
 *
 * 设计决策:Sensor 类无公共无参构造器,创建假 Sensor 对象必然失败或产生无效实例,
 * App 强转 Sensor 时 ClassCastException → 崩溃。因此本 hooker 采用纯监控模式,
 * 仅在检测到传感器查询时 emit 记录,不修改任何返回值。
 */
class EmulatorSensorHooker(
    context: ProbeContext,
) : DeviceEnvBaseHooker(context) {

    override fun install() {
        hookGetDefaultSensor()
        hookGetSensorList()
    }

    private fun hookGetDefaultSensor() {
        val clazz = runCatching { Class.forName("android.hardware.SensorManager") }.getOrNull() ?: return
        val method = runCatching {
            clazz.getMethod("getDefaultSensor", Int::class.javaPrimitiveType)
        }.getOrNull() ?: return
        engine.hook(method, object : MethodHook() {
            override fun after(invocation: Invocation) {
                if (!config().deviceEnvEnabled(DeviceEnvKind.EMULATOR_SENSOR)) return
                val sensorType = invocation.getArg(0) as? Int ?: return
                emit(
                    kind = DeviceEnvKind.EMULATOR_SENSOR,
                    operation = DeviceEnvOperation.GET_DEFAULT_SENSOR,
                    originalValue = if (invocation.result != null) "non-null" else "null",
                    params = mapOf("sensorType" to sensorType.toString()),
                )
            }
        })
    }

    private fun hookGetSensorList() {
        val clazz = runCatching { Class.forName("android.hardware.SensorManager") }.getOrNull() ?: return
        val method = runCatching {
            clazz.getMethod("getSensorList", Int::class.javaPrimitiveType)
        }.getOrNull() ?: return
        engine.hook(method, object : MethodHook() {
            override fun after(invocation: Invocation) {
                if (!config().deviceEnvEnabled(DeviceEnvKind.EMULATOR_SENSOR)) return
                @Suppress("UNCHECKED_CAST")
                val original = invocation.result as? List<Any> ?: return
                val sensorType = invocation.getArg(0) as? Int ?: return
                emit(
                    kind = DeviceEnvKind.EMULATOR_SENSOR,
                    operation = DeviceEnvOperation.SENSOR_LIST,
                    originalValue = if (original.isEmpty()) "empty" else "size=${original.size}",
                    params = mapOf("sensorType" to sensorType.toString()),
                )
            }
        })
    }
}
