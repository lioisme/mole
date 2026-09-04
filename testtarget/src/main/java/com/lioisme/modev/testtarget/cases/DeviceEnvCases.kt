package com.lioisme.modev.testtarget.cases

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.content.Intent
import android.content.IntentFilter
import com.lioisme.modev.testtarget.Tt
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.InputStreamReader

/**
 * L-DEV-* / E-ROOT-* / E-XPP-* / E-EMU-* / E-PRC-*:设备环境触发器 + 自检器。
 * 每个用例读取原始检测值并经 TT_RESULT 输出,供增强二态对照断言。
 */
object DeviceEnvCases {

    private val SU_PATHS = listOf("/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su")
    private val EMU_FILES = listOf("/dev/qemu_pipe", "/dev/goldfish_pipe", "/dev/socket/qemud")

    private fun readLines(path: String, max: Int = 24): List<String> = runCatching {
        BufferedReader(FileReader(path)).use { br ->
            val out = ArrayList<String>(max)
            var i = 0
            while (i < max) {
                val l = br.readLine() ?: break
                out.add(l); i++
            }
            out
        }
    }.getOrDefault(emptyList())

    fun run(ctx: Context, case: String) {
        when (case) {
            // ---- Root 隐藏 ----
            "L-DEV-01", "E-ROOT-01" -> {
                val found = SU_PATHS.filter { File(it).exists() }
                Tt.result(case, "suFiles" to found.joinToString(",").ifEmpty { "none" })
            }
            "L-DEV-02", "E-ROOT-02" -> {
                val r = runCatching {
                    val p = Runtime.getRuntime().exec(arrayOf("which", "su"))
                    BufferedReader(InputStreamReader(p.inputStream)).readLine().also { p.waitFor() }
                }
                Tt.result(case, "whichSu" to (r.getOrNull() ?: if (r.isFailure) "blocked" else "empty"))
            }
            "L-DEV-03", "E-ROOT-03" -> {
                val rootNames = setOf(
                    "com.noshufou.android.su", "eu.chainfire.supersu", "com.kingroot.kinguser",
                    "com.topjohnwu.magisk", "io.github.huskydg.magisk", "org.lsposed.manager",
                )
                val pkgs = ctx.packageManager.getInstalledPackages(0)
                    .mapNotNull { it.packageName }
                    .filter { it in rootNames }
                Tt.result(case, "rootPkgs" to pkgs.joinToString(",").ifEmpty { "none" })
            }
            "L-DEV-04", "E-ROOT-04" -> {
                // Build.TAGS 为静态字段不可 hook;root 指示属性经 System.getProperty 读取
                // (RootPropertyHooker 拦截 ro.build.tags 等 root 指示键)
                val tags = System.getProperty("ro.build.tags") ?: "null"
                Tt.result(case, "tags" to tags, "fieldTags" to Build.TAGS)
            }
            // ---- Xposed 隐藏 ----
            "L-DEV-05", "E-XPP-01" -> {
                // ART 的 Class.forName 走原生解析,可绕过 Java 层 loadClass 钩子;
                // 检测框架两种都用,靶程序双路径触发
                val r1 = runCatching { Class.forName("de.robv.android.xposed.XposedBridge") }
                val r2 = runCatching { ctx.classLoader.loadClass("de.robv.android.xposed.XposedHelpers") }
                val found = listOf(r1, r2).count { it.isSuccess }
                Tt.result(case, "xposedClass" to if (found > 0) "found" else "cnfe")
            }
            "L-DEV-06", "E-XPP-02" -> {
                val frames = Thread.currentThread().stackTrace.map { it.className }
                val dirty = frames.filter { f ->
                    f.contains("de.robv") || f.contains("lspd") || f.contains("lsposed") || f.contains("libxposed")
                }
                Tt.result(case, "stackFrames" to frames.size.toString(), "xposedFrames" to dirty.size.toString())
            }
            "L-DEV-07", "E-XPP-03" -> {
                val lines = readLines("/proc/self/maps", 600)
                val dirty = lines.count { l ->
                    listOf("xposed", "lspd", "lsplant", "edxposed", "magisk", "zygisk", "supersu", "frida", "gadget", "modev")
                        .any { l.contains(it, ignoreCase = true) }
                }
                Tt.result(case, "mapsLines" to lines.size.toString(), "dirtyLines" to dirty.toString())
            }
            // ---- 模拟器伪装 ----
            "L-DEV-08", "E-EMU-01" -> {
                // 反射读取 Build 字段(EmulatorBuildHooker 经 Class.getField 拦截记录;
                // 直接 GETSTATIC 不可 hook)
                val cls = Build::class.java
                fun rf(name: String): String = runCatching {
                    cls.getField(name).get(null)?.toString() ?: "null"
                }.getOrDefault("err")
                Tt.result(
                    case,
                    "fp" to rf("FINGERPRINT").take(48),
                    "model" to rf("MODEL"),
                    "manu" to rf("MANUFACTURER"),
                    "product" to rf("PRODUCT"),
                    "device" to rf("DEVICE"),
                    "board" to rf("BOARD"),
                    "hardware" to rf("HARDWARE"),
                    "brand" to rf("BRAND"),
                )
            }
            "L-DEV-09", "E-EMU-02" -> {
                val found = EMU_FILES.filter { File(it).exists() }
                Tt.result(case, "emuFiles" to found.joinToString(",").ifEmpty { "none" })
            }
            "L-DEV-10", "E-EMU-03" -> {
                val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
                val acc = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
                Tt.result(case, "accSensor" to "${acc?.name ?: "null"}|${acc?.vendor ?: "null"}")
            }
            "L-DEV-11", "E-EMU-04" -> {
                val wifi = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                @Suppress("DEPRECATION")
                val mac = runCatching { wifi.connectionInfo.macAddress }.getOrNull() ?: "null"
                Tt.result(case, "mac" to mac)
            }
            "L-DEV-12", "E-EMU-05" -> {
                val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as android.telephony.TelephonyManager
                @Suppress("DEPRECATION", "MissingPermission")
                val id = runCatching { tm.deviceId }.getOrNull() ?: "denied"
                Tt.result(case, "deviceId" to id)
            }
            "L-DEV-13", "E-EMU-06" -> {
                val cpuinfo = readLines("/proc/cpuinfo").count { it.startsWith("processor") }
                Tt.result(case, "availProcs" to Runtime.getRuntime().availableProcessors().toString(), "cpuinfo" to cpuinfo.toString())
            }
            "L-DEV-14", "E-EMU-07" -> {
                val intent = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                Tt.result(case, "battery" to "$level/$scale")
            }
            // ---- 系统属性 / proc 过滤 ----
            "L-DEV-15", "E-PRC-05" -> {
                val sp = Class.forName("android.os.SystemProperties")
                val get = sp.getMethod("get", String::class.java, String::class.java)
                val dbg = get.invoke(null, "ro.debuggable", "?") as String
                val secure = get.invoke(null, "ro.secure", "?") as String
                Tt.result(case, "roDebuggable" to dbg, "roSecure" to secure)
            }
            "L-DEV-16", "E-PRC-01" -> {
                val status = readLines("/proc/self/status")
                val tracer = status.firstOrNull { it.startsWith("TracerPid") } ?: "absent"
                Tt.result(case, "tracerPid" to tracer.substringAfter(":").trim())
            }
            "L-DEV-17", "E-PRC-02" -> {
                val mount = readLines("/proc/self/mountinfo", 200)
                val dirty = mount.count { it.contains("magisk", true) || it.contains("zygisk", true) }
                Tt.result(case, "mountLines" to mount.size.toString(), "dirtyMount" to dirty.toString())
            }
            "L-DEV-18", "E-PRC-03" -> {
                val cg = readLines("/proc/self/cgroup")
                val dirty = cg.count { it.contains("magisk", true) || it.contains("hidden", true) }
                Tt.result(case, "cgroupLines" to cg.size.toString(), "dirtyCgroup" to dirty.toString())
            }
            "L-DEV-19", "E-PRC-04" -> {
                // 制造脏内容:监听 frida 默认端口 27042,使 /proc/net/tcp 出现可过滤行
                val probe = runCatching { java.net.ServerSocket(27042) }.getOrNull()
                val tcp = readLines("/proc/net/tcp", 200) + readLines("/proc/net/tcp6", 200)
                val frida = tcp.count { it.contains(":69A2", true) || it.contains(":69A3", true) }
                probe?.close()
                Tt.result(case, "tcpLines" to tcp.size.toString(), "fridaPorts" to frida.toString())
            }
            // ---- 检测行为监控(观测型) ----
            "L-DEV-20" -> {
                SU_PATHS.forEach { File(it).exists() }
                runCatching { Runtime.getRuntime().exec(arrayOf("which", "su")) }
                Tt.result(case, "rootProbe" to "done")
            }
            "L-DEV-21" -> {
                Build.FINGERPRINT
                Build.MODEL
                readLines("/proc/self/maps", 600)
                Tt.result(case, "emuProbe" to "done")
            }
            else -> Tt.result(case, "error" to "unknown-case")
        }
    }
}
