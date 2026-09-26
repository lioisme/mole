package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.UiInteractionKind
import com.lioisme.modev.core.model.UiInteractionOperation
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.MethodHook
import com.lioisme.modev.hook.ProbeContext

/**
 * 控件文本赋值 Hooker:hook android.widget.TextView.setText(CharSequence),
 * 记录每次赋值的文本内容(截断预览)与控件类名。
 * 仅记录文本非空且长度 > 1 的赋值,避免空/占位噪声。
 */
class WidgetTextHooker(
    context: ProbeContext,
) : UiInteractionBaseHooker(context) {

    private val registry = BehaviorMethodRegistry.forName("android.widget.TextView")

    override fun install() {
        val methods = registry?.named("setText").orEmpty()
        if (methods.isEmpty()) return
        for (method in methods) {
            engine.hook(method, object : MethodHook() {
                override fun after(invocation: Invocation) {
                    // V44 T2.2 门控先行:setText 在列表滚动期每帧数十次,旧形态
                    // 「取参 + text.length + subSequence/toString + 取类名」全部先于
                    // emit 内部门控执行(widgetText 默认关 → 主线程白付分配)。
                    if (!featureOn(UiInteractionKind.WIDGET_TEXT)) return
                    val arg = invocation.getArg(0)
                    // V50 孤儿整改:循环挂了全部 setText 重载,但旧形态把非 CharSequence 的
                    // 第一实参直接 return ⇒ `setText(@StringRes int)` 这一重载恒零发射,
                    // 而 WIDGET_SET_TEXT_RES 在枚举/四语/筛选菜单里都是可见选项。
                    if (arg is Int) {
                        emitResourceId(invocation, arg)
                        return
                    }
                    val text = arg as? CharSequence ?: return
                    val len = text.length
                    if (len <= 1) return
                    val max = config().maxBytesPerField
                    val preview = if (max > 0 && len > max) text.subSequence(0, max).toString() else text.toString()
                    val widgetClassName = invocation.thisObject?.javaClass?.name ?: return
                    emit(
                        kind = UiInteractionKind.WIDGET_TEXT,
                        operation = UiInteractionOperation.WIDGET_SET_TEXT,
                        widgetText = preview,
                        widgetClassName = widgetClassName,
                        params = mapOf(
                            "textLength" to len.toString(),
                            "truncated" to (len > (if (max > 0) max else Int.MAX_VALUE)).toString(),
                        ),
                    )
                }
            })
        }
    }

    /**
     * `setText(@StringRes int)` 重载:解析资源名与文本值后发射
     * (反射全部排在门控之后;解析失败仍发射 —— 资源 id 本身就是有效观测信息)。
     */
    private fun emitResourceId(invocation: Invocation, resId: Int) {
        val widget = invocation.thisObject ?: return
        val widgetClassName = widget.javaClass.name
        val context = runCatching {
            widget.javaClass.getMethod("getContext").invoke(widget) as? android.content.Context
        }.getOrNull()
        val resolved = context?.let {
            runCatching { it.javaClass.getMethod("getText", Int::class.javaPrimitiveType)
                .invoke(it, resId) as? CharSequence }.getOrNull()?.toString()
        }
        val entryName = context?.let {
            runCatching { it.resources.getResourceEntryName(resId) }.getOrNull()
        }
        val max = config().maxBytesPerField
        val preview = resolved?.let {
            if (max > 0 && it.length > max) it.substring(0, max) else it
        }
        emit(
            kind = UiInteractionKind.WIDGET_TEXT,
            operation = UiInteractionOperation.WIDGET_SET_TEXT_RES,
            widgetText = preview,
            widgetClassName = widgetClassName,
            params = buildList {
                add("resId" to "0x${resId.toUInt().toString(16)}")
                entryName?.let { add("resName" to it) }
                add("resolved" to (preview != null).toString())
            }.toMap(),
        )
    }
}
