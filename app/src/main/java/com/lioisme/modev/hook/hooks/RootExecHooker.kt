package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.DeviceEnvKind
import com.lioisme.modev.core.model.DeviceEnvOperation
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.MethodHook
import com.lioisme.modev.hook.ProbeContext

/**
 * Root 命令执行干预 Hooker(V31):拦截 Runtime.exec(String[]) 和 ProcessBuilder.start(),
 * 当命令包含 su 时阻止执行,防止应用通过命令执行检测 root。
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
            engine.hook(methodStringArray, object : MethodHook() {
                override fun before(invocation: Invocation) {
                    if (!config().rootExec) return
                    val command = invocation.getArg(0) as? Array<*> ?: return
                    if (!containsSu(command)) return
                    emit(
                        kind = DeviceEnvKind.ROOT_EXEC,
                        operation = DeviceEnvOperation.EXEC_COMMAND,
                        originalValue = command.joinToString(" "),
                        maskedValue = null,
                        targetClass = "java.lang.Runtime",
                        detectionPattern = ROOT_EXEC_PATTERN,
                        params = mapOf("api" to "exec-string-array"),
                    )
                }
            })
        }

        // exec(String[], String[])
        val stringArrayStringArrayClass = arrayOf(Array<String>::class.java, Array<String>::class.java)
        val methodStringArrayStringArray = runCatching {
            clazz.getMethod("exec", Array<String>::class.java, Array<String>::class.java)
        }.getOrNull()
        if (methodStringArrayStringArray != null) {
            engine.hook(methodStringArrayStringArray, object : MethodHook() {
                override fun before(invocation: Invocation) {
                    if (!config().rootExec) return
                    val command = invocation.getArg(0) as? Array<*> ?: return
                    if (!containsSu(command)) return
                    emit(
                        kind = DeviceEnvKind.ROOT_EXEC,
                        operation = DeviceEnvOperation.EXEC_COMMAND,
                        originalValue = command.joinToString(" "),
                        maskedValue = null,
                        targetClass = "java.lang.Runtime",
                        detectionPattern = ROOT_EXEC_PATTERN,
                        params = mapOf("api" to "exec-string-array-env"),
                    )
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
            engine.hook(methodWithDir, object : MethodHook() {
                override fun before(invocation: Invocation) {
                    if (!config().rootExec) return
                    val command = invocation.getArg(0) as? Array<*> ?: return
                    if (!containsSu(command)) return
                    val dir = (invocation.getArg(2) as? java.io.File)?.path
                    emit(
                        kind = DeviceEnvKind.ROOT_EXEC,
                        operation = DeviceEnvOperation.EXEC_COMMAND,
                        originalValue = command.joinToString(" "),
                        maskedValue = null,
                        targetClass = "java.lang.Runtime",
                        detectionPattern = ROOT_EXEC_PATTERN,
                        params = buildMap {
                            put("api", "exec-string-array-env-dir")
                            dir?.let { put("workdir", it) }
                        },
                    )
                }
            })
        }
    }

    private fun hookProcessBuilderStart() {
        val clazz = runCatching { Class.forName("java.lang.ProcessBuilder") }.getOrNull() ?: return
        val method = runCatching { clazz.getMethod("start") }.getOrNull() ?: return

        engine.hook(method, object : MethodHook() {
            override fun before(invocation: Invocation) {
                if (!config().rootExec) return
                val builder = invocation.thisObject as? ProcessBuilder ?: return
                val command = runCatching { builder.command() }.getOrNull() ?: return
                if (!containsSu(command)) return
                val dir = runCatching { builder.directory()?.path }.getOrNull()
                emit(
                    kind = DeviceEnvKind.ROOT_EXEC,
                    operation = DeviceEnvOperation.PROCESS_BUILDER,
                    originalValue = command.joinToString(" "),
                    maskedValue = null,
                    targetClass = "java.lang.ProcessBuilder",
                    detectionPattern = ROOT_EXEC_PATTERN,
                    params = buildMap {
                        put("api", "process-builder")
                        dir?.let { put("workdir", it) }
                    },
                )
            }
        })
    }

    private fun containsSu(command: Array<*>): Boolean =
        command.any { arg ->
            val s = arg?.toString() ?: return@any false
            val name = s.substringAfterLast('/').substringAfterLast('\\')
            name.equals("su", ignoreCase = true)
        }

    private fun containsSu(command: List<String>): Boolean =
        command.any { arg ->
            val name = arg.substringAfterLast('/').substringAfterLast('\\')
            name.equals("su", ignoreCase = true)
        }

    companion object {
        private const val ROOT_EXEC_PATTERN = "root-exec"
    }
}
