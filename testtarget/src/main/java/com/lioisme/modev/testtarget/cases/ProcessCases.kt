package com.lioisme.modev.testtarget.cases

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import com.lioisme.modev.testtarget.Tt

/** E-MP-01 / E-EXIT-01 / E-EXIT-02 / E-CRASH-01:进程控制干预触发器。 */
object ProcessCases {

    private val main = Handler(Looper.getMainLooper())

    fun run(act: Activity, case: String) {
        when (case) {
            "E-MP-01" -> {
                val mgr = act.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                act.startActivityForResult(mgr.createScreenCaptureIntent(), 0x7701)
                Tt.result(case, "intent" to "started")
                main.postDelayed({ Tt.result(case, "elapsed" to "3000") }, 3000)
            }
            "E-EXIT-01" -> {
                System.exit(0)
                Tt.result(case, "survived" to "true")
            }
            "E-EXIT-02" -> {
                android.os.Process.killProcess(android.os.Process.myPid())
                Tt.result(case, "survived" to "true")
            }
            "E-CRASH-01" -> {
                Thread {
                    throw IllegalStateException("tt-crash-probe")
                }.apply { name = "tt-crash" }.start()
                main.postDelayed({
                    Tt.result(case, "survived" to "true")
                }, 1500)
            }
            else -> Tt.result(case, "error" to "unknown-case")
        }
    }
}
