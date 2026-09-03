package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.UiInteractionKind
import com.lioisme.modev.core.model.UiInteractionOperation
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.MethodHook
import com.lioisme.modev.hook.ProbeContext

/**
 * 对话框关键词拦截 Hooker(V33,V34 审计修复):hook AlertDialog.Builder 构造后的 show(),
 * 在 before() 中检查 title/message 是否命中配置中的 dialogKeywords 列表,
 * 命中则调用 setResult(null) 阻止弹窗显示。
 *
 * 干预机制:before() 中 setResult(null) 阻止 AlertDialog.show() 执行,弹窗不显示。
 * 干预结果通过 [UiInteractionRecord.interventionAction] 记录("BLOCKED"|"DETECTED"|null)。
 *
 * 安全注意:此功能会阻止匹配关键词的弹窗显示,可能影响应用正常交互。
 * 仅在用户显式开启并配置关键词时生效。
 */
class DialogKeywordInterceptHooker(
    context: ProbeContext,
) : UiInteractionBaseHooker(context) {

    private val registry = BehaviorMethodRegistry.forName("android.app.AlertDialog")

    override fun install() {
        val methods = registry?.named("show").orEmpty()
        if (methods.isEmpty()) return
        for (method in methods) {
            engine.hook(method, object : MethodHook() {
                override fun before(invocation: Invocation) {
                    val dialog = invocation.thisObject ?: return
                    val title = extractTitle(dialog)
                    val message = extractMessage(dialog)
                    val keywords = config().dialogKeywords
                    if (keywords.isEmpty()) return
                    val matched = mutableListOf<String>()
                    title?.let { t -> keywords.filter { t.contains(it, ignoreCase = true) }.let(matched::addAll) }
                    message?.let { m -> keywords.filter { m.contains(it, ignoreCase = true) }.let(matched::addAll) }
                    if (matched.isEmpty()) return
                    emit(
                        kind = UiInteractionKind.DIALOG_KEYWORD,
                        operation = UiInteractionOperation.DIALOG_INTERCEPTED,
                        dialogTitle = title,
                        dialogMessage = message,
                        dialogKeywords = matched,
                        interventionAction = ACTION_BLOCKED,
                    )
                    invocation.setResult(null)
                }
            })
        }
    }

    private fun extractTitle(dialog: Any?): String? = runCatching {
        val m = dialog?.javaClass?.getMethod("getTitle") ?: return null
        (m.invoke(dialog) as? CharSequence)?.toString()
    }.getOrNull()

    private fun extractMessage(dialog: Any?): String? = runCatching {
        val getField = dialog?.javaClass?.getDeclaredField("mAlert") ?: return null
        getField.isAccessible = true
        val alert = getField.get(dialog)
        val mMessage = alert?.javaClass?.getDeclaredField("mMessage") ?: return null
        mMessage.isAccessible = true
        (mMessage.get(alert) as? CharSequence)?.toString()
    }.getOrNull()

    companion object {
        /** interventionAction:已阻止弹窗显示 */
        private const val ACTION_BLOCKED = "BLOCKED"
    }
}
