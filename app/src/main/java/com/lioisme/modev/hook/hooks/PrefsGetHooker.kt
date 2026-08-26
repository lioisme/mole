package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.codec.Json
import com.lioisme.modev.core.model.PrefsKind
import com.lioisme.modev.core.model.PrefsOperation
import com.lioisme.modev.hook.ProbeContext

/**
 * 偏好读取 Hooker(V28):SharedPreferencesImpl 的 get* 全家桶 + contains/getAll。
 * after 捕获键与返回值(「随调用即可得」);值域受 fileContentCapture 闸门
 * (决策 D6),contains 仅记存在性不产值。
 *
 * 被动红线:仅读实参与返回值;返回的 Set/Map 即刻序列化为不可变文本,
 * 绝不持有引用、绝不回写、绝不触碰底层文件。
 */
class PrefsGetHooker(
    context: ProbeContext,
    index: PrefsInstanceIndex,
) : PrefsBaseHooker(context, index) {

    override fun install() {
        val registry = BehaviorMethodRegistry.forName(PrefsOpenHooker.IMPL_CLASS) ?: return
        installScalar(registry, "getString", TYPE_STRING)
        installScalar(registry, "getInt", TYPE_INT)
        installScalar(registry, "getLong", TYPE_LONG)
        installScalar(registry, "getFloat", TYPE_FLOAT)
        installScalar(registry, "getBoolean", TYPE_BOOLEAN)
        installCollection(registry, "getStringSet", TYPE_STRING_SET)
        installContains(registry)
        installGetAll(registry)
    }

    /** 标量读取(getString/getInt/...):值 = 返回值 toString。 */
    private fun installScalar(
        registry: BehaviorMethodRegistry,
        name: String,
        valueType: String,
    ) {
        hookAfter(registry.named(name)) { inv, result ->
            emit(
                kind = PrefsKind.GET,
                operation = scalarOperation(name),
                key = inv.getArg(0) as? String,
                value = capturedText(valueText(result)),
                path = index.pathOf(inv.thisObject),
                params = mapOf(PARAM_TYPE to valueType),
            )
        }
    }

    private fun scalarOperation(name: String): PrefsOperation =
        when (name) {
            "getString" -> PrefsOperation.GET_STRING
            "getInt" -> PrefsOperation.GET_INT
            "getLong" -> PrefsOperation.GET_LONG
            "getFloat" -> PrefsOperation.GET_FLOAT
            else -> PrefsOperation.GET_BOOLEAN
        }

    /** getStringSet:集合即刻序列化 JSON 数组(防调用方后续变异污染记录)。 */
    private fun installCollection(
        registry: BehaviorMethodRegistry,
        name: String,
        valueType: String,
    ) {
        hookAfter(registry.named(name)) { inv, result ->
            val set = result as? Set<*> ?: return@hookAfter
            emit(
                kind = PrefsKind.GET,
                operation = PrefsOperation.GET_STRING_SET,
                key = inv.getArg(0) as? String,
                value = capturedText(collectionText(set)),
                path = index.pathOf(inv.thisObject),
                params = mapOf(PARAM_TYPE to valueType),
            )
        }
    }

    /** contains:仅存在性(params[found]),零产值。 */
    private fun installContains(registry: BehaviorMethodRegistry) {
        hookAfter(registry.named("contains")) { inv, result ->
            if (result !is Boolean) return@hookAfter
            emit(
                kind = PrefsKind.GET,
                operation = PrefsOperation.CONTAINS,
                key = inv.getArg(0) as? String,
                path = index.pathOf(inv.thisObject),
                params = mapOf(PARAM_FOUND to result.toString()),
            )
        }
    }

    /** getAll:全量快照 JSON 对象(maxBytesPerField 钳制;无键)。 */
    private fun installGetAll(registry: BehaviorMethodRegistry) {
        hookAfter(registry.named("getAll")) { inv, result ->
            val map = result as? Map<*, *> ?: return@hookAfter
            if (map.isEmpty()) return@hookAfter
            emit(
                kind = PrefsKind.GET,
                operation = PrefsOperation.GET_ALL,
                value = capturedText(collectionText(map)),
                path = index.pathOf(inv.thisObject),
                params = mapOf(PARAM_TYPE to TYPE_MAP),
            )
        }
    }

    /** fileContentCapture 闸门(D6):关闭或空文本 → null。 */
    private fun capturedText(text: String?) =
        if (config().fileContentCapture) textViewOf(text) else null

    private fun valueText(result: Any?): String? =
        result?.toString()?.takeIf { it.isNotEmpty() }

    private fun collectionText(container: Any): String? =
        runCatching { Json.gson.toJson(container) }.getOrNull()

    companion object {
        const val PARAM_TYPE = "type"
        const val PARAM_FOUND = "found"
        const val TYPE_STRING = "string"
        const val TYPE_INT = "int"
        const val TYPE_LONG = "long"
        const val TYPE_FLOAT = "float"
        const val TYPE_BOOLEAN = "boolean"
        const val TYPE_STRING_SET = "stringSet"
        const val TYPE_MAP = "map"
    }
}
