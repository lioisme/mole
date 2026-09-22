package com.lioisme.modev.testtarget.cases

import android.content.Context
import com.lioisme.modev.testtarget.SmokeProbe
import com.lioisme.modev.testtarget.Tt

/**
 * L-SMOKE-01:caller-classloader 帧语义完整性冒烟(D-17 防回归)。
 * 单参 Class.forName 依赖 Reflection.getCallerClass() 取调用方 loader;
 * 任何钩子若以桥接反射帧替换真实调用方(如 LSPlant 钩 java.lang.Class.forName),
 * 此处将以错误 loader 解析 → ClassNotFoundException → 用例失败。
 *
 * L-PERF-01(V43 T6):热路径「零工作」开销预算探针——开关全关档案下测量
 * File.exists / SharedPreferences.getAll / System.getProperty / loadClass
 * 单次均摊耗时(µs),供 23-run-smoke 扩展段与卸载基线同法比对;
 * 门控倒挂(V2.1–V2.4)任何回归都会放大单次成本被此断言捕获。
 */
object SmokeCases {

    fun run(ctx: Context, case: String) {
        when (case) {
            "L-SMOKE-01" -> {
                val cls = runCatching { Class.forName(SmokeProbe::class.java.name) }
                val explicit = runCatching {
                    Class.forName(SmokeProbe::class.java.name, false, ctx.classLoader)
                }
                val inst = cls.getOrNull()?.let { c -> runCatching { c.getDeclaredConstructor().newInstance() } }
                Tt.result(
                    case,
                    "forNameSingle" to if (cls.isSuccess) "ok" else "fail:${cls.exceptionOrNull()?.javaClass?.simpleName}",
                    "forNameExplicit" to if (explicit.isSuccess) "ok" else "fail",
                    "instantiate" to if (inst?.isSuccess == true) "ok" else "fail",
                )
            }
            "L-PERF-01" -> {
                val sp = ctx.getSharedPreferences("modev_perf_probe", Context.MODE_PRIVATE)
                sp.edit().putString("k", "v").putInt("n", 1).apply()
                fun phase(
                    name: String,
                    iters: Int,
                    body: (Int) -> Unit,
                ): Pair<String, String> {
                    repeat(iters.coerceAtMost(50)) { body(it) } // 预热(钩子惰性安装/内联缓存)
                    val t0 = System.nanoTime()
                    repeat(iters) { body(it) }
                    val us = (System.nanoTime() - t0) / 1000 / iters
                    return name to "${us}us"
                }
                val results = listOf(
                    phase("exists", 400) { java.io.File("/modev_probe_absent_$it").exists() },
                    phase("getAll", 80) { sp.all },
                    phase("getProperty", 800) { System.getProperty("modev.probe.key.$it") },
                    phase("loadClass", 2000) {
                        runCatching { ctx.classLoader.loadClass("java.lang.String") }
                    },
                )
                Tt.result(case, *results.toTypedArray())
            }
            else -> Tt.result(case, "error" to "unknown-case")
        }
    }
}
