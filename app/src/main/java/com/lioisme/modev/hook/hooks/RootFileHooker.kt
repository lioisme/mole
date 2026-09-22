package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.DeviceEnvKind
import com.lioisme.modev.core.model.DeviceEnvOperation
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.MethodHook
import com.lioisme.modev.hook.ProbeContext

/**
 * 文件存在性检测干预 Hooker(V31):统一拦截 java.io.File 的 exists/length/canRead,
 * 同时覆盖 root 路径与模拟器路径检测,避免同一方法被多个 hooker 重复 hook。
 *
 * 合并自原 RootFileHooker + EmulatorFileHooker 的 File.exists() hook,
 * 减少热路径回调开销(三合一→单一)。
 *
 * 路径匹配优化:使用 HashSet 存储路径后缀,O(1) 查找替代 List 遍历。
 */
class RootFileHooker(
    context: ProbeContext,
) : DeviceEnvBaseHooker(context) {

    override fun install() {
        hookFileExists()
        hookFileLength()
        hookFileCanRead()
        hookFileCanWrite()
        hookFileCanExecute()
    }

    private fun hookFileExists() {
        val clazz = runCatching { Class.forName("java.io.File") }.getOrNull() ?: return
        val method = runCatching { clazz.getMethod("exists") }.getOrNull() ?: return

        engine.hook(method, object : MethodHook() {
            override fun before(invocation: Invocation) {
                // V43 T2:File.exists 是宿主最热探针之一——两分支门控先行,
                // lowercase 分配仅在任一干预开启时发生(V2.7)
                val rootOn = featureOn(DeviceEnvKind.ROOT_FILE)
                val emuOn = featureOn(DeviceEnvKind.EMULATOR_FILE)
                if (!rootOn && !emuOn) return
                val path = (invocation.thisObject as? java.io.File)?.path ?: return
                val lower = path.lowercase()

                // Root 路径检测干预
                if (rootOn) {
                    if (matchesRootPath(lower)) {
                        val original = runCatching { invocation.result }.getOrNull()?.toString()
                        invocation.setResult(false)
                        emit(
                            kind = DeviceEnvKind.ROOT_FILE,
                            operation = DeviceEnvOperation.FILE_EXISTS,
                            originalValue = original,
                            maskedValue = "false",
                            targetPath = path,
                            detectionPattern = ROOT_PATTERN,
                        )
                        return
                    }
                }

                // 模拟器路径检测干预
                if (emuOn) {
                    val emuSuffix = matchesEmulatorPath(lower)
                    if (emuSuffix != null) {
                        invocation.setResult(false)
                        val op = when {
                            emuSuffix.startsWith("/dev/") -> DeviceEnvOperation.DEV_FILE_EXISTS
                            emuSuffix.startsWith("/system/lib") || emuSuffix.startsWith("/system/bin") -> DeviceEnvOperation.LIB_FILE_EXISTS
                            else -> DeviceEnvOperation.SYS_FILE_EXISTS
                        }
                        emit(
                            kind = DeviceEnvKind.EMULATOR_FILE,
                            operation = op,
                            originalValue = "true",
                            maskedValue = "false",
                            targetPath = path,
                            detectionPattern = emuSuffix,
                        )
                        return
                    }
                }
            }
        })
    }

    private fun hookFileLength() {
        val clazz = runCatching { Class.forName("java.io.File") }.getOrNull() ?: return
        val method = runCatching { clazz.getMethod("length") }.getOrNull() ?: return

        engine.hook(method, object : MethodHook() {
            override fun before(invocation: Invocation) {
                if (!featureOn(DeviceEnvKind.ROOT_FILE)) return
                val path = (invocation.thisObject as? java.io.File)?.path ?: return
                if (!matchesRootPath(path.lowercase())) return
                invocation.setResult(0L)
                emit(
                    kind = DeviceEnvKind.ROOT_FILE,
                    operation = DeviceEnvOperation.FILE_LENGTH,
                    originalValue = null,
                    maskedValue = "0",
                    targetPath = path,
                    detectionPattern = ROOT_PATTERN,
                )
            }
        })
    }

    private fun hookFileCanRead() {
        val clazz = runCatching { Class.forName("java.io.File") }.getOrNull() ?: return
        val method = runCatching { clazz.getMethod("canRead") }.getOrNull() ?: return

        engine.hook(method, object : MethodHook() {
            override fun before(invocation: Invocation) {
                if (!featureOn(DeviceEnvKind.ROOT_FILE)) return
                val path = (invocation.thisObject as? java.io.File)?.path ?: return
                if (!matchesRootPath(path.lowercase())) return
                invocation.setResult(false)
                emit(
                    kind = DeviceEnvKind.ROOT_FILE,
                    operation = DeviceEnvOperation.FILE_READABLE,
                    originalValue = null,
                    maskedValue = "false",
                    targetPath = path,
                    detectionPattern = ROOT_PATTERN,
                )
            }
        })
    }

    private fun hookFileCanWrite() {
        val clazz = runCatching { Class.forName("java.io.File") }.getOrNull() ?: return
        val method = runCatching { clazz.getMethod("canWrite") }.getOrNull() ?: return

        engine.hook(method, object : MethodHook() {
            override fun before(invocation: Invocation) {
                if (!featureOn(DeviceEnvKind.ROOT_FILE)) return
                val path = (invocation.thisObject as? java.io.File)?.path ?: return
                if (!matchesRootPath(path.lowercase())) return
                invocation.setResult(false)
                emit(
                    kind = DeviceEnvKind.ROOT_FILE,
                    operation = DeviceEnvOperation.FILE_READABLE,
                    originalValue = null,
                    maskedValue = "false",
                    targetPath = path,
                    detectionPattern = ROOT_PATTERN,
                )
            }
        })
    }

    private fun hookFileCanExecute() {
        val clazz = runCatching { Class.forName("java.io.File") }.getOrNull() ?: return
        val method = runCatching { clazz.getMethod("canExecute") }.getOrNull() ?: return

        engine.hook(method, object : MethodHook() {
            override fun before(invocation: Invocation) {
                if (!featureOn(DeviceEnvKind.ROOT_FILE)) return
                val path = (invocation.thisObject as? java.io.File)?.path ?: return
                if (!matchesRootPath(path.lowercase())) return
                invocation.setResult(false)
                emit(
                    kind = DeviceEnvKind.ROOT_FILE,
                    operation = DeviceEnvOperation.FILE_READABLE,
                    originalValue = null,
                    maskedValue = "false",
                    targetPath = path,
                    detectionPattern = ROOT_PATTERN,
                )
            }
        })
    }

    private fun matchesRootPath(lower: String): Boolean =
        DeviceEnvPathMatcher.matchesRootPath(lower)

    private fun matchesEmulatorPath(lower: String): String? =
        DeviceEnvPathMatcher.matchesEmulatorPath(lower)

    companion object {

        private const val ROOT_PATTERN = "root-file"
    }
}
