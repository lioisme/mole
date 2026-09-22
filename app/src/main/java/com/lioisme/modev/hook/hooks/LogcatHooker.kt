package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.SystemKind
import com.lioisme.modev.core.model.SystemOperation
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.MethodHook
import com.lioisme.modev.hook.ProbeContext

/**
 * Logcat 捕获 Hooker(V33):hook android.util.Log 的 v/d/i/w/e 方法,
 * 记录 tag + 级别 + message(截断预览)。仅记录目标进程自身的日志输出。
 */
class LogcatHooker(
    context: ProbeContext,
) : SystemBaseHooker(context) {

    private val registry = BehaviorMethodRegistry.forName("android.util.Log")

    override fun install() {
        if (registry == null) return
        val levels = mapOf(
            "v" to SystemOperation.LOG_VERBOSE,
            "d" to SystemOperation.LOG_DEBUG,
            "i" to SystemOperation.LOG_INFO,
            "w" to SystemOperation.LOG_WARN,
            "e" to SystemOperation.LOG_ERROR,
        )
        for ((method, op) in levels) {
            val methods = registry.named(method)
            if (methods.isEmpty()) continue
            for (m in methods) {
                engine.hook(m, object : MethodHook() {
                    override fun after(invocation: Invocation) {
                        // V44 T2.2 门控先行:logcat 为极高频路径(每次 Log.x 一行),
                        // 取参/截断/字符串化全部排在门控之后。
                        if (!featureOn(SystemKind.LOGCAT)) return
                        val tag = invocation.getArg(0) as? String ?: return
                        val msg = invocation.getArg(1) as? String ?: return
                        if (msg.isBlank()) return
                        val max = config().maxBytesPerField
                        val preview = if (max > 0 && msg.length > max) msg.substring(0, max) else msg
                        emit(
                            kind = SystemKind.LOGCAT,
                            operation = op,
                            logcatTag = tag,
                            logcatLevel = method.uppercase(),
                            logcatMessage = preview,
                        )
                    }
                })
            }
        }
    }
}
