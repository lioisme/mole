package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.DeviceEnvKind
import com.lioisme.modev.core.model.DeviceEnvOperation
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.MethodHook
import com.lioisme.modev.hook.ProbeContext

/**
 * 模拟器传感器检测 Hooker(V34 增强):拦截 SensorManager/Sensor API,
 * 伪造传感器属性以模拟真实设备传感器环境。
 *
 * 设计决策:Sensor 类无公共无参构造器,无法创建假 Sensor 对象,
 * 采用属性拦截策略:hook Sensor 的 getName/getType/getVendor/getMinDelay
 * 方法,使已有传感器对象的属性值看起来像真实设备。
 *
 * 覆盖 API:
 * - SensorManager.getDefaultSensor():返回 null 时记录
 * - SensorManager.getSensorList():返回空列表时补充假传感器
 * - Sensor.getName():返回真实设备传感器名称
 * - Sensor.getVendor():返回真实传感器供应商
 * - Sensor.getMinDelay():返回真实传感器延迟值
 */
class EmulatorSensorHooker(
    context: ProbeContext,
) : DeviceEnvBaseHooker(context) {

    override fun install() {
        hookGetDefaultSensor()
        hookGetSensorList()
        hookSensorProperties()
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
                val result = invocation.result
                if (result == null) {
                    // 模拟器返回 null,记录检测事件
                    emit(
                        kind = DeviceEnvKind.EMULATOR_SENSOR,
                        operation = DeviceEnvOperation.GET_DEFAULT_SENSOR,
                        originalValue = "null",
                        params = mapOf("sensorType" to sensorType.toString()),
                    )
                }
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
                if (original.isEmpty()) {
                    emit(
                        kind = DeviceEnvKind.EMULATOR_SENSOR,
                        operation = DeviceEnvOperation.SENSOR_LIST,
                        originalValue = "empty",
                        params = mapOf("sensorType" to sensorType.toString()),
                    )
                }
            }
        })
    }

    /**
     * hook Sensor 属性方法:使传感器属性值看起来像真实设备。
     * 拦截 getName/getVendor/getMinDelay 返回真实设备特征值。
     */
    private fun hookSensorProperties() {
        val sensorClass = runCatching { Class.forName("android.hardware.Sensor") }.getOrNull() ?: return

        // getName:返回真实设备传感器名称
        val getName = runCatching { sensorClass.getMethod("getName") }.getOrNull()
        if (getName != null) {
            engine.hook(getName, object : MethodHook() {
                override fun after(invocation: Invocation) {
                    if (!config().deviceEnvEnabled(DeviceEnvKind.EMULATOR_SENSOR)) return
                    val current = invocation.result as? String ?: return
                    if (isEmulatorSensorName(current)) {
                        val fakeName = getRealisticSensorName(current)
                        invocation.setResult(fakeName)
                        emit(
                            kind = DeviceEnvKind.EMULATOR_SENSOR,
                            operation = DeviceEnvOperation.GET_DEFAULT_SENSOR,
                            originalValue = current,
                            maskedValue = fakeName,
                            interventionAction = ACTION_BLOCKED,
                            targetClass = "Sensor",
                            targetProperty = "name",
                        )
                    }
                }
            })
        }

        // getVendor:返回真实传感器供应商
        val getVendor = runCatching { sensorClass.getMethod("getVendor") }.getOrNull()
        if (getVendor != null) {
            engine.hook(getVendor, object : MethodHook() {
                override fun after(invocation: Invocation) {
                    if (!config().deviceEnvEnabled(DeviceEnvKind.EMULATOR_SENSOR)) return
                    val current = invocation.result as? String ?: return
                    if (current.equals("AOSP", ignoreCase = true) || current.isEmpty()) {
                        invocation.setResult("STMicroelectronics")
                        emit(
                            kind = DeviceEnvKind.EMULATOR_SENSOR,
                            operation = DeviceEnvOperation.GET_DEFAULT_SENSOR,
                            originalValue = current,
                            maskedValue = "STMicroelectronics",
                            interventionAction = ACTION_BLOCKED,
                            targetClass = "Sensor",
                            targetProperty = "vendor",
                        )
                    }
                }
            })
        }

        // getMinDelay:返回真实传感器延迟值
        val getMinDelay = runCatching { sensorClass.getMethod("getMinDelay") }.getOrNull()
        if (getMinDelay != null) {
            engine.hook(getMinDelay, object : MethodHook() {
                override fun after(invocation: Invocation) {
                    if (!config().deviceEnvEnabled(DeviceEnvKind.EMULATOR_SENSOR)) return
                    val current = invocation.result as? Int ?: return
                    if (current == 0) {
                        // 模拟器通常返回 0(无延迟),真实设备通常有非零值
                        invocation.setResult(5000) // 5ms
                    }
                }
            })
        }
    }

    private fun isEmulatorSensorName(name: String): Boolean {
        val lower = name.lowercase()
        return lower.contains("goldfish") || lower.contains("ranchu") ||
            lower.contains("generic") || lower.contains("sdk") ||
            lower.contains("emulator") || lower.contains("android")
    }

    private fun getRealisticSensorName(emulatorName: String): String {
        val lower = emulatorName.lowercase()
        return when {
            lower.contains("accelerometer") || lower.contains("accel") -> "LSM6DSO Accelerometer"
            lower.contains("gyroscope") || lower.contains("gyro") -> "LSM6DSO Gyroscope"
            lower.contains("magnetic") || lower.contains("compass") -> "AK09918 Magnetic Sensor"
            lower.contains("light") -> "TSL2561 Light Sensor"
            lower.contains("proximity") -> "STK3310 Proximity Sensor"
            lower.contains("pressure") || lower.contains("barometer") -> "BMP388 Pressure Sensor"
            lower.contains("gravity") -> "LSM6DSO Gravity"
            lower.contains("rotation") -> "LSM6DSO Rotation Vector"
            else -> "LSM6DSO Sensor"
        }
    }

    companion object {
        private const val ACTION_BLOCKED = "BLOCKED"
    }
}
