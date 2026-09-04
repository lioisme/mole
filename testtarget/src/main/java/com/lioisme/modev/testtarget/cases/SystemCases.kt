package com.lioisme.modev.testtarget.cases

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.lioisme.modev.testtarget.Tt

/** L-SYS-*:系统信息触发器(Logcat 输出/应用元数据/签名读取)。 */
object SystemCases {

    fun run(ctx: Context, case: String) {
        when (case) {
            "L-SYS-01" -> {
                Log.v("TTSYS", "verbose-probe")
                Log.d("TTSYS", "debug-probe")
                Log.i("TTSYS", "info-probe")
                Log.w("TTSYS", "warn-probe")
                Log.e("TTSYS", "error-probe")
                Tt.result(case, "logs" to "5")
            }
            "L-SYS-02" -> {
                val ai = ctx.packageManager.getApplicationInfo(ctx.packageName, 0)
                val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
                Tt.result(case, "version" to (pi.versionName ?: "?"), "targetSdk" to ai.targetSdkVersion.toString())
            }
            "L-SYS-03" -> {
                @Suppress("DEPRECATION")
                val pi = ctx.packageManager.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNATURES)
                Tt.result(case, "sigCount" to (pi.signatures?.size ?: 0).toString())
            }
            else -> Tt.result(case, "error" to "unknown-case")
        }
    }
}
