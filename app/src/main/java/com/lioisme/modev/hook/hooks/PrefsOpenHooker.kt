package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.PrefsKind
import com.lioisme.modev.core.model.PrefsOperation
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.ProbeContext
import java.io.File

/**
 * 偏好配置文件载入 Hooker:
 * - SharedPreferencesImpl 全部首参 File 的声明构造器(after)= 磁盘读入完成时刻,
 *   进程内每文件一次(实例缓存),天然低噪;
 * - edit(after)登记 editor→路径映射,供 [PrefsEditorHooker] 反查。
 *
 * 被动红线:仅读取构造实参 File 路径,绝不触碰文件内容、绝不反射内部字段。
 */
class PrefsOpenHooker(
    context: ProbeContext,
    index: PrefsInstanceIndex,
) : PrefsBaseHooker(context, index) {

    override fun install() {
        installOpen()
        installEdit()
    }

    /** 全部首参 File 的声明构造器(after;与 FileStreamHooker 构造器扫描同款)。 */
    private fun installOpen() {
        val clazz = BehaviorMethodRegistry.classForName(IMPL_CLASS) ?: return
        val ctors = runCatching { clazz.declaredConstructors }.getOrElse { emptyArray() }
        for (ctor in ctors) {
            if (ctor.parameterTypes.firstOrNull() != File::class.java) continue
            engine.hook(ctor, object : com.lioisme.modev.hook.MethodHook() {
                override fun after(invocation: Invocation) {
                    val file = invocation.getArg(0) as? File ?: return
                    val path = runCatching { file.absolutePath }.getOrDefault(file.path)
                    index.rememberImpl(invocation.thisObject, path)
                    emit(
                        kind = PrefsKind.OPEN,
                        operation = PrefsOperation.OPEN,
                        path = path,
                        params = buildMap {
                            invocation.getArg(1)?.let { put(PARAM_MODE, it.toString()) }
                        },
                    )
                }
            })
        }
    }

    /** edit(after):editor 实例 → impl → 路径 固化(不产记录)。 */
    private fun installEdit() {
        val registry = BehaviorMethodRegistry.forName(IMPL_CLASS) ?: return
        hookAfter(registry.named("edit")) { inv, result ->
            index.rememberEditor(result, inv.thisObject)
        }
    }

    companion object {
        const val IMPL_CLASS = "android.app.SharedPreferencesImpl"

        /** 参数键:打开 mode(Context.MODE_* 原始 int)。 */
        const val PARAM_MODE = "mode"
    }
}
