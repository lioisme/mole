package com.lioisme.modev.testtarget.cases

import android.content.Context
import com.lioisme.modev.testtarget.SmokeProbe
import com.lioisme.modev.testtarget.Tt

/**
 * L-SMOKE-01:caller-classloader 帧语义完整性冒烟(D-17 防回归)。
 * 单参 Class.forName 依赖 Reflection.getCallerClass() 取调用方 loader;
 * 任何钩子若以桥接反射帧替换真实调用方(如 LSPlant 钩 java.lang.Class.forName),
 * 此处将以错误 loader 解析 → ClassNotFoundException → 用例失败。
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
            else -> Tt.result(case, "error" to "unknown-case")
        }
    }
}
