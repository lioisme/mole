package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.DeviceEnvKind
import com.lioisme.modev.core.model.DeviceEnvOperation
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.MethodHook
import com.lioisme.modev.hook.ProbeContext
import java.io.BufferedReader
import java.io.FileReader

/**
 * /proc/net/tcp 行过滤 Hooker(V35 momo 增强):
 * 拦截 BufferedReader.readLine() 当读取 /proc/net/tcp 时,
 * 过滤 Frida gadget 监听端口等可疑连接条目。
 *
 * momo 检测项覆盖:
 * - Frida 默认监听端口 27042/27043 → /proc/net/tcp 中出现对应 local_address
 *
 * 与 ProcStatusHooker/ProcMountInfoHooker/ProcCgroupHooker 同构。
 */
class ProcNetHooker(
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
                if (!isProcNetPath(path)) return
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
                if (!config().procNet) return
                val line = invocation.result as? String ?: return
                if (isFridaPortLine(line)) {
                    invocation.setResult(null)
                    emit(
                        kind = DeviceEnvKind.PROC_NET,
                        operation = DeviceEnvOperation.PROC_NET_READ,
                        originalValue = line,
                        targetPath = PROC_NET_TCP_PATH,
                        detectionPattern = "frida-port",
                        interventionAction = ACTION_BLOCKED,
                    )
                }
            }
        })
    }

    /**
     * 判断 /proc/net/tcp 行是否包含 Frida 默认监听端口。
     * local_address 格式: "IP:PORT", port 为十六进制。
     * Frida 默认端口: 27042 (0x69A2), 27043 (0x69A3)
     */
    private fun isFridaPortLine(line: String): Boolean {
        if (line.isBlank() || line.startsWith("sl")) return false
        val parts = line.trim().split("\\s+".toRegex())
        if (parts.size < 2) return false
        val localAddress = parts[1]
        val portHex = localAddress.substringAfterLast(':', "")
        if (portHex.isEmpty()) return false
        val portValue = runCatching { portHex.toInt(16) }.getOrNull() ?: return false
        return FRIDA_PORTS.contains(portValue)
    }

    private fun isProcNetPath(path: String): Boolean {
        val canonical = runCatching { java.io.File(path).canonicalPath }.getOrDefault(path)
        return canonical.contains("/proc/") && (
            canonical.endsWith("/net/tcp") ||
                canonical.endsWith("/net/tcp6")
            )
    }

    companion object {
        private const val PROC_NET_TCP_PATH = "/proc/net/tcp"
        private const val ACTION_BLOCKED = "BLOCKED"
        private const val MAX_TRACKED = 64

        /** Frida 默认监听端口集合(十进制)。 */
        private val FRIDA_PORTS = setOf(27042, 27043)
    }
}
