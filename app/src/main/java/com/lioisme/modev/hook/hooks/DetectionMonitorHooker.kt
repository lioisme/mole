package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.DeviceEnvKind
import com.lioisme.modev.core.model.DeviceEnvOperation
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.MethodHook
import com.lioisme.modev.hook.ProbeContext
import java.io.BufferedReader
import java.io.File

class DetectionMonitorHooker(
    context: ProbeContext,
) : DeviceEnvBaseHooker(context) {

    override fun install() {
        val switch = config()
        if (switch.detectRoot) {
            monitorClassForName()
            monitorFileExists()
        }
        if (switch.detectEmulator) {
            monitorGetStackTrace()
            monitorReadLine()
            monitorGetDefaultSensor()
            monitorGetMacAddress()
            monitorBuildFields()
        }
    }

    private fun monitorClassForName() {
        val method = runCatching {
            Class::class.java.getMethod("forName", String::class.java)
        }.getOrNull() ?: return
        engine.hook(method, object : MethodHook() {
            override fun after(invocation: Invocation) {
                val name = invocation.getArg(0) as? String ?: return
                val lower = name.lowercase()
                val matched = DETECT_KEYWORDS.firstOrNull { lower.contains(it) } ?: return
                emit(
                    kind = DeviceEnvKind.DETECT_ROOT,
                    operation = DeviceEnvOperation.ROOT_CHECK_API,
                    originalValue = name,
                    targetClass = name,
                    detectionPattern = matched,
                    intervention = false,
                )
            }
        })
    }

    private fun monitorGetStackTrace() {
        val method = runCatching {
            Throwable::class.java.getMethod("getStackTrace")
        }.getOrNull() ?: return
        engine.hook(method, object : MethodHook() {
            override fun after(invocation: Invocation) {
                val stack = invocation.result as? Array<StackTraceElement> ?: return
                val hasDetection = stack.any { frame ->
                    val cn = frame.className.lowercase()
                    DETECT_FRAME_KEYWORDS.any { cn.contains(it) }
                }
                if (hasDetection) {
                    emit(
                        kind = DeviceEnvKind.DETECT_EMULATOR,
                        operation = DeviceEnvOperation.EMULATOR_CHECK_BUILD,
                        detectionPattern = "stack-trace-scan",
                        intervention = false,
                    )
                }
            }
        })
    }

    private fun monitorReadLine() {
        val method = runCatching {
            BufferedReader::class.java.getMethod("readLine")
        }.getOrNull() ?: return
        engine.hook(method, object : MethodHook() {
            override fun after(invocation: Invocation) {
                val line = invocation.result as? String ?: return
                val lower = line.lowercase()
                val matched = MAPS_KEYWORDS.firstOrNull { lower.contains(it) } ?: return
                emit(
                    kind = DeviceEnvKind.DETECT_EMULATOR,
                    operation = DeviceEnvOperation.EMULATOR_CHECK_FILE,
                    originalValue = line,
                    targetPath = "/proc/self/maps",
                    detectionPattern = matched,
                    intervention = false,
                )
            }
        })
    }

    private fun monitorFileExists() {
        val method = runCatching {
            File::class.java.getMethod("exists")
        }.getOrNull() ?: return
        engine.hook(method, object : MethodHook() {
            override fun after(invocation: Invocation) {
                val path = (invocation.thisObject as? File)?.path ?: return
                val lower = path.lowercase()
                val matched = ROOT_FILE_KEYWORDS.firstOrNull { lower.contains(it) } ?: return
                emit(
                    kind = DeviceEnvKind.DETECT_ROOT,
                    operation = DeviceEnvOperation.ROOT_CHECK_FILE,
                    targetPath = path,
                    detectionPattern = matched,
                    intervention = false,
                )
            }
        })
    }

    private fun monitorGetDefaultSensor() {
        val clazz = runCatching { Class.forName("android.hardware.SensorManager") }.getOrNull() ?: return
        val method = runCatching {
            clazz.getMethod("getDefaultSensor", Int::class.javaPrimitiveType)
        }.getOrNull() ?: return
        engine.hook(method, object : MethodHook() {
            override fun after(invocation: Invocation) {
                val result = invocation.result
                val params = mapOf(
                    "sensorType" to (invocation.getArg(0) as? Int).toString(),
                    "returned" to (result != null).toString(),
                )
                emit(
                    kind = DeviceEnvKind.DETECT_EMULATOR,
                    operation = DeviceEnvOperation.EMULATOR_CHECK_PROP,
                    params = params,
                    intervention = false,
                )
            }
        })
    }

    private fun monitorGetMacAddress() {
        val clazz = runCatching { Class.forName("android.net.wifi.WifiInfo") }.getOrNull() ?: return
        val method = runCatching { clazz.getMethod("getMacAddress") }.getOrNull() ?: return
        engine.hook(method, object : MethodHook() {
            override fun after(invocation: Invocation) {
                val mac = invocation.result as? String ?: return
                emit(
                    kind = DeviceEnvKind.DETECT_EMULATOR,
                    operation = DeviceEnvOperation.EMULATOR_CHECK_BUILD,
                    originalValue = mac,
                    detectionPattern = "mac-check",
                    intervention = false,
                )
            }
        })
    }

    private fun monitorBuildFields() {
        val clazz = runCatching { Class.forName("android.os.Build") }.getOrNull() ?: return
        for (field in BUILD_FIELDS) {
            val method = runCatching { clazz.getMethod(field) }.getOrNull() ?: continue
            engine.hook(method, object : MethodHook() {
                override fun after(invocation: Invocation) {
                    val value = invocation.result as? String ?: return
                    emit(
                        kind = DeviceEnvKind.DETECT_EMULATOR,
                        operation = DeviceEnvOperation.EMULATOR_CHECK_BUILD,
                        originalValue = value,
                        targetProperty = field,
                        detectionPattern = "build-check",
                        intervention = false,
                    )
                }
            })
        }
    }

    companion object {
        private val DETECT_KEYWORDS = listOf(
            "xposed", "lspd", "lsplant", "edxposed", "de.robv",
            "su", "busybox", "magisk", "superuser",
        )

        private val DETECT_FRAME_KEYWORDS = listOf(
            "xposed", "lspd", "lsplant", "de.robv",
            "frida", "gadget", "substrate",
        )

        private val MAPS_KEYWORDS = listOf(
            "xposed", "lspd", "lsplant", "edxposed",
            "magisk", "zygisk", "zygisknext",
            "supersu", "superuser", "chainfire",
            "substrate", "frida", "gadget",
        )

        private val ROOT_FILE_KEYWORDS = listOf(
            "su", "busybox", "magisk", "superuser", "supersu",
        )

        private val BUILD_FIELDS = listOf(
            "getFINGERPRINT", "getMODEL", "getPRODUCT", "getMANUFACTURER",
        )
    }
}
