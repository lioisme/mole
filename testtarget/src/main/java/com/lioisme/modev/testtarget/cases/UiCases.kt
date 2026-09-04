package com.lioisme.modev.testtarget.cases

import android.app.Activity
import android.app.AlertDialog
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import com.lioisme.modev.testtarget.Tt
import com.lioisme.modev.testtarget.TriggerActivity

/** L-UI-* / E-DLG-*:UI 交互触发器(弹窗/控件文本/Activity 跳转/生命周期)。 */
object UiCases {

    private val main = Handler(Looper.getMainLooper())

    fun run(act: Activity, case: String) {
        when (case) {
            "L-UI-01" -> {
                val d = AlertDialog.Builder(act)
                    .setTitle("TT-Dialog")
                    .setMessage("plain-dialog-body")
                    .setPositiveButton("ok") { x, _ -> x.dismiss() }
                    .show()
                main.postDelayed({
                    Tt.result(case, "shown" to d.isShowing.toString())
                    d.dismiss()
                }, 400)
            }
            "L-UI-02", "E-DLG-01" -> {
                // 关键字弹窗:dialogKeywords 含 "ModevKeywordHit" 时 ON 轮应被拦截
                val d = runCatching {
                    AlertDialog.Builder(act)
                        .setTitle("安全警告")
                        .setMessage("检测到模拟器 ModevKeywordHit 环境,应用将退出")
                        .show()
                }.getOrNull()
                main.postDelayed({
                    Tt.result(case, "shown" to (d?.isShowing?.toString() ?: "null"))
                    runCatching { d?.dismiss() }
                }, 500)
            }
            "E-DLG-02" -> {
                // 非关键字弹窗:ON 轮仍应正常显示(DIALOG_PASSED)
                val d = runCatching {
                    AlertDialog.Builder(act)
                        .setTitle("普通提示")
                        .setMessage("这是一条不含任何关键字的普通消息")
                        .show()
                }.getOrNull()
                main.postDelayed({
                    Tt.result(case, "shown" to (d?.isShowing?.toString() ?: "null"))
                    runCatching { d?.dismiss() }
                }, 500)
            }
            "L-UI-03" -> {
                val tv = TextView(act)
                tv.setText("widget-text-case")
                tv.setText(android.R.string.ok)
                Tt.result(case, "text" to tv.text.toString())
            }
            "L-UI-04" -> {
                act.startActivity(android.content.Intent(act, TriggerActivity::class.java).apply {
                    putExtra(TriggerActivity.EXTRA_DOMAIN, "system")
                    putExtra(TriggerActivity.EXTRA_CASE, "L-SYS-02")
                })
                Tt.result(case, "started" to "true")
            }
            "L-UI-05" -> {
                Tt.result(case, "resumed" to "true")
            }
            else -> Tt.result(case, "error" to "unknown-case")
        }
    }
}
