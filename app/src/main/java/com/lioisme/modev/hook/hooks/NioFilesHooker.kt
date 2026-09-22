package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.BehaviorKind
import com.lioisme.modev.core.model.BehaviorOperation
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.ProbeContext

/**
 * NIO 文件 Hooker(V27):java.nio.file.Files 静态门面(API 26+;类缺失静默零安装)。
 *
 * 内容预览边界(被动红线 #11):仅「随调用即可得」的整读/整写 API 采集正文——
 * readAllBytes/readAllLines 的返回值与 write 的实参即内容本体,零额外 I/O;
 * 流式 API(newInputStream/newOutputStream/copy)绝不触碰句柄。
 */
class NioFilesHooker(
    context: ProbeContext,
) : BehaviorBaseHooker(context) {

    private val registry = BehaviorMethodRegistry.forName(FILES_CLASS)

    override fun install() {
        val r = registry ?: return
        hookAfter(r.named("delete")) { inv, _ ->
            if (!featureOn(BehaviorKind.FILE_DELETE)) return@hookAfter
            emit(BehaviorKind.FILE_DELETE, BehaviorOperation.DELETE, path = inv.argPath(0))
        }
        hookAfter(r.named("move")) { inv, _ ->
            if (!featureOn(BehaviorKind.FILE_RENAME)) return@hookAfter
            emit(
                kind = BehaviorKind.FILE_RENAME,
                operation = BehaviorOperation.RENAME,
                path = inv.argPath(0),
                destPath = inv.argPath(1),
            )
        }
        hookAfter(r.named("copy")) { inv, _ ->
            if (!featureOn(BehaviorKind.FILE_WRITE)) return@hookAfter
            emit(
                kind = BehaviorKind.FILE_WRITE,
                operation = BehaviorOperation.WRITE_ALL,
                path = inv.argPath(1),
                params = inv.argPath(0)?.let { mapOf(PARAM_SOURCE to it) },
            )
        }
        hookAfter(r.namedAny("createDirectory")) { inv, _ ->
            if (!featureOn(BehaviorKind.FILE_WRITE)) return@hookAfter
            emit(BehaviorKind.FILE_WRITE, BehaviorOperation.MKDIR, path = inv.argPath(0))
        }
        hookAfter(r.namedAny("createDirectories")) { inv, _ ->
            if (!featureOn(BehaviorKind.FILE_WRITE)) return@hookAfter
            emit(BehaviorKind.FILE_WRITE, BehaviorOperation.MKDIRS, path = inv.argPath(0))
        }
        hookAfter(r.named("newInputStream")) { inv, _ ->
            if (!featureOn(BehaviorKind.FILE_READ)) return@hookAfter
            emit(BehaviorKind.FILE_READ, BehaviorOperation.OPEN_READ, path = inv.argPath(0))
        }
        hookAfter(r.named("newOutputStream")) { inv, _ ->
            if (!featureOn(BehaviorKind.FILE_WRITE)) return@hookAfter
            val append = hasAppendOption(inv)
            emit(
                kind = BehaviorKind.FILE_WRITE,
                operation = BehaviorOperation.OPEN_WRITE,
                path = inv.argPath(0),
                params = if (append) mapOf(PARAM_APPEND to "true") else null,
            )
        }
        hookAfter(r.named("readAllBytes")) { inv, result ->
            // V44 T2.2 门控先行:readAllBytes 返回整文件字节,fileRead 默认关时
            // 旧形态仍会先走 viewOf(result) 的三态物化(hex+base64+text)。
            if (!featureOn(BehaviorKind.FILE_READ)) return@hookAfter
            val capture = config().fileContentCapture
            emit(
                kind = BehaviorKind.FILE_READ,
                operation = BehaviorOperation.READ_ALL,
                path = inv.argPath(0),
                fileSize = (result as? ByteArray)?.size?.toLong(),
                content = if (capture) viewOf(result as? ByteArray) else null,
            )
        }
        hookAfter(r.named("readAllLines")) { inv, result ->
            // V44 T2.2 门控先行 + 内容护栏:joinToString/toByteArray 必须在门控
            // 与 fileContentCapture 之后(旧形态每次调用先全量拼接整文件字符串)。
            if (!featureOn(BehaviorKind.FILE_READ)) return@hookAfter
            val lines = result as? List<*> ?: return@hookAfter
            val capture = config().fileContentCapture
            emit(
                kind = BehaviorKind.FILE_READ,
                operation = BehaviorOperation.READ_ALL,
                path = inv.argPath(0),
                fileSize = lines.size.toLong(),
                content = if (capture) viewOf(lines.joinToString("\n") { it?.toString().orEmpty() }.toByteArray()) else null,
                params = mapOf(LINE_COUNT to lines.size.toString()),
            )
        }
        hookAfter(r.named("write")) { inv, _ ->
            if (!featureOn(BehaviorKind.FILE_WRITE)) return@hookAfter
            // write(Path, byte[], ...) 与 write(Path, Iterable<CharSequence>, ...) 双形态。
            val bytes = inv.getArg(1) as? ByteArray
            val lines = inv.getArg(1) as? List<*>
            emit(
                kind = BehaviorKind.FILE_WRITE,
                operation = BehaviorOperation.WRITE_ALL,
                path = inv.argPath(0),
                fileSize = bytes?.size?.toLong() ?: lines?.size?.toLong(),
                content = if (!config().fileContentCapture) {
                    null
                } else {
                    viewOf(bytes) ?: viewOf(lines?.joinToString("\n") { it?.toString().orEmpty() }?.toByteArray())
                },
            )
        }
    }

    /** Invocation 实参 → Path 字符串(非 Path 形态返回 null)。 */
    private fun Invocation.argPath(index: Int): String? =
        runCatching {
            @Suppress("UNNECESSARY_SAFE_CALL")
            (getArg(index) as? java.nio.file.Path)?.toString()
        }.getOrNull()

    /** OpenOption 序列中是否含 APPEND(write 系 API 编译期不可达,运行期反射形态判断)。 */
    private fun hasAppendOption(invocation: Invocation): Boolean {
        for (index in 1 until invocation.argCount) {
            val option = invocation.getArg(index) ?: continue
            if (option.toString().contains("APPEND")) return true
        }
        return false
    }

    companion object {

        private const val FILES_CLASS = "java.nio.file.Files"

        /** 参数键:copy 源路径。 */
        const val PARAM_SOURCE = "source"

        /** 参数键:append 追加写标记。 */
        const val PARAM_APPEND = "append"

        /** 参数键:readAllLines 行数。 */
        const val LINE_COUNT = "lineCount"
    }
}
