package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.DeviceEnvKind
import com.lioisme.modev.core.model.DeviceEnvOperation
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.ProbeContext

/**
 * /proc/self/status 行过滤(V35 momo 增强;V44 T2.1 收编):readLine 行事件经
 * [ProcScanBroadcaster] 单一广播分派(旧多注册 + 构造器零门控形态退役)。
 *
 * 拦截读取 /proc/[pid]/status 的行,掩蔽调试器 attach 痕迹:
 * - TracerPid != 0 → 覆写为 "TracerPid:\t0"(检测到 Frida/gdb attach)。
 *
 * 与 XposedMapsHooker/ProcNetHooker/ProcMountInfoHooker/ProcCgroupHooker 同构:
 * 仅承载行处理逻辑,路径跟踪机制在协调器单点实现。
 */
class ProcStatusHooker(
    context: ProbeContext,
    private val procScan: ProcScanBroadcaster,
) : DeviceEnvBaseHooker(context), ProcScanBroadcaster.Handler {

    override val kind: DeviceEnvKind = DeviceEnvKind.PROC_STATUS

    override val paths: Set<ProcScanBroadcaster.ProcPath> =
        setOf(ProcScanBroadcaster.ProcPath.STATUS)

    override fun install() {
        procScan.addHandler(this)
    }

    /** 行事件(master 已由协调器粗门;此处自判干预 kind 门控——FeatureGate 红线)。 */
    override fun onLine(invocation: Invocation, path: ProcScanBroadcaster.ProcPath, line: String) {
        if (!featureOn(DeviceEnvKind.PROC_STATUS)) return
        if (line.startsWith("TracerPid:")) {
            val pidValue = line.substringAfter(":").trim()
            if (pidValue != "0") {
                invocation.setResult("TracerPid:\t0")
                emit(
                    kind = DeviceEnvKind.PROC_STATUS,
                    operation = DeviceEnvOperation.PROC_STATUS_READ,
                    originalValue = line,
                    maskedValue = "TracerPid:\t0",
                    targetPath = PROC_STATUS_PATH,
                    detectionPattern = "tracer-pid",
                    interventionAction = ACTION_BLOCKED,
                )
            }
        }
    }

    companion object {
        private const val PROC_STATUS_PATH = "/proc/self/status"
        private const val ACTION_BLOCKED = "BLOCKED"
    }
}
