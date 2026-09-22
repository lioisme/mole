package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.DeviceEnvKind
import com.lioisme.modev.core.model.DeviceEnvOperation
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.ProbeContext

/**
 * /proc/net/tcp 行过滤(V35 momo 增强;V44 T2.1 收编):readLine 行事件经
 * [ProcScanBroadcaster] 单一广播分派(旧多注册 + 构造器零门控形态退役)。
 *
 * momo 检测项覆盖:
 * - Frida 默认监听端口 27042/27043 → /proc/net/tcp 中出现对应 local_address 即吞行。
 */
class ProcNetHooker(
    context: ProbeContext,
    private val procScan: ProcScanBroadcaster,
) : DeviceEnvBaseHooker(context), ProcScanBroadcaster.Handler {

    override val kind: DeviceEnvKind = DeviceEnvKind.PROC_NET

    override val paths: Set<ProcScanBroadcaster.ProcPath> =
        setOf(ProcScanBroadcaster.ProcPath.NET)

    override fun install() {
        procScan.addHandler(this)
    }

    /** 行事件(master 已由协调器粗门;此处自判干预 kind 门控——FeatureGate 红线)。 */
    override fun onLine(invocation: Invocation, path: ProcScanBroadcaster.ProcPath, line: String) {
        if (!featureOn(DeviceEnvKind.PROC_NET)) return
        if (isFridaPortLine(line)) {
            invocation.setResult(null)
            emit(
                kind = DeviceEnvKind.PROC_NET,
                operation = DeviceEnvOperation.PROC_NET_READ,
                originalValue = line,
                targetPath = PROC_NET_TCP_PATH,
                detectionPattern = "frida-port",
                interventionAction = ACTION_BLOCKED,
            )
        }
    }

    /**
     * 判断 /proc/net/tcp 行是否包含 Frida 默认监听端口。
     * local_address 格式: "IP:PORT", port 为十六进制。
     * Frida 默认端口: 27042 (0x69A2), 27043 (0x69A3)
     */
    private fun isFridaPortLine(line: String): Boolean {
        if (line.isBlank() || line.startsWith("sl")) return false
        val parts = line.trim().split("\\s+".toRegex())
        if (parts.size < 2) return false
        val localAddress = parts[1]
        val portHex = localAddress.substringAfterLast(':', "")
        if (portHex.isEmpty()) return false
        val portValue = runCatching { portHex.toInt(16) }.getOrNull() ?: return false
        // V37 T2.4:内置 Frida 端口基线 ∪ 用户追加端口
        return FRIDA_PORTS.contains(portValue) || config().feature.extraFridaPorts.contains(portValue)
    }

    companion object {
        private const val PROC_NET_TCP_PATH = "/proc/net/tcp"
        private const val ACTION_BLOCKED = "BLOCKED"

        /** Frida 默认监听端口集合(十进制)。 */
        private val FRIDA_PORTS = setOf(27042, 27043)
    }
}
