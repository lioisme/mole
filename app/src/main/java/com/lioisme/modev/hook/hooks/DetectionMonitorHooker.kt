package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.DeviceEnvKind
import com.lioisme.modev.core.model.DeviceEnvOperation
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.MethodHook
import com.lioisme.modev.hook.ProbeContext
import com.lioisme.modev.hook.SharedMethodBroadcast
import java.io.File

/**
 * 检测行为监控 Hooker:被动监控 root/emulator 检测行为,仅记录不干预。
 *
 * 设计决策:当干预型 Hooker 已覆盖同一 API 时,让位判定改**运行时**(读当前配置),
 * V44 T2.6 修复旧「安装期一次性快照」缺陷(DETECT_* 后开不补装 = 永不监控);
 * 让位成本仅一次 featureOn volatile 读。
 *
 * V44 T2.1 收编:
 * - readLine 监控改经 [ProcScanBroadcaster] 行处理器(旧独立 readLine 注册与
 *   XposedMaps 干预在 Vector 下互相遮蔽的根因消除);
 * - Throwable.getStackTrace 监控与 [XposedStackHooker] 干预并轨 [SharedMethodBroadcast]
 *   单一注册(旧双注册形态;子链运行时让位判定替代安装期判定)。
 */
class DetectionMonitorHooker(
    context: ProbeContext,
    private val procScan: ProcScanBroadcaster,
) : DeviceEnvBaseHooker(context), ProcScanBroadcaster.Handler {

    override val kind: DeviceEnvKind = DeviceEnvKind.DETECT_EMULATOR

    override val paths: Set<ProcScanBroadcaster.ProcPath> =
        setOf(ProcScanBroadcaster.ProcPath.MAPS)

    override fun install() {
        // /proc/self/maps 行监控(V44 收编为单一广播行处理器;XPOSED_MAPS 干预启用时让位)。
        procScan.addHandler(this)
        applyInstallDecisions()
        // V44 T2.6(审计 P14):装钩决策由「安装期一次性快照」改为「配置变更重评估」——
        // 旧形态若装配时 DETECT_* 关闭,后续开启永不补装(静默失效:开关亮着却零记录)。
        // 判定纯读当前配置,重评估幂等(各安装点自带 installed 守卫),成本 = 一次通知。
        configWatcher.register { applyInstallDecisions() }
    }

    /**
     * 装钩决策:按**当前**有效配置决定各监控点是否安装。
     * 让位判定(干预型已覆盖同一 API)同样按当前配置,故干预关闭后监控可补装。
     */
    private fun applyInstallDecisions() {
        if (featureOn(DeviceEnvKind.DETECT_ROOT)) {
            // File.exists 由 RootFileHooker 覆盖,跳过 monitorFileExists
            if (!featureOn(DeviceEnvKind.ROOT_FILE)) monitorFileExists()
            // Class.forName hook 在 LSPosed 下易引发 ClassNotFoundException 传播异常,
            // 跳过 monitorClassForName;root 检测信号已由 File.exists 等覆盖。
        }
        if (featureOn(DeviceEnvKind.DETECT_EMULATOR)) {
            monitorGetStackTrace()
            // getDefaultSensor 由 EmulatorSensorHooker 覆盖,跳过 monitorGetDefaultSensor
            if (!featureOn(DeviceEnvKind.EMULATOR_SENSOR)) monitorGetDefaultSensor()
            // getMacAddress 由 EmulatorNetworkHooker 覆盖,跳过 monitorGetMacAddress
            if (!featureOn(DeviceEnvKind.EMULATOR_NETWORK)) monitorGetMacAddress()
            monitorBuildFieldReflection()
        }
    }

    /**
     * maps 行监控:经协调器分发,运行时让位判定——
     * XPOSED_MAPS 干预启用时其已记录被滤行,监控不重复申报。
     */
    override fun onLine(invocation: Invocation, path: ProcScanBroadcaster.ProcPath, line: String) {
        if (featureOn(DeviceEnvKind.XPOSED_MAPS)) return
        if (!featureOn(DeviceEnvKind.DETECT_EMULATOR)) return
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

    /** 各监控点安装幂等标记(V44 T2.6:装钩决策可被配置变更重评估,必须幂等)。 */
    private var stackTraceInstalled = false
    private var fileExistsInstalled = false
    private var defaultSensorInstalled = false
    private var macAddressInstalled = false
    private var buildFieldsInstalled = false

    @Synchronized
    private fun monitorGetStackTrace() {
        if (stackTraceInstalled) return
        stackTraceInstalled = true
        val method = runCatching {
            Throwable::class.java.getMethod("getStackTrace")
        }.getOrNull() ?: return
        // V44 T2.1:getStackTrace 与 XposedStack 干预共享单一广播注册(子链各判自身门控)
        broadcast.subscribe(
            method,
            object : SharedMethodBroadcast.Sub {
                override fun after(invocation: Invocation, result: Any?) {
                    if (!featureOn(DeviceEnvKind.DETECT_EMULATOR)) return
                    // 运行时让位:干预型订阅(栈过滤)启用时其已改写并记录,监控不重复申报
                    if (featureOn(DeviceEnvKind.XPOSED_STACK)) return
                    val stack = result as? Array<StackTraceElement> ?: return
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
            },
        )
    }

    @Synchronized
    private fun monitorFileExists() {
        if (fileExistsInstalled) return
        fileExistsInstalled = true
        val method = runCatching {
            File::class.java.getMethod("exists")
        }.getOrNull() ?: return
        engine.hook(method, object : MethodHook() {
            override fun after(invocation: Invocation) {
                // 运行时检查:如果干预型 Hooker 已启用,跳过监控(避免双重 Hook)
                if (featureOn(DeviceEnvKind.ROOT_FILE)) return
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

    @Synchronized
    private fun monitorGetDefaultSensor() {
        if (defaultSensorInstalled) return
        defaultSensorInstalled = true
        val clazz = runCatching { Class.forName("android.hardware.SensorManager") }.getOrNull() ?: return
        val method = runCatching {
            clazz.getMethod("getDefaultSensor", Int::class.javaPrimitiveType)
        }.getOrNull() ?: return
        engine.hook(method, object : MethodHook() {
            override fun after(invocation: Invocation) {
                // 运行时检查:如果干预型 Hooker 已启用,跳过监控(避免双重 Hook)
                if (featureOn(DeviceEnvKind.EMULATOR_SENSOR)) return
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

    @Synchronized
    private fun monitorGetMacAddress() {
        if (macAddressInstalled) return
        macAddressInstalled = true
        val clazz = runCatching { Class.forName("android.net.wifi.WifiInfo") }.getOrNull() ?: return
        val method = runCatching { clazz.getMethod("getMacAddress") }.getOrNull() ?: return
        // V43 T3/D-4:getMacAddress 与 EmulatorNetwork 干预共享单一广播注册点
        broadcast.subscribe(
            method,
            object : SharedMethodBroadcast.Sub {
                override fun after(invocation: Invocation, result: Any?) {
                    // 运行时检查:如果干预型订阅(伪装)已启用,其替换结果会经子链
                    // 级联可见——监控不再重复申报被伪装后的值
                    if (featureOn(DeviceEnvKind.EMULATOR_NETWORK)) return
                    val mac = result as? String ?: return
                    emit(
                        kind = DeviceEnvKind.DETECT_EMULATOR,
                        operation = DeviceEnvOperation.EMULATOR_CHECK_BUILD,
                        originalValue = mac,
                        detectionPattern = "mac-check",
                        intervention = false,
                    )
                }
            },
        )
    }

    /**
     * Build 字段反射读取监控。
     *
     * **旧实装是一条永不生效的死钩**:它用 `clazz.getMethod(field)` 找
     * `getFINGERPRINT` / `getMODEL` / `getPRODUCT` / `getMANUFACTURER` —— 而
     * `android.os.Build` 的这些成员是 `public static final` **字段,根本没有同名 getter**
     * ⇒ 四次 `NoSuchMethodException` 全被 `?: continue` 咽掉 ⇒ 零安装、零记录,
     * 而 AGENTS 写着「已监控 Build 字段」(与 V49 实锤的 `File.readText` / @hide 方法同族)。
     *
     * 静态字段直读(`GETSTATIC`)在 ART 上不可钩,这是物理上限;真正可观测的是
     * **反射读**这条通路,故本实装改钩 `java.lang.reflect.Field.get` 并只筛 `Build` 的字段。
     * 直读路径不申报 —— 由 `EmulatorBuildHooker` 的字段改写覆盖(干预面),不伪造观测面。
     *
     * 热路径护栏:`Field.get` 是极高频反射入口,门控与「是否 Build 字段」判定必须在
     * 取任何值之前完成(V44 T2.2 热路径先行规约),非 Build 字段的成本 = 两次引用比较。
     */
    @Synchronized
    private fun monitorBuildFieldReflection() {
        if (buildFieldsInstalled) return
        buildFieldsInstalled = true
        val method = runCatching {
            java.lang.reflect.Field::class.java.getMethod("get", Any::class.java)
        }.getOrNull()
        if (method == null) {
            // 解析失败必须点名(V49 收口:静默空装 = 文档替死钩撒谎)
            unresolvedHookPoints += "java.lang.reflect.Field#get/get"
            return
        }
        engine.hook(method, object : MethodHook() {
            override fun before(invocation: Invocation) {
                if (!featureOn(DeviceEnvKind.DETECT_EMULATOR)) return
                val field = invocation.thisObject as? java.lang.reflect.Field ?: return
                if (field.declaringClass != android.os.Build::class.java) return
                if (field.type != String::class.java) return
                val name = field.name
                if (name !in BUILD_FIELDS) return
                emit(
                    kind = DeviceEnvKind.DETECT_EMULATOR,
                    operation = DeviceEnvOperation.EMULATOR_CHECK_BUILD,
                    targetProperty = name,
                    detectionPattern = "build-reflect",
                    intervention = false,
                    params = mapOf("via" to "Field.get"),
                )
            }
        })
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

        /**
         * 参与反射读监控的 `android.os.Build` **字段名**(V50 更正:旧列表写的是
         * `getFINGERPRINT` 之类的假 getter 名,而 Build 上不存在这些方法 ⇒ 死钩)。
         * 只列字符串型身份字段;`Build` 其余字段(int/long/布尔)不是模拟器指纹的主信号。
         */
        private val BUILD_FIELDS = setOf(
            "FINGERPRINT", "MODEL", "PRODUCT", "MANUFACTURER",
            "BRAND", "DEVICE", "BOARD", "TAGS", "USER", "HOST", "ID", "DISPLAY", "SERIAL",
        )
    }
}
