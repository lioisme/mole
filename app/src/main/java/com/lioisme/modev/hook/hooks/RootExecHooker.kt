package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.DeviceEnvKind
import com.lioisme.modev.core.model.DeviceEnvOperation
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.MethodHook
import com.lioisme.modev.hook.ProbeContext
import java.io.IOException

/**
 * Root 命令执行干预 Hooker(V31,V34 审计修复):拦截 Runtime.exec(String[]) 和 ProcessBuilder.start(),
 * 当命令包含 su 时阻止执行,防止应用通过命令执行检测 root。
 *
 * 干预机制(V43 T3):before() 中经 setThrowable(IOException) 以契约内异常拦截 su/getenforce/
 * setenforce/getprop/which su 等命令执行——Runtime.exec 与 ProcessBuilder.start 的失败契约
 * 本就是 IOException,宿主既有 catch 分支正常消化(V2.5:旧 setResult(null) 致 waitFor NPE)。
 * 干预结果通过 [DeviceEnvRecord.interventionAction] 记录("BLOCKED"|"DETECTED"|null)。
 */
class RootExecHooker(
    context: ProbeContext,
) : DeviceEnvBaseHooker(context) {

    override fun install() {
        hookRuntimeExec()
        hookProcessBuilderStart()
    }

    private fun hookRuntimeExec() {
        val clazz = runCatching { Class.forName("java.lang.Runtime") }.getOrNull() ?: return

        // exec(String[])
        val stringArrayClass = Array<String>::class.java
        val methodStringArray = runCatching { clazz.getMethod("exec", stringArrayClass) }.getOrNull()
        if (methodStringArray != null) {
            engine.hookThrowing(methodStringArray, object : MethodHook() {
                override fun before(invocation: Invocation) {
                    if (!featureOn(DeviceEnvKind.ROOT_EXEC)) return
                    val command = invocation.getArg(0) as? Array<*> ?: return
                    val firstArg = command.firstOrNull()?.toString() ?: return
                    val cmdName = firstArg.substringAfterLast('/').substringAfterLast('\\')

                    // su 命令拦截
                    if (cmdName.equals("su", ignoreCase = true)) {
                        emitBlocked(invocation, command.joinToString(" "), "su-command")
                        return
                    }
                    // getenforce 拦截 → 返回 "Enforcing"
                    if (cmdName.equals("getenforce", ignoreCase = true)) {
                        emitBlocked(invocation, command.joinToString(" "), "getenforce-command")
                        return
                    }
                    // setenforce 拦截 → 阻止执行
                    if (cmdName.equals("setenforce", ignoreCase = true)) {
                        emitBlocked(invocation, command.joinToString(" "), "setenforce-command")
                        return
                    }
                    // getprop 拦截 → 转发到 System.getProperty
                    if (cmdName.equals("getprop", ignoreCase = true)) {
                        emitBlocked(invocation, command.joinToString(" "), "getprop-command")
                        return
                    }
                    // which/whereis su 拦截
                    if ((cmdName.equals("which", ignoreCase = true) || cmdName.equals("whereis", ignoreCase = true)) &&
                        command.any { it?.toString()?.equals("su", ignoreCase = true) == true }
                    ) {
                        emitBlocked(invocation, command.joinToString(" "), "which-su-command")
                        return
                    }
                }
            })
        }

        // exec(String[], String[])
        val stringArrayStringArrayClass = arrayOf(Array<String>::class.java, Array<String>::class.java)
        val methodStringArrayStringArray = runCatching {
            clazz.getMethod("exec", Array<String>::class.java, Array<String>::class.java)
        }.getOrNull()
        if (methodStringArrayStringArray != null) {
            engine.hookThrowing(methodStringArrayStringArray, object : MethodHook() {
                override fun before(invocation: Invocation) {
                    if (!featureOn(DeviceEnvKind.ROOT_EXEC)) return
                    val command = invocation.getArg(0) as? Array<*> ?: return
                    val firstArg = command.firstOrNull()?.toString() ?: return
                    val cmdName = firstArg.substringAfterLast('/').substringAfterLast('\\')
                    if (isBlockedCommand(cmdName, command)) {
                        emitBlocked(invocation, command.joinToString(" "), "exec-command")
                    }
                }
            })
        }

        // exec(String[], String[], File)
        val methodWithDir = runCatching {
            clazz.getMethod(
                "exec",
                Array<String>::class.java,
                Array<String>::class.java,
                java.io.File::class.java,
            )
        }.getOrNull()
        if (methodWithDir != null) {
            engine.hookThrowing(methodWithDir, object : MethodHook() {
                override fun before(invocation: Invocation) {
                    if (!featureOn(DeviceEnvKind.ROOT_EXEC)) return
                    val command = invocation.getArg(0) as? Array<*> ?: return
                    val firstArg = command.firstOrNull()?.toString() ?: return
                    val cmdName = firstArg.substringAfterLast('/').substringAfterLast('\\')
                    if (isBlockedCommand(cmdName, command)) {
                        val dir = (invocation.getArg(2) as? java.io.File)?.path
                        emit(
                            kind = DeviceEnvKind.ROOT_EXEC,
                            operation = DeviceEnvOperation.EXEC_COMMAND,
                            originalValue = command.joinToString(" "),
                            maskedValue = null,
                            targetClass = "java.lang.Runtime",
                            detectionPattern = ROOT_EXEC_PATTERN,
                            interventionAction = ACTION_BLOCKED,
                            params = buildMap {
                                put("api", "exec-string-array-env-dir")
                                dir?.let { put("workdir", it) }
                            },
                        )
                        invocation.setThrowable(IOException("Command blocked by Modev: root-exec"))
                    }
                }
            })
        }
    }

    /** 判断命令是否应被拦截(su/getenforce/setenforce/getprop/which su)。 */
    private fun isBlockedCommand(cmdName: String, command: Array<*>): Boolean {
        if (cmdName.equals("su", ignoreCase = true)) return true
        if (cmdName.equals("getenforce", ignoreCase = true)) return true
        if (cmdName.equals("setenforce", ignoreCase = true)) return true
        if (cmdName.equals("getprop", ignoreCase = true)) return true
        if ((cmdName.equals("which", ignoreCase = true) || cmdName.equals("whereis", ignoreCase = true)) &&
            command.any { it?.toString()?.equals("su", ignoreCase = true) == true }
        ) {
            return true
        }
        return false
    }

    private fun emitBlocked(invocation: Invocation, command: String, pattern: String) {
        emit(
            kind = DeviceEnvKind.ROOT_EXEC,
            operation = DeviceEnvOperation.EXEC_COMMAND,
            originalValue = command,
            maskedValue = null,
            targetClass = "java.lang.Runtime",
            detectionPattern = pattern,
            interventionAction = ACTION_BLOCKED,
        )
        // V43 T3 契约忠实化(V2.5):exec 失败契约 = IOException(宿主 catch 分支);
        // 旧 setResult(null) 令宿主 `.waitFor()`/流读取直接 NPE = 模块引入的宿主崩溃
        invocation.setThrowable(IOException("Command blocked by Modev: $pattern"))
    }

    private fun hookProcessBuilderStart() {
        val clazz = runCatching { Class.forName("java.lang.ProcessBuilder") }.getOrNull() ?: return
        val method = runCatching { clazz.getMethod("start") }.getOrNull() ?: return

        engine.hookThrowing(method, object : MethodHook() {
            override fun before(invocation: Invocation) {
                if (!featureOn(DeviceEnvKind.ROOT_EXEC)) return
                val builder = invocation.thisObject as? ProcessBuilder ?: return
                val command = runCatching { builder.command() }.getOrNull() ?: return
                val firstArg = command.firstOrNull() ?: return
                val cmdName = firstArg.substringAfterLast('/').substringAfterLast('\\')
                if (isBlockedCommand(cmdName, command.toTypedArray())) {
                    val dir = runCatching { builder.directory()?.path }.getOrNull()
                    emit(
                        kind = DeviceEnvKind.ROOT_EXEC,
                        operation = DeviceEnvOperation.PROCESS_BUILDER,
                        originalValue = command.joinToString(" "),
                        maskedValue = null,
                        targetClass = "java.lang.ProcessBuilder",
                        detectionPattern = ROOT_EXEC_PATTERN,
                        interventionAction = ACTION_BLOCKED,
                            params = buildMap {
                                put("api", "process-builder")
                                dir?.let { put("workdir", it) }
                            },
                        )
                        invocation.setThrowable(IOException("Command blocked by Modev: root-exec"))
                }
            }
        })
    }

    companion object {
        private const val ROOT_EXEC_PATTERN = "root-exec"
        private const val ACTION_BLOCKED = "BLOCKED"
    }
}
