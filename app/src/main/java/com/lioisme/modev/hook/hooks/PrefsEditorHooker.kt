package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.codec.Json
import com.lioisme.modev.core.model.PrefsKind
import com.lioisme.modev.core.model.PrefsOperation
import com.lioisme.modev.hook.ProbeContext

/**
 * 偏好写入 Hooker:SharedPreferencesImpl$EditorImpl 的 put* 全家桶 +
 * remove/clear。after 捕获键与暂存值(「随调用即可得」实参);
 * 值域受 fileContentCapture 闸门(决策 D6);commit/apply 不挂钩(决策 D5)。
 *
 * 被动红线:仅读实参;绝不触碰底层文件、绝不反射 EditorImpl 内部字段。
 */
class PrefsEditorHooker(
    context: ProbeContext,
    index: PrefsInstanceIndex,
) : PrefsBaseHooker(context, index) {

    override fun install() {
        val registry = BehaviorMethodRegistry.forName(EDITOR_CLASS) ?: return
        installPut(registry, "putString", TYPE_STRING)
        installPut(registry, "putInt", TYPE_INT)
        installPut(registry, "putLong", TYPE_LONG)
        installPut(registry, "putFloat", TYPE_FLOAT)
        installPut(registry, "putBoolean", TYPE_BOOLEAN)
        installPutSet(registry)
        installRemove(registry)
        installClear(registry)
    }

    /** 标量写入(putString/...):值 = 实参 toString。 */
    private fun installPut(
        registry: BehaviorMethodRegistry,
        name: String,
        valueType: String,
    ) {
        hookAfter(registry.named(name)) { inv, _ ->
            emit(
                kind = PrefsKind.PUT,
                operation = putOperation(name),
                key = inv.getArg(0) as? String,
                value = capturedText(valueText(inv.getArg(1))),
                path = index.pathOfEditor(inv.thisObject),
                params = mapOf(PARAM_TYPE to valueType),
            )
        }
    }

    private fun putOperation(name: String): PrefsOperation =
        when (name) {
            "putString" -> PrefsOperation.PUT_STRING
            "putInt" -> PrefsOperation.PUT_INT
            "putLong" -> PrefsOperation.PUT_LONG
            "putFloat" -> PrefsOperation.PUT_FLOAT
            else -> PrefsOperation.PUT_BOOLEAN
        }

    /** putStringSet:实参集合即刻序列化 JSON 数组。 */
    private fun installPutSet(registry: BehaviorMethodRegistry) {
        hookAfter(registry.named("putStringSet")) { inv, _ ->
            val set = inv.getArg(1) as? Set<*> ?: return@hookAfter
            emit(
                kind = PrefsKind.PUT,
                operation = PrefsOperation.PUT_STRING_SET,
                key = inv.getArg(0) as? String,
                value = capturedText(collectionText(set)),
                path = index.pathOfEditor(inv.thisObject),
                params = mapOf(PARAM_TYPE to TYPE_STRING_SET),
            )
        }
    }

    private fun installRemove(registry: BehaviorMethodRegistry) {
        hookAfter(registry.named("remove")) { inv, _ ->
            emit(
                kind = PrefsKind.REMOVE,
                operation = PrefsOperation.REMOVE,
                key = inv.getArg(0) as? String,
                path = index.pathOfEditor(inv.thisObject),
            )
        }
    }

    private fun installClear(registry: BehaviorMethodRegistry) {
        hookAfter(registry.named("clear")) { inv, _ ->
            emit(
                kind = PrefsKind.CLEAR,
                operation = PrefsOperation.CLEAR,
                path = index.pathOfEditor(inv.thisObject),
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
        /** 内部类名($ 需转义防 Kotlin 模板插值)。 */
        const val EDITOR_CLASS = "android.app.SharedPreferencesImpl\$EditorImpl"

        const val PARAM_TYPE = "type"
        const val TYPE_STRING = "string"
        const val TYPE_INT = "int"
        const val TYPE_LONG = "long"
        const val TYPE_FLOAT = "float"
        const val TYPE_BOOLEAN = "boolean"
        const val TYPE_STRING_SET = "stringSet"
    }
}
