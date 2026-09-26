package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.BehaviorKind
import com.lioisme.modev.core.model.BehaviorOperation
import com.lioisme.modev.hook.ProbeContext
import java.io.File

/**
 * 文件元操作 Hooker:java.io.File 的删除/重命名/建目录/列目录。
 * 仅 after 且原方法正常返回后触发(delete/renameTo 以返回值 true 为准,
 * false = 操作失败不记录);list/listFiles 返回 null(目录不存在)不记录。
 */
class FileMetaHooker(
    context: ProbeContext,
) : BehaviorBaseHooker(context) {

    private val registry = BehaviorMethodRegistry.forName("java.io.File")

    override fun install() {
        val r = registry ?: return
        hookAfter(r.named("delete")) { inv, result ->
            if (result == true) {
                emit(
                    kind = BehaviorKind.FILE_DELETE,
                    operation = BehaviorOperation.DELETE,
                    path = (inv.thisObject as? File)?.path,
                )
            }
        }
        hookAfter(r.named("renameTo")) { inv, result ->
            if (result == true) {
                emit(
                    kind = BehaviorKind.FILE_RENAME,
                    operation = BehaviorOperation.RENAME,
                    path = (inv.thisObject as? File)?.path,
                    destPath = (inv.getArg(0) as? File)?.path,
                )
            }
        }
        hookAfter(r.named("mkdir")) { inv, result ->
            if (result == true) {
                emit(
                    kind = BehaviorKind.FILE_WRITE,
                    operation = BehaviorOperation.MKDIR,
                    path = (inv.thisObject as? File)?.path,
                )
            }
        }
        hookAfter(r.named("mkdirs")) { inv, result ->
            if (result == true) {
                emit(
                    kind = BehaviorKind.FILE_WRITE,
                    operation = BehaviorOperation.MKDIRS,
                    path = (inv.thisObject as? File)?.path,
                )
            }
        }
        hookAfter(r.namedAny("list", "listFiles")) { inv, result ->
            // list/listFiles:目录不存在时返回 null,不记录;条目数入 params(零迭代)。
            val dir = (inv.thisObject as? File)?.path ?: return@hookAfter
            val count = (result as? Array<*>)?.size ?: return@hookAfter
            emit(
                kind = BehaviorKind.FILE_LIST,
                operation = BehaviorOperation.LIST,
                path = dir,
                params = mapOf(DIR_COUNT to count.toString()),
            )
        }
    }

    companion object {

        /** 目录列举条目数参数键。 */
        const val DIR_COUNT = "dirCount"
    }
}
