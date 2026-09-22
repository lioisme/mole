package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.HookSwitch
import com.lioisme.modev.core.model.KeywordRule
import com.lioisme.modev.core.model.UiInteractionKind
import com.lioisme.modev.core.model.UiInteractionOperation
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.MethodHook
import com.lioisme.modev.hook.ProbeContext

/**
 * 对话框关键词拦截 Hooker(V33,V34 审计修复;V37 T0.1 闸门收口):
 * hook AlertDialog.Builder 构造后的 show(),在 before() 中检查 title/message
 * 是否命中配置中的 dialogKeywords 列表,命中则调用 setResult(null) 阻止弹窗显示。
 *
 * 干预机制:before() 中经 [matchedKeywords] 统一闸门(uiListen master ∧ DIALOG_KEYWORD
 * ∧ 词表非空)判定,命中才 setResult(null) 阻止弹窗显示——开关关闭时即使词表命中
 * 也一律放行(V37 FeatureGate 红线,修复「只压记录不压干预」旁路)。
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
                    // V44 T2.2 门控先行:extractTitle/extractMessage 均为反射路径
                    // (getMethod/getDeclaredField + setAccessible),旧形态每次
                    // AlertDialog.show 都先付两次反射取字段,再进门控。
                    // DIALOG_KEYWORD 默认关 → 主线程白付。
                    val switch = config()
                    if (!switch.uiInteractionEnabled(UiInteractionKind.DIALOG_KEYWORD)) return
                    if (switch.activeKeywordRules.isEmpty()) return
                    val dialog = invocation.thisObject ?: return
                    val title = extractTitle(dialog)
                    val message = extractMessage(dialog)
                    val matched = matchedKeywords(switch, title, message)
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

        /**
         * 拦截判定纯函数(V37 T0.1,可 JVM 直测;T2.1 规则化):
         * master ∧ kind 门控 → 有效规则表 → 标题/消息命中集(命中词文本)。
         * 任一前置不满足返回空集(= 不拦截)。
         */
        internal fun matchedKeywords(switch: HookSwitch, title: String?, message: String?): List<String> {
            if (!switch.uiInteractionEnabled(UiInteractionKind.DIALOG_KEYWORD)) return emptyList()
            val rules = switch.activeKeywordRules
            if (rules.isEmpty()) return emptyList()
            val matched = mutableListOf<String>()
            for (rule in rules) {
                if (KeywordRule.matches(rule, title) || KeywordRule.matches(rule, message)) {
                    matched += rule.text
                }
            }
            return matched
        }
    }
}
