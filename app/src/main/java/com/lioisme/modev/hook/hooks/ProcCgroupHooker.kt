package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.DeviceEnvKind
import com.lioisme.modev.core.model.DeviceEnvOperation
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.ProbeContext

/**
 * /proc/self/cgroup 行过滤(V35 momo 增强;V44 T2.1 收编):readLine 行事件经
 * [ProcScanBroadcaster] 单一广播分派(旧多注册 + 构造器零门控形态退役)。
 * 过滤含 Magisk/Zygisk/LSPosed 痕迹的 cgroup 行(命中即吞行)。
 */
class ProcCgroupHooker(
    context: ProbeContext,
    private val procScan: ProcScanBroadcaster,
) : DeviceEnvBaseHooker(context), ProcScanBroadcaster.Handler {

    override val kind: DeviceEnvKind = DeviceEnvKind.PROC_CGROUP

    override val paths: Set<ProcScanBroadcaster.ProcPath> =
        setOf(ProcScanBroadcaster.ProcPath.CGROUP)

    override fun install() {
        procScan.addHandler(this)
    }

    /** 行事件(master 已由协调器粗门;此处自判干预 kind 门控——FeatureGate 红线)。 */
    override fun onLine(invocation: Invocation, path: ProcScanBroadcaster.ProcPath, line: String) {
        if (!featureOn(DeviceEnvKind.PROC_CGROUP)) return
        if (isMagiskCgroupLine(line)) {
            invocation.setResult(null)
            emit(
                kind = DeviceEnvKind.PROC_CGROUP,
                operation = DeviceEnvOperation.PROC_CGROUP_READ,
                originalValue = line,
                targetPath = PROC_CGROUP_PATH,
                detectionPattern = extractCgroupKeyword(line),
                interventionAction = ACTION_BLOCKED,
            )
        }
    }

    /** 生效掩码词表(V37 T2.4):内置基线 ∪ 用户追加关键词(extraMaskKeywords)。 */
    private fun maskKeywords(): List<String> =
        if (config().feature.extraMaskKeywords.isEmpty()) {
            MAGISK_CGROUP_KEYWORDS
        } else {
            MAGISK_CGROUP_KEYWORDS + config().feature.extraMaskKeywords.map { it.lowercase() }
        }

    private fun isMagiskCgroupLine(line: String): Boolean {
        val lower = line.lowercase()
        return maskKeywords().any { lower.contains(it) }
    }

    private fun extractCgroupKeyword(line: String): String? {
        val lower = line.lowercase()
        return maskKeywords().firstOrNull { lower.contains(it) }
    }

    companion object {
        private const val PROC_CGROUP_PATH = "/proc/self/cgroup"
        private const val ACTION_BLOCKED = "BLOCKED"

        private val MAGISK_CGROUP_KEYWORDS = listOf(
            "magisk",
            "zygisk",
            "lspd",
            "lsposed",
        )
    }
}
