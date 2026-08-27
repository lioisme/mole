package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.DeviceEnvKind
import com.lioisme.modev.core.model.DeviceEnvOperation
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.MethodHook
import com.lioisme.modev.hook.ProbeContext
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

class XposedMapsHooker(
    context: ProbeContext,
) : DeviceEnvBaseHooker(context) {

    private val mapsReaders: MutableSet<BufferedReader> =
        Collections.synchronizedSet(ConcurrentHashMap.newKeySet())

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
                val path = invocation.getArg(0) as? String ?: return
                if (!isProcSelfMaps(path)) return
                val reader = invocation.thisObject as? BufferedReader ?: return
                mapsReaders.add(reader)
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
                    val path = runCatching {
                        val fpField = inner.javaClass.getDeclaredField("fp")
                        fpField.isAccessible = true
                        val fpObj = fpField.get(inner)
                        val pathField = fpObj.javaClass.getDeclaredField("path")
                        pathField.isAccessible = true
                        pathField.get(fpObj) as? String
                    }.getOrNull()
                    if (path != null && isProcSelfMaps(path)) {
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
                val line = invocation.result as? String ?: return
                val reader = invocation.thisObject as? BufferedReader ?: return
                val isMaps = try { reader in mapsReaders } catch (_: Exception) { false }
                if (!isMaps) return
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
        val canonical = runCatching { File(path).canonicalPath }.getOrDefault(path)
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

        private val MAPS_KEYWORDS = listOf(
            "xposed", "lspd", "lsplant", "edxposed",
            "magisk", "zygisk", "zygisknext",
            "supersu", "superuser", "chainfire",
            "substrate", "frida", "gadget", "modev",
        )
    }
}
