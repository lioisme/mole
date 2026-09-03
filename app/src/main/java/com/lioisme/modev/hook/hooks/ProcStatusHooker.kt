package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.DeviceEnvKind
import com.lioisme.modev.core.model.DeviceEnvOperation
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.MethodHook
import com.lioisme.modev.hook.ProbeContext
import java.io.BufferedReader
import java.io.FileReader

/**
 * /proc/self/status 行过滤 Hooker(V35 momo 增强):
 * 拦截 BufferedReader.readLine() 当读取 /proc/[pid]/status 时,
 * 过滤 TracerPid (Frida 检测) 等敏感行。
 *
 * momo 检测项覆盖:
 * - TracerPid != 0 → 检测到调试器 attach (Frida/gdb)
 * - VmpTrace/VMP trace 相关条目
 *
 * 与 XposedMapsHooker 同构:FileReader 构造 → BufferedReader 标记 → readLine 过滤。
 */
class ProcStatusHooker(
    context: ProbeContext,
) : DeviceEnvBaseHooker(context) {

    private val trackedReaders: MutableSet<BufferedReader> =
        java.util.Collections.synchronizedSet(HashSet())

    private val trackedFileReaders: MutableSet<Int> =
        java.util.Collections.synchronizedSet(HashSet())

    override fun install() {
        hookFileReader()
        hookBufferedReaderCtor()
        hookReadLine()
    }

    private fun hookFileReader() {
        val method = runCatching {
            FileReader::class.java.getConstructor(String::class.java)
        }.getOrNull() ?: return
        engine.hook(method, object : MethodHook() {
            override fun after(invocation: Invocation) {
                if (trackedFileReaders.size >= MAX_TRACKED) return
                val path = invocation.getArg(0) as? String ?: return
                if (!isProcStatusPath(path)) return
                trackedFileReaders.add(System.identityHashCode(invocation.thisObject))
            }
        })
    }

    private fun hookBufferedReaderCtor() {
        val method = runCatching {
            BufferedReader::class.java.getConstructor(java.io.Reader::class.java)
        }.getOrNull() ?: return
        engine.hook(method, object : MethodHook() {
            override fun after(invocation: Invocation) {
                val inner = invocation.getArg(0) as? java.io.Reader ?: return
                if (inner is FileReader) {
                    val isTracked = try {
                        System.identityHashCode(inner) in trackedFileReaders
                    } catch (_: Exception) { false }
                    if (isTracked) {
                        trackedReaders.add(invocation.thisObject as? BufferedReader ?: return)
                    }
                }
            }
        })
    }

    private fun hookReadLine() {
        val method = runCatching {
            BufferedReader::class.java.getMethod("readLine")
        }.getOrNull() ?: return
        engine.hook(method, object : MethodHook() {
            override fun after(invocation: Invocation) {
                val reader = invocation.thisObject as? BufferedReader ?: return
                val isTracked = try { reader in trackedReaders } catch (_: Exception) { false }
                if (!isTracked) return
                if (invocation.result == null) {
                    trackedReaders.remove(reader)
                    return
                }
                if (!config().procStatus) return
                val line = invocation.result as? String ?: return
                if (line.startsWith("TracerPid:")) {
                    val pidValue = line.substringAfter(":").trim()
                    if (pidValue != "0") {
                        invocation.setResult("TracerPid:\t0")
                        emit(
                            kind = DeviceEnvKind.PROC_STATUS,
                            operation = DeviceEnvOperation.PROC_STATUS_READ,
                            originalValue = line,
                            maskedValue = "TracerPid:\t0",
                            targetPath = PROC_STATUS_PATH,
                            detectionPattern = "tracer-pid",
                            interventionAction = ACTION_BLOCKED,
                        )
                    }
                }
            }
        })
    }

    private fun isProcStatusPath(path: String): Boolean {
        val canonical = runCatching { java.io.File(path).canonicalPath }.getOrDefault(path)
        return canonical.contains("/proc/") && canonical.endsWith("/status")
    }

    companion object {
        private const val PROC_STATUS_PATH = "/proc/self/status"
        private const val ACTION_BLOCKED = "BLOCKED"
        private const val MAX_TRACKED = 64
    }
}
