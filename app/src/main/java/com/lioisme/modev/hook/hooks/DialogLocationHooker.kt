package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.UiInteractionKind
import com.lioisme.modev.core.model.UiInteractionOperation
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.MethodHook
import com.lioisme.modev.hook.ProbeContext

/**
 * 对话框位置与返回键拦截 Hooker:hook AlertDialog.show/Dialog.show
 * 获取窗口位置;hook Activity.onBackPressed / DialogInterface.dismiss 捕获返回键。
 * 需 targetSdk < 33(后弹窗自动取消)。
 */
class DialogLocationHooker(
    context: ProbeContext,
) : UiInteractionBaseHooker(context) {

    private val registry = BehaviorMethodRegistry.forName("android.app.AlertDialog")

    override fun install() {
        hookDialogShow()
        hookDialogCancel()
        hookOnBackPressed()
    }

    /**
     * `Dialog.cancel` 观测:`DIALOG_CANCEL` 在枚举、四语标签与筛选菜单里
     * 都存在,但从未挂钩 ⇒ 恒空选项。用户主动取消与点按钮关闭在风控语义上不同,值得单列。
     */
    private fun hookDialogCancel() {
        val methods = registry?.named("cancel").orEmpty()
        if (methods.isEmpty()) return
        for (method in methods) {
            engine.hook(method, object : MethodHook() {
                override fun after(invocation: Invocation) {
                    if (!featureOn(UiInteractionKind.DIALOG_SHOW)) return // 门控先行
                    emit(
                        kind = UiInteractionKind.DIALOG_SHOW,
                        operation = UiInteractionOperation.DIALOG_CANCEL,
                        dialogTitle = extractTitle(invocation.thisObject),
                    )
                }
            })
        }
    }

    private fun hookDialogShow() {
        // AlertDialog.show 调用 Activity.showDialog,可从此处获取 dialog 引用
        val methods = registry?.named("show").orEmpty()
        if (methods.isEmpty()) return
        for (method in methods) {
            engine.hook(method, object : MethodHook() {
                override fun after(invocation: Invocation) {
                    // V44 T2.2 门控先行:extractTitle 走 getMethod+invoke 反射,必须排在门控之后。
                    if (!featureOn(UiInteractionKind.DIALOG_SHOW)) return
                    val title = extractTitle(invocation.thisObject)
                    emit(
                        kind = UiInteractionKind.DIALOG_SHOW,
                        operation = UiInteractionOperation.DIALOG_SHOW,
                        dialogTitle = title,
                        params = mapOf(
                            "location" to "center",
                            "callerThread" to Thread.currentThread().name,
                        ),
                    )
                }
            })
        }
    }

    private fun hookOnBackPressed() {
        val methods = runCatching {
            val clazz = Class.forName("android.app.Activity")
            clazz.methods.filter { it.name == "onBackPressed" }
        }.getOrNull().orEmpty()
        for (method in methods) {
            engine.hook(method, object : MethodHook() {
                override fun after(invocation: Invocation) {
                    // V44 T2.2 门控先行:onBackPressed 为交互热路径,取类名/组装全部后置。
                    if (!featureOn(UiInteractionKind.DIALOG_SHOW)) return
                    val activityName = invocation.thisObject?.javaClass?.name ?: return
                    emit(
                        kind = UiInteractionKind.DIALOG_SHOW,
                        operation = UiInteractionOperation.DIALOG_DISMISS,
                        activityName = activityName,
                        params = mapOf("trigger" to "backKey"),
                    )
                }
            })
        }
    }

    private fun extractTitle(dialog: Any?): String? = runCatching {
        val mGetTitle = dialog?.javaClass?.getMethod("getTitle") ?: return@runCatching null
        (mGetTitle.invoke(dialog) as? CharSequence)?.toString()
    }.getOrNull()
}
