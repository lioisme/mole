package com.lioisme.modev.hook.hooks

import android.content.res.AssetFileDescriptor
import com.lioisme.modev.core.model.BehaviorKind
import com.lioisme.modev.core.model.BehaviorOperation
import com.lioisme.modev.hook.ProbeContext

/**
 * Assets 资源 Hooker(V27):AssetManager.open/openFd/list 全公开重载。
 *
 * 被动红线:open 返回的流绝不读取(消费会破坏宿主读取位置),内容预览不适用于
 * 本 Hooker(整读 API 内容采集由 [NioFilesHooker] 承担);openFd 的长度为
 * 调用结果自带元数据,可安全记录。
 */
class AssetHooker(
    context: ProbeContext,
) : BehaviorBaseHooker(context) {

    private val registry = BehaviorMethodRegistry.forName("android.content.res.AssetManager")

    override fun install() {
        val r = registry ?: return
        hookAfter(r.namedAny("open", "openNonAsset")) { inv, _ ->
            val name = inv.getArg(0) as? String ?: return@hookAfter
            val params = buildMap {
                val mode = inv.getArg(1)
                if (mode is String) put(PARAM_ACCESS_MODE, mode)
            }
            emit(
                kind = BehaviorKind.ASSET_READ,
                operation = BehaviorOperation.ASSET_OPEN,
                path = name,
                params = params.ifEmpty { null },
            )
        }
        hookAfter(r.named("openFd")) { inv, result ->
            val name = inv.getArg(0) as? String ?: return@hookAfter
            val fdLength = runCatching { (result as? AssetFileDescriptor)?.length }.getOrNull()
            emit(
                kind = BehaviorKind.ASSET_READ,
                operation = BehaviorOperation.ASSET_OPEN_FD,
                path = name,
                fileSize = fdLength?.takeIf { it >= 0 },
            )
        }
        hookAfter(r.named("list")) { inv, result ->
            val dir = inv.getArg(0) as? String ?: return@hookAfter
            val names = result as? Array<String> ?: return@hookAfter
            emit(
                kind = BehaviorKind.ASSET_READ,
                operation = BehaviorOperation.ASSET_LIST,
                path = dir,
                params = mapOf(DIR_COUNT to names.size.toString()),
            )
        }
    }

    companion object {

        /** 参数键:打开模式。 */
        const val PARAM_ACCESS_MODE = "accessMode"

        /** 参数键:列举条目数。 */
        const val DIR_COUNT = "dirCount"
    }
}
