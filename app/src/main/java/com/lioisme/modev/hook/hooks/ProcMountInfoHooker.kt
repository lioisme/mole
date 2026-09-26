package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.DeviceEnvKind
import com.lioisme.modev.core.model.DeviceEnvOperation
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.ProbeContext

/**
 * /proc/self/mountinfo 行过滤(V35 momo 增强;V44 T2.1 收编):readLine 行事件经
 * [ProcScanBroadcaster] 单一广播分派(旧多注册 + 构造器零门控形态退役)。
 * 过滤 Magisk 挂载痕迹行(命中即吞行)。
 */
class ProcMountInfoHooker(
    context: ProbeContext,
    private val procScan: ProcScanBroadcaster,
) : DeviceEnvBaseHooker(context), ProcScanBroadcaster.Handler {

    override val kind: DeviceEnvKind = DeviceEnvKind.PROC_MOUNT_INFO

    override val paths: Set<ProcScanBroadcaster.ProcPath> =
        setOf(ProcScanBroadcaster.ProcPath.MOUNT_INFO)

    override fun install() {
        procScan.addHandler(this)
    }

    /** 行事件(master 已由协调器粗门;此处自判干预 kind 门控——FeatureGate 红线)。 */
    override fun onLine(invocation: Invocation, path: ProcScanBroadcaster.ProcPath, line: String) {
        if (!featureOn(DeviceEnvKind.PROC_MOUNT_INFO)) return
        if (isMagiskMountLine(line)) {
            invocation.setResult(null)
            emit(
                kind = DeviceEnvKind.PROC_MOUNT_INFO,
                operation = DeviceEnvOperation.PROC_MOUNT_READ,
                originalValue = line,
                targetPath = PROC_MOUNT_INFO_PATH,
                detectionPattern = extractMountKeyword(line),
                interventionAction = ACTION_BLOCKED,
            )
        }
    }

    /** 生效掩码词表:内置基线 ∪ 用户追加关键词(extraMaskKeywords)。 */
    private fun maskKeywords(): List<String> =
        if (config().feature.extraMaskKeywords.isEmpty()) {
            MAGISK_MOUNT_KEYWORDS
        } else {
            MAGISK_MOUNT_KEYWORDS + config().feature.extraMaskKeywords.map { it.lowercase() }
        }

    private fun isMagiskMountLine(line: String): Boolean {
        val lower = line.lowercase()
        return maskKeywords().any { lower.contains(it) }
    }

    private fun extractMountKeyword(line: String): String? {
        val lower = line.lowercase()
        return maskKeywords().firstOrNull { lower.contains(it) }
    }

    companion object {
        private const val PROC_MOUNT_INFO_PATH = "/proc/self/mountinfo"
        private const val ACTION_BLOCKED = "BLOCKED"

        private val MAGISK_MOUNT_KEYWORDS = listOf(
            "magisk",
            "/sbin/magisk",
            "tmpfs on /sbin",
            "/data/adb/modules",
            "mirror",
            "modbond",
            "kmodules",
        )
    }
}
