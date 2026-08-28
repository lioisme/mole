package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.DeviceEnvKind
import com.lioisme.modev.core.model.DeviceEnvOperation
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.MethodHook
import com.lioisme.modev.hook.ProbeContext
import java.io.BufferedReader
import java.io.FileReader

/**
 * /proc/self/maps 扫描干预 Hooker(V31):拦截 BufferedReader.readLine(),
 * 当读取的是 /proc/[pid]/maps 文件时,过滤包含 Xposed/Magisk/Frida 等关键词的行。
 *
 * 设计决策:不再通过反射访问 FileReader 内部字段(fp/path)获取路径,
 * 而是在 hookFileReader 阶段直接从构造器参数获取路径并追踪 reader 实例,
 * 避免不同 Android 版本/OEM ROM 内部字段名不一致导致的反射失败。
 */
class XposedMapsHooker(
    context: ProbeContext,
) : DeviceEnvBaseHooker(context) {

    /** 已知的 maps reader 集合:由 hookFileReader 填充,由 hookReadLine 消费。 */
    private val mapsReaders: MutableSet<BufferedReader> =
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
                // 容量保护:防止长时间运行后 Set 无限增长导致 OOM
                if (mapsReaderPaths.size >= MAX_TRACKED_READER_COUNT) return
                val path = invocation.getArg(0) as? String ?: return
                if (!isProcSelfMaps(path)) return
                // FileReader 构造后 thisObject 是新创建的 FileReader 实例,
                // 但它还不是 BufferedReader。需要在 BufferedReader(Reader) 构造器中
                // 识别此 FileReader 并注册 BufferedReader 实例。
                // 此处仅记录路径标记,由 hookBufferedReaderCtor 消费。
                mapsReaderPaths.add(System.identityHashCode(invocation.thisObject))
            }
        })
    }

    private val mapsReaderPaths: MutableSet<Int> =
        java.util.Collections.synchronizedSet(HashSet())

    private fun hookBufferedReaderCtor() {
        val method = runCatching {
            BufferedReader::class.java.getConstructor(java.io.Reader::class.java)
        }.getOrNull() ?: return
        engine.hook(method, object : MethodHook() {
            override fun after(invocation: Invocation) {
                val inner = invocation.getArg(0) as? java.io.Reader ?: return
                if (inner is FileReader) {
                    // 检查此 FileReader 是否是 maps reader（由 hookFileReader 标记）
                    val isMaps = try {
                        System.identityHashCode(inner) in mapsReaderPaths
                    } catch (_: Exception) {
                        false
                    }
                    if (isMaps) {
                        val reader = invocation.thisObject as? BufferedReader ?: return
                        mapsReaders.add(reader)
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
                val isMaps = try { reader in mapsReaders } catch (_: Exception) { false }
                if (!isMaps) return
                // 读到 null 表示流结束/reader 已关闭,从追踪集合中移除防止泄漏
                if (invocation.result == null) {
                    mapsReaders.remove(reader)
                    return
                }
                if (!config().xposedMaps) return
                val line = invocation.result as? String ?: return
                if (isFilteredLine(line)) {
                    invocation.setResult(null)
                    emit(
                        kind = DeviceEnvKind.XPOSED_MAPS,
                        operation = DeviceEnvOperation.READ_MAPS,
                        originalValue = line,
                        targetPath = PROC_MAPS_PATH,
                        detectionPattern = extractKeyword(line),
                    )
                }
            }
        })
    }

    private fun isProcSelfMaps(path: String): Boolean {
        val canonical = runCatching { java.io.File(path).canonicalPath }.getOrDefault(path)
        return canonical.endsWith("/maps") && canonical.contains("/proc/")
    }

    private fun isFilteredLine(line: String): Boolean {
        val lower = line.lowercase()
        return MAPS_KEYWORDS.any { lower.contains(it) }
    }

    private fun extractKeyword(line: String): String? {
        val lower = line.lowercase()
        return MAPS_KEYWORDS.firstOrNull { lower.contains(it) }
    }

    companion object {
        private const val PROC_MAPS_PATH = "/proc/self/maps"
        private const val MAX_TRACKED_READER_COUNT = 64

        private val MAPS_KEYWORDS = listOf(
            "xposed", "lspd", "lsplant", "edxposed",
            "magisk", "zygisk", "zygisknext",
            "supersu", "superuser", "chainfire",
            "substrate", "frida", "gadget", "modev",
        )
    }
}
