package com.lioisme.modev.testtarget

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager
import com.lioisme.modev.testtarget.TriggerActivity.Companion.EXTRA_CASE

/**
 * FLAG_SECURE 干预靶页(E-SEC-01..03 / E-SF-01 / L-PRC-01):
 * 按用例设置安全窗口标志后保持前台,供 ADB 侧 screencap 像素方差断言。
 */
class SecureActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val case = intent.getStringExtra(EXTRA_CASE) ?: "E-SEC-01"
        when (case) {
            "E-SEC-01", "L-PRC-01" -> {
                window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
                Tt.result(case, "path" to "setFlags", "flagsHasSecure" to hasSecure().toString())
            }
            "E-SEC-02" -> {
                window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                Tt.result(case, "path" to "addFlags", "flagsHasSecure" to hasSecure().toString())
            }
            "E-SEC-03" -> {
                window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                Tt.result(case, "path" to "add+clear", "flagsHasSecure" to hasSecure().toString())
            }
            "E-SF-01" -> {
                window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                val s: Any = Class.forName("android.view.Surface").getConstructor().newInstance()
                val canvas = runCatching {
                    val m = s.javaClass.getMethod("lockCanvas", android.graphics.Rect::class.java)
                    m.invoke(s, null as android.graphics.Rect?) as? android.graphics.Canvas
                }.getOrNull()
                runCatching {
                    val m = s.javaClass.getMethod("unlockCanvasAndPost", android.graphics.Canvas::class.java)
                    m.invoke(s, canvas)
                }
                runCatching { s.javaClass.getMethod("release").invoke(s) }
                Tt.result(case, "canvas" to (canvas != null).toString(), "flagsHasSecure" to hasSecure().toString())
            }
        }
    }

    private fun hasSecure(): Boolean =
        window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
}
