package com.lioisme.modev.domain.server

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * 端口写闸单测(V45 T2.4,DUAL-3 根因收口):
 * 「是否可写」的裁决必须在**方法层**发生,UI 预检与实现方落盘前置调用同一函数 ——
 * 本测试锁定 [PortWriteGuard] 的三态语义(放行 / 抛分型异常 / 自身端口豁免),
 * 使「UI 硬拒而 MCP 静默 clamp」在类型上不可再现。
 *
 * 占用探测需真实 socket,故各用例只取**必然在探测之前就被拒**的输入
 * (特权端口 / 双服务冲突)或**豁免直返**的输入,不依赖端口占用环境(免假失败)。
 */
class PortWriteGuardTest {

    private fun check(candidate: Int, self: Int, other: Int?) = runBlocking {
        PortWriteGuard.check(
            candidate = candidate,
            selfPort = self,
            selfLabel = "wire",
            otherPort = other,
            otherLabel = "MCP",
            externalAccess = false,
        )
    }

    private fun require(candidate: Int, self: Int, other: Int?) = runBlocking {
        PortWriteGuard.requireAvailable(
            candidate = candidate,
            selfPort = self,
            selfLabel = "wire",
            otherPort = other,
            otherLabel = "MCP",
            externalAccess = false,
        )
    }

    @Test
    fun `privileged port is rejected before any probing`() {
        assertEquals(PortValidator.PortCheck.Privileged, check(candidate = 80, self = 1511, other = 1510))
    }

    @Test
    fun `conflict with the other service is rejected`() {
        assertEquals(PortValidator.PortCheck.Conflict("MCP"), check(candidate = 1510, self = 1511, other = 1510))
    }

    @Test
    fun `own current port is exempted without probing`() {
        // 重存原值 = 合法 no-op(即使对端同值也不冲突:豁免先于冲突?否 ——
        // 校验序为「范围 → 特权 → 冲突 → 自身豁免」,故对端为空时才走豁免分支)
        assertEquals(PortValidator.PortCheck.Available, check(candidate = 1511, self = 1511, other = null))
    }

    @Test
    fun `requireAvailable throws typed rejection`() {
        val error = assertThrows(PortRejectedException::class.java) { require(22, self = 1511, other = 1510) }
        assertEquals(PortValidator.PortCheck.Privileged, error.check)
    }

    @Test
    fun `requireAvailable passes the exempt self port`() {
        require(1511, self = 1511, other = null)
    }
}
