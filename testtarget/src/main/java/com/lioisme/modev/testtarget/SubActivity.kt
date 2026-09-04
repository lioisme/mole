package com.lioisme.modev.testtarget

import android.app.Activity
import android.os.Bundle

/**
 * 分离进程靶(C-MULTI-01):运行于 `:sub` 进程,验证 spool 按进程分目录 + 记录归并主包名。
 */
class SubActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("tt_sub", MODE_PRIVATE)
        prefs.edit().putString("sub-key", "sub-value").commit()
        java.io.File(filesDir, "tt-sub-write.txt").writeText("sub")
        Tt.result("C-MULTI-01", "process" to currentProcessName(), "done" to "true")
        finish()
    }

    private fun currentProcessName(): String =
        runCatching { android.app.Application.getProcessName() }.getOrDefault("?")
}
