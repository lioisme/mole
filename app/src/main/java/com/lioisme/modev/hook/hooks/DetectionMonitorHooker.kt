package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.DeviceEnvKind
import com.lioisme.modev.core.model.DeviceEnvOperation
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.MethodHook
import com.lioisme.modev.hook.ProbeContext
import java.io.BufferedReader
import java.io.File

/**
 * 检测行为监控 Hooker(V31):被动监控 root/emulator 检测行为,仅记录不干预。
 *
 * 设计决策:当干预型 Hooker 已覆盖同一 API 时,跳过对应的 monitor hook,
 * 避免双重 hook 开销(RootFileHooker 已覆盖 File.exists,
 * XposedClassHooker 已覆盖 Class.forName 等)。
 */
class DetectionMonitorHooker(
    context: ProbeContext,
) : DeviceEnvBaseHooker(context) {

    override fun install() {
        val switch = config()
        if (switch.detectRoot) {
            // File.exists 由 RootFileHooker 覆盖,跳过 monitorFileExists
            if (!switch.rootFile) {
                monitorFileExists()
            }
            // Class.forName hook 在 LSPosed 下易引发 ClassNotFoundException 传播异常,
            // 跳过 monitorClassForName;root 检测信号已由 File.exists 等覆盖。
        }
        if (switch.detectEmulator) {
            // getStackTrace 由 XposedStackHooker 覆盖,跳过 monitorGetStackTrace
            if (!switch.xposedStack) {
                monitorGetStackTrace()
            }
            // readLine 由 XposedMapsHooker 覆盖,跳过 monitorReadLine
            if (!switch.xposedMaps) {
                monitorReadLine()
            }
            // getDefaultSensor 由 EmulatorSensorHooker 覆盖,跳过 monitorGetDefaultSensor
            if (!switch.emulatorSensor) {
                monitorGetDefaultSensor()
            }
            // getMacAddress 由 EmulatorNetworkHooker 覆盖,跳过 monitorGetMacAddress
            if (!switch.emulatorNetwork) {
                monitorGetMacAddress()
            }
            monitorBuildFields()
        }
    }

    private fun monitorGetStackTrace() {
        val method = runCatching {
            Throwable::class.java.getMethod("getStackTrace")
        }.getOrNull() ?: return
        engine.hook(method, object : MethodHook() {
            override fun after(invocation: Invocation) {
                // 运行时检查:如果干预型 Hooker 已启用,跳过监控(避免双重 Hook)
                if (config().xposedStack) return
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
                // 运行时检查:如果干预型 Hooker 已启用,跳过监控(避免双重 Hook)
                if (config().xposedMaps) return
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
                // 运行时检查:如果干预型 Hooker 已启用,跳过监控(避免双重 Hook)
                if (config().rootFile) return
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
                // 运行时检查:如果干预型 Hooker 已启用,跳过监控(避免双重 Hook)
                if (config().emulatorSensor) return
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
                // 运行时检查:如果干预型 Hooker 已启用,跳过监控(避免双重 Hook)
                if (config().emulatorNetwork) return
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
                    // 运行时检查:如果干预型 Hooker 已启用,跳过监控(避免双重 Hook)
                    if (config().emulatorBuild) return
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
