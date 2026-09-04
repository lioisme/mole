package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.DeviceEnvKind
import com.lioisme.modev.core.model.DeviceEnvOperation
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.MethodHook
import com.lioisme.modev.hook.ProbeContext
import java.io.BufferedReader
import java.io.FileReader

/**
 * /proc/self/cgroup 行过滤 Hooker(V35 momo 增强):
 * 拦截 BufferedReader.readLine() 当读取 /proc/self/cgroup 时,
 * 过滤 Magisk hidden process cgroup 条目。
 *
 * momo 检测项覆盖:
 * - Magisk 进程隐藏 → cgroup 中出现 magisk/zygisk 相关条目
 *
 * 与 ProcStatusHooker/ProcMountInfoHooker 同构。
 */
class ProcCgroupHooker(
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
                if (!isProcCgroupPath(path)) return
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
                if (!featureOn(DeviceEnvKind.PROC_CGROUP)) return
                val line = invocation.result as? String ?: return
                if (isMagiskCgroupLine(line)) {
                    invocation.setResult(null)
                    emit(
                        kind = DeviceEnvKind.PROC_CGROUP,
                        operation = DeviceEnvOperation.PROC_CGROUP_READ,
                        originalValue = line,
                        targetPath = PROC_CGROUP_PATH,
                        detectionPattern = extractCgroupKeyword(line),
                        interventionAction = ACTION_BLOCKED,
                    )
                }
            }
        })
    }

    /** 生效掩码词表(V37 T2.4):内置基线 ∪ 用户追加关键词(extraMaskKeywords)。 */
    private fun maskKeywords(): List<String> =
        if (config().feature.extraMaskKeywords.isEmpty()) {
            MAGISK_CGROUP_KEYWORDS
        } else {
            MAGISK_CGROUP_KEYWORDS + config().feature.extraMaskKeywords.map { it.lowercase() }
        }

    private fun isMagiskCgroupLine(line: String): Boolean {
        val lower = line.lowercase()
        return maskKeywords().any { lower.contains(it) }
    }

    private fun extractCgroupKeyword(line: String): String? {
        val lower = line.lowercase()
        return maskKeywords().firstOrNull { lower.contains(it) }
    }

    private fun isProcCgroupPath(path: String): Boolean {
        val canonical = runCatching { java.io.File(path).canonicalPath }.getOrDefault(path)
        return canonical.endsWith("/cgroup") && canonical.contains("/proc/")
    }

    companion object {
        private const val PROC_CGROUP_PATH = "/proc/self/cgroup"
        private const val ACTION_BLOCKED = "BLOCKED"
        private const val MAX_TRACKED = 64

        private val MAGISK_CGROUP_KEYWORDS = listOf(
            "magisk",
            "zygisk",
            "lspd",
            "lsposed",
        )
    }
}
