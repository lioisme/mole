package com.lioisme.modev.testtarget

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.lioisme.modev.testtarget.cases.BehaviorCases
import com.lioisme.modev.testtarget.cases.CryptoCases
import com.lioisme.modev.testtarget.cases.DbCases
import com.lioisme.modev.testtarget.cases.DeviceEnvCases
import com.lioisme.modev.testtarget.cases.NetworkCases
import com.lioisme.modev.testtarget.cases.PrefsCases
import com.lioisme.modev.testtarget.cases.ProcessCases
import com.lioisme.modev.testtarget.cases.SystemCases
import com.lioisme.modev.testtarget.cases.UiCases

/**
 * 用例驱动器:单 Activity + intent 参数(domain/case/keep)幂等执行。
 * 后台域(crypto/db/behavior/prefs/network/deviceenv/system)在工作线程运行;
 * UI/弹窗/进程干预域在主线程;webview/secure 域转发到专用 Activity。
 */
class TriggerActivity : Activity() {

    private val main = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dispatch(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        dispatch(intent)
    }

    private fun dispatch(intent: Intent) {
        val domain = intent.getStringExtra(EXTRA_DOMAIN)
        val case = intent.getStringExtra(EXTRA_CASE)
        val keep = intent.getBooleanExtra(EXTRA_KEEP, false)

        if (domain == "list") {
            Tt.result("REGISTRY", "cases" to TestCases.ALL.joinToString(","))
            finish()
            return
        }
        if (case == null) {
            Tt.result("DISPATCH", "error" to "missing-case")
            finish()
            return
        }

        when {
            domain == "webview" -> {
                startActivity(Intent(this, WvActivity::class.java).apply {
                    putExtra(EXTRA_CASE, case)
                })
                finish()
            }
            domain == "enhance-secure" -> {
                startActivity(Intent(this, SecureActivity::class.java).apply {
                    putExtra(EXTRA_CASE, case)
                })
                finish()
            }
            case == "C-MULTI-01" -> {
                startActivity(Intent(this, SubActivity::class.java))
                finish()
            }
            domain == "ui" || domain == "enhance-dialog" -> {
                main.post {
                    runCatching { UiCases.run(this@TriggerActivity, case) }
                        .onFailure { Tt.result(case, "error" to it.javaClass.simpleName + ":" + it.message) }
                    if (!keep) main.postDelayed({ finish() }, 800)
                }
            }
            domain == "enhance-proc" -> {
                main.post {
                    runCatching { ProcessCases.run(this@TriggerActivity, case) }
                        .onFailure { Tt.result(case, "error" to it.javaClass.simpleName + ":" + it.message) }
                    if (!keep) main.postDelayed({ finish() }, 2500)
                }
            }
            else -> {
                Thread {
                    runCatching {
                        when (domain) {
                            "crypto" -> CryptoCases.run(applicationContext, case)
                            "db" -> DbCases.run(applicationContext, case)
                            "behavior" -> BehaviorCases.run(applicationContext, case)
                            "prefs" -> PrefsCases.run(applicationContext, case)
                            "network", "enhance-ssl", "enhance-proxy", "enhance-vpn" ->
                                NetworkCases.run(applicationContext, case)
                            "deviceenv", "enhance-root", "enhance-xposed", "enhance-emulator", "enhance-procfile" ->
                                DeviceEnvCases.run(applicationContext, case)
                            "system" -> SystemCases.run(applicationContext, case)
                            "linkage" -> when (case) {
                                "C-GATE-01" -> DbCases.run(applicationContext, "L-DB-11")
                                "C-THR-01" -> BehaviorCases.run(applicationContext, "L-BHV-12")
                                else -> Tt.result(case, "error" to "unknown-linkage")
                            }
                            else -> Tt.result(case, "error" to "unknown-domain:$domain")
                        }
                    }.onFailure {
                        Tt.result(case, "error" to (it.javaClass.simpleName + ":" + it.message))
                    }
                    if (!keep) main.postDelayed({ finish() }, 200)
                }.start()
            }
        }
    }

    companion object {
        const val EXTRA_DOMAIN = "domain"
        const val EXTRA_CASE = "case"
        const val EXTRA_KEEP = "keep"
    }
}
