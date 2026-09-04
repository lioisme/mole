package com.lioisme.modev.testtarget.cases

import android.content.Context
import com.lioisme.modev.testtarget.Tt

/** L-PRF-*:偏好监听触发器(SharedPreferences 构造 / Get 全家桶 / Editor put 系列/remove/clear)。 */
object PrefsCases {

    private fun fresh(ctx: Context, tag: String) =
        ctx.getSharedPreferences("tt_prefs_$tag", Context.MODE_PRIVATE)

    fun run(ctx: Context, case: String) {
        when (case) {
            "L-PRF-01" -> {
                val sp = fresh(ctx, "open-${System.nanoTime()}")
                sp.edit().putString("seed", "1").commit()
                Tt.result(case, "opened" to "true")
            }
            "L-PRF-02" -> {
                val sp = fresh(ctx, "put")
                sp.edit()
                    .putString("k_str", "v")
                    .putInt("k_int", 42)
                    .putLong("k_long", 42L)
                    .putFloat("k_float", 4.2f)
                    .putBoolean("k_bool", true)
                    .putStringSet("k_set", setOf("a", "b"))
                    .commit()
                sp.edit().putString("k_apply", "applied").apply()
                Tt.result(case, "puts" to "7")
            }
            "L-PRF-03" -> {
                val sp = fresh(ctx, "get")
                sp.getString("k_str", null)
                sp.getInt("k_int", 0)
                sp.getLong("k_long", 0L)
                sp.getFloat("k_float", 0f)
                sp.getBoolean("k_bool", false)
                sp.getStringSet("k_set", null)
                sp.contains("k_str")
                sp.all
                Tt.result(case, "gets" to "8")
            }
            "L-PRF-04" -> {
                val sp = fresh(ctx, "remove")
                sp.edit().putString("gone", "1").commit()
                sp.edit().remove("gone").commit()
                Tt.result(case, "removed" to "true")
            }
            "L-PRF-05" -> {
                val sp = fresh(ctx, "clear")
                sp.edit().putString("a", "1").commit()
                sp.edit().clear().commit()
                Tt.result(case, "cleared" to (sp.all.isEmpty()).toString())
            }
            "L-PRF-06" -> {
                // 多偏好文件独立性
                fresh(ctx, "multi-a").edit().putString("x", "a").commit()
                fresh(ctx, "multi-b").edit().putString("x", "b").commit()
                Tt.result(case, "files" to "2")
            }
            else -> Tt.result(case, "error" to "unknown-case")
        }
    }
}
