package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.BehaviorKind
import com.lioisme.modev.core.model.BehaviorOperation
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.ProbeContext
import java.io.File

/**
 * Shell 命令 Hooker:Runtime.exec 全部重载 + ProcessBuilder.start。
 * 记录 argv 全量与工作目录/环境差异标记;进程输出流不触碰(被动红线),
 * exitCode/stdout 捕获列为二期。
 */
class ShellHooker(
    context: ProbeContext,
) : BehaviorBaseHooker(context) {

    override fun install() {
        installRuntime()
        installProcessBuilder()
    }

    private fun installRuntime() {
        val registry = BehaviorMethodRegistry.forName("java.lang.Runtime") ?: return
        hookAfter(registry.named("exec")) { inv, _ ->
            val first = inv.getArg(0)
            val argv =
                when (first) {
                    is String -> listOf(first)
                    is Array<*> -> first.filterIsInstance<String>().takeIf { it.isNotEmpty() }
                    else -> null
                } ?: return@hookAfter
            val params =
                buildMap {
                    put(PARAM_API, API_RUNTIME)
                    for (index in 1 until inv.argCount) {
                        when (val arg = inv.getArg(index)) {
                            is File -> put(PARAM_WORKDIR, arg.path)
                            is Array<*> -> if (arg.isArrayOf<String>()) put(PARAM_ENV, ENV_CUSTOM)
                            is Boolean -> put(PARAM_REDIRECTS, arg.toString())
                        }
                    }
                }
            emit(
                kind = BehaviorKind.SHELL_EXEC,
                operation = BehaviorOperation.EXEC,
                command = argv,
                params = params,
            )
        }
    }

    private fun installProcessBuilder() {
        val registry = BehaviorMethodRegistry.forName("java.lang.ProcessBuilder") ?: return
        hookAfter(registry.named("start")) { inv, _ ->
            val builder = inv.thisObject as? ProcessBuilder ?: return@hookAfter
            val command = runCatching { builder.command() }.getOrNull()?.takeIf { it.isNotEmpty() }
                ?: return@hookAfter
            val workdir = runCatching { builder.directory()?.path }.getOrNull()
            val params =
                buildMap {
                    put(PARAM_API, API_PROCESS_BUILDER)
                    workdir?.let { put(PARAM_WORKDIR, it) }
                    runCatching { if (builder.redirectErrorStream()) put(PARAM_MERGE_STREAMS, "true") }
                }
            emit(
                kind = BehaviorKind.SHELL_EXEC,
                operation = BehaviorOperation.EXEC,
                command = command,
                params = params,
            )
        }
    }

    companion object {

        /** 参数键:调用门面。 */
        const val PARAM_API = "api"
        const val API_RUNTIME = "runtime"
        const val API_PROCESS_BUILDER = "process-builder"

        /** 参数键:工作目录。 */
        const val PARAM_WORKDIR = "workdir"

        /** 参数键:自定义环境变量。 */
        const val PARAM_ENV = "env"
        const val ENV_CUSTOM = "custom"

        /** 参数键:重定向合并标记(Runtime.exec 重载布尔)。 */
        const val PARAM_REDIRECTS = "redirects"

        /** 参数键:stderr 并入 stdout(ProcessBuilder.redirectErrorStream)。 */
        const val PARAM_MERGE_STREAMS = "mergeStreams"
    }
}
