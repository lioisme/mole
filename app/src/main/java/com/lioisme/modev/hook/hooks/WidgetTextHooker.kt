package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.UiInteractionKind
import com.lioisme.modev.core.model.UiInteractionOperation
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.MethodHook
import com.lioisme.modev.hook.ProbeContext

/**
 * 控件文本赋值 Hooker(V33):hook android.widget.TextView.setText(CharSequence),
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
                    val text = invocation.getArg(0) as? CharSequence ?: return
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
}
