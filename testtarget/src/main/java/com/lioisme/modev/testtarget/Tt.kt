package com.lioisme.modev.testtarget

import android.util.Log

/**
 * TT_RESULT 输出规范:增强/自检用例的可观测断言通道。
 * 格式:`TT_RESULT case=<用例ID> result=<k>=<v>;<k>=<v>`;ADB 侧 logcat -s TT_RESULT 解析。
 */
object Tt {
    const val TAG = "TT_RESULT"

    fun result(case: String, vararg kv: Pair<String, String>) {
        Log.i(TAG, "case=$case result=" + kv.joinToString(";") { (k, v) -> "$k=${v.replace(';', ',')}" })
    }

    fun result(case: String, detail: String) {
        Log.i(TAG, "case=$case result=$detail")
    }
}
