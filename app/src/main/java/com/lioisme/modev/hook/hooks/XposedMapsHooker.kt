package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.DeviceEnvKind
import com.lioisme.modev.core.model.DeviceEnvOperation
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.ProbeContext

/**
 * /proc/self/maps 扫描干预(V31;V44 T2.1 收编):readLine 行事件经
 * [ProcScanBroadcaster] 单一广播分派(旧形态 FileReader/BufferedReader/readLine
 * 三方法各自注册且构造器回调零门控——全进程每次 new FileReader 付 canonicalPath,
 * 开关全关照付;多注册在 Vector 下互相遮蔽)。
 *
 * 拦截 BufferedReader.readLine,当读取 /proc/[pid]/maps 文件时,
 * 过滤包含 Xposed/Magisk/Frida 等关键词的行(命中即 setResult(null) 吞行)。
 *
 * 设计决策:不反射 FileReader 内部字段;路径识别统一在
 * [ProcScanBroadcaster.classify] 廉价判定,本处理器仅承载行过滤逻辑。
 */
class XposedMapsHooker(
    context: ProbeContext,
    private val procScan: ProcScanBroadcaster,
) : DeviceEnvBaseHooker(context), ProcScanBroadcaster.Handler {

    override val kind: DeviceEnvKind = DeviceEnvKind.XPOSED_MAPS

    override val paths: Set<ProcScanBroadcaster.ProcPath> =
        setOf(ProcScanBroadcaster.ProcPath.MAPS)

    override fun install() {
        procScan.addHandler(this)
    }

    /** 行事件(master 已由协调器粗门;此处自判干预 kind 门控——FeatureGate 红线)。 */
    override fun onLine(invocation: Invocation, path: ProcScanBroadcaster.ProcPath, line: String) {
        if (!featureOn(DeviceEnvKind.XPOSED_MAPS)) return
        if (isFilteredLine(line)) {
            invocation.setResult(null)
            emit(
                kind = DeviceEnvKind.XPOSED_MAPS,
                operation = DeviceEnvOperation.READ_MAPS,
                originalValue = line,
                targetPath = PROC_MAPS_PATH,
                detectionPattern = extractKeyword(line),
            )
        }
    }

    /** 生效掩码词表:内置基线 ∪ 用户追加关键词(extraMaskKeywords)。 */
    private fun maskKeywords(): List<String> =
        if (config().feature.extraMaskKeywords.isEmpty()) {
            MAPS_KEYWORDS
        } else {
            MAPS_KEYWORDS + config().feature.extraMaskKeywords.map { it.lowercase() }
        }

    private fun isFilteredLine(line: String): Boolean {
        val lower = line.lowercase()
        return maskKeywords().any { lower.contains(it) }
    }

    private fun extractKeyword(line: String): String? {
        val lower = line.lowercase()
        return maskKeywords().firstOrNull { lower.contains(it) }
    }

    companion object {
        private const val PROC_MAPS_PATH = "/proc/self/maps"

        private val MAPS_KEYWORDS = listOf(
            "xposed", "lspd", "lsplant", "edxposed",
            "magisk", "zygisk", "zygisknext",
            "supersu", "superuser", "chainfire",
            "substrate", "frida", "gadget", "modev",
        )
    }
}
