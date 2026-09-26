package com.lioisme.modev.domain.server

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.BindException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.SocketException

/**
 * 端口可用性校验:编辑链路的「是否可用」三闸 + 范围防御。
 * domain 层零 Android 依赖(JVM 直测);P5-1 边界内——UI 经 ServicesViewModel
 * 消费,禁 import data.server 实现类型。
 *
 * 校验序:范围 → 特权(<1024 硬错误,Android 应用进程绑定必 EACCES)→
 * 双服务冲突(wire ≠ MCP)→ 自身当前端口豁免(重存原值 = 合法 no-op)→
 * 占用探测(ServerSocket 试绑;reuseAddress=true 与 ConfigServer 自绑语义一致,
 * TIME_WAIT 不假阳性、活跃监听必 EADDRINUSE)。
 * 探测为即时快照非终审:最终裁决 = 真实绑定结果(bindError 观测面兜底)。
 */
object PortValidator {

    /** 单位:特权端口阈值(RFC 6335;Android 非特权进程 <1024 绑定必失败)。 */
    private const val PRIVILEGED_PORT_MAX = 1023

    /** 校验结果(封闭集合,UI 按分型渲染行内错误)。 */
    sealed interface PortCheck {
        /** 可用(含自身当前端口豁免:重存原值直接放行)。 */
        data object Available : PortCheck

        /** 范围外(防御闸,sheet 已滤,正常不触达)。 */
        data object OutOfRange : PortCheck

        /** 特权端口(<1024,应用进程无绑定权限)。 */
        data object Privileged : PortCheck

        /** 与另一服务(wire/MCP)端口冲突。 */
        data class Conflict(val otherLabel: String) : PortCheck

        /** 已被其他进程占用(试绑 EADDRINUSE/BindException)。 */
        data object InUse : PortCheck
    }

    /**
     * 校验候选端口。
     *
     * @param candidate 候选端口
     * @param selfPort 本服务当前端口(相等即豁免探测,直接 Available)
     * @param selfLabel 本服务名(探测地址语义不区分,预留诊断)
     * @param otherPort 对端服务端口(无对端 = null)
     * @param otherLabel 对端服务名(冲突错误分型文案)
     * @param externalAccess 统一外部访问开关(探测地址随之:0.0.0.0/127.0.0.1,
     *   与真实绑定地址语义一致,避免「探测回环通过、外绑失败」的口径差)
     */
    suspend fun check(
        candidate: Int,
        selfPort: Int,
        selfLabel: String,
        otherPort: Int?,
        otherLabel: String,
        externalAccess: Boolean,
    ): PortCheck {
        if (candidate !in 1..65535) return PortCheck.OutOfRange
        if (candidate <= PRIVILEGED_PORT_MAX) return PortCheck.Privileged
        if (otherPort != null && candidate == otherPort) {
            return PortCheck.Conflict(otherLabel)
        }
        if (candidate == selfPort) return PortCheck.Available
        return probe(candidate, externalAccess)
    }

    /** 试绑探测(IO 线程;成功即关,BindException/SocketException = 占用)。 */
    private suspend fun probe(candidate: Int, externalAccess: Boolean): PortCheck =
        withContext(Dispatchers.IO) {
            try {
                ServerSocket().apply { reuseAddress = true }.use { socket ->
                    val address = if (externalAccess) {
                        ConnectionMonitor.BIND_ALL_INTERFACES
                    } else {
                        ConnectionMonitor.BIND_LOOPBACK
                    }
                    socket.bind(InetSocketAddress(address, candidate))
                }
                PortCheck.Available
            } catch (e: BindException) {
                // EADDRINUSE 主形态(部分内核经 SocketException("Address already in use"))。
                PortCheck.InUse
            } catch (e: SocketException) {
                PortCheck.InUse
            }
        }
}
