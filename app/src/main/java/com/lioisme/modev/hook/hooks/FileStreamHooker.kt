package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.BehaviorKind
import com.lioisme.modev.core.model.BehaviorOperation
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.ProbeContext
import java.io.File

/**
 * 文件流 Hooker:FileInputStream / FileOutputStream 构造器(全部公开重载)。
 * FileReader/FileWriter 经 libcore 内部委托到流构造器被自然覆盖,不重复挂。
 *
 * 被动红线:仅记录路径与 append 标记,绝不触碰句柄读内容(内容预览由
 * [NioFilesHooker] 的整读 API 承担);FileDescriptor 变体无路径,
 * 记 params[source]=file-descriptor。
 */
class FileStreamHooker(
    context: ProbeContext,
) : BehaviorBaseHooker(context) {

    override fun install() {
        installStreams("java.io.FileInputStream", read = true)
        installStreams("java.io.FileOutputStream", read = false)
    }

    private fun installStreams(className: String, read: Boolean) {
        val clazz =
            runCatching {
                try {
                    Class.forName(className, false, null)
                } catch (e: ClassNotFoundException) {
                    Class.forName(className)
                }
            }.getOrNull() ?: return
        val ctors = runCatching { clazz.declaredConstructors }.getOrElse { emptyArray() }
        for (ctor in ctors) {
            engine.hook(ctor, object : com.lioisme.modev.hook.MethodHook() {
                override fun after(invocation: Invocation) {
                    emitStream(invocation, read = read)
                }
            })
        }
    }

    private fun emitStream(
        invocation: Invocation,
        read: Boolean,
    ) {
        // V44 T2.2 门控先行:FileInputStream/FileOutputStream 构造器是全进程最高频
        // 的钩子之一(框架启动即数百次),fileRead 默认关时必须首行短路,不得先付
        // 取参/路径字符串化(File.path 为惰性计算,首次访问即缓存分配)。
        val kind = if (read) BehaviorKind.FILE_READ else BehaviorKind.FILE_WRITE
        if (!featureOn(kind)) return
        val path = streamPathOf(invocation)
        val params =
            if (path == null) {
                mapOf(PARAM_SOURCE to SOURCE_FD)
            } else {
                appendFlag(invocation)?.let { mapOf(PARAM_APPEND to it.toString()) }
            }
        val operation = if (read) BehaviorOperation.OPEN_READ else BehaviorOperation.OPEN_WRITE
        emit(kind = kind, operation = operation, path = path, params = params)
    }

    /** 从构造器实参提取路径(File/String 形态;FileDescriptor 无路径返回 null)。 */
    private fun streamPathOf(invocation: Invocation): String? =
        when (val first = invocation.getArg(0)) {
            is File -> first.path
            is String -> first
            else -> null
        }

    /** FileOutputStream 的 append 标记(末位 Boolean 实参;无则 null)。 */
    private fun appendFlag(invocation: Invocation): Boolean? {
        for (index in invocation.argCount - 1 downTo 1) {
            when (val arg = invocation.getArg(index)) {
                is Boolean -> return arg
                null -> continue
                else -> return null
            }
        }
        return null
    }

    companion object {

        /** 参数键:来源形态(fd)。 */
        const val PARAM_SOURCE = "source"

        /** 参数键:append 追加写标记。 */
        const val PARAM_APPEND = "append"

        /** fd 来源标记值。 */
        const val SOURCE_FD = "file-descriptor"
    }
}
