package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.UiInteractionKind
import com.lioisme.modev.core.model.UiInteractionOperation
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.MethodHook
import com.lioisme.modev.hook.ProbeContext

/**
 * 对话框位置与返回键拦截 Hooker(V33):hook AlertDialog.show()/Dialog.show()
 * 获取窗口位置;hook Activity.onBackPressed / DialogInterface.dismiss 捕获返回键。
 * 需 targetSdk < 33(后弹窗自动取消)。
 */
class DialogLocationHooker(
    context: ProbeContext,
) : UiInteractionBaseHooker(context) {

    private val registry = BehaviorMethodRegistry.forName("android.app.AlertDialog")

    override fun install() {
        hookDialogShow()
        hookOnBackPressed()
    }

    private fun hookDialogShow() {
        // AlertDialog.show() 调用 Activity.showDialog,可从此处获取 dialog 引用
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
