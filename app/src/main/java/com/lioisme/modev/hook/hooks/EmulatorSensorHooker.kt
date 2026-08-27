package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.DeviceEnvKind
import com.lioisme.modev.core.model.DeviceEnvOperation
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.MethodHook
import com.lioisme.modev.hook.ProbeContext

class EmulatorSensorHooker(
    context: ProbeContext,
) : DeviceEnvBaseHooker(context) {

    private val fakeSensors = HashMap<Int, Any>()

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
                if (invocation.result != null) return
                val sensorType = invocation.getArg(0) as? Int ?: return
                val fake = getOrCreateFakeSensor(sensorType)
                invocation.setResult(fake)
                emit(
                    kind = DeviceEnvKind.EMULATOR_SENSOR,
                    operation = DeviceEnvOperation.GET_DEFAULT_SENSOR,
                    originalValue = "null",
                    maskedValue = "fake",
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
                if (original.isNotEmpty()) return
                val sensorType = invocation.getArg(0) as? Int ?: return
                val fake = getOrCreateFakeSensor(sensorType)
                invocation.setResult(listOf(fake))
                emit(
                    kind = DeviceEnvKind.EMULATOR_SENSOR,
                    operation = DeviceEnvOperation.SENSOR_LIST,
                    originalValue = "empty",
                    maskedValue = "fake(1)",
                    params = mapOf("sensorType" to sensorType.toString()),
                )
            }
        })
    }

    private fun getOrCreateFakeSensor(type: Int): Any {
        return fakeSensors.getOrPut(type) {
            val sensorClass = runCatching { Class.forName("android.hardware.Sensor") }.getOrNull()
                ?: return@getOrPut Object()
            val constructor = runCatching {
                sensorClass.getDeclaredConstructor().also { it.isAccessible = true }
            }.getOrNull()
            constructor?.newInstance() ?: Object()
        }
    }
}
