package com.lioisme.modev.domain.server

import kotlinx.coroutines.runBlocking
import java.net.ServerSocket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 端口可用性校验 JVM 直测(V42 T3,PortValidator 纯 Kotlin 零 Android 依赖):
 * 四分支静态闸门 + 探测两用例(真实 ServerSocket 占位)。
 * 探测语义与生产一致:reuseAddress=true(TIME_WAIT 不假阳性)、
 * 自身当前端口豁免、探测地址随统一外部访问开关。
 */
class PortValidatorTest {

    private suspend fun check(
        candidate: Int,
        selfPort: Int = 1511,
        otherPort: Int? = 1510,
        externalAccess: Boolean = false,
    ): PortValidator.PortCheck =
        PortValidator.check(
            candidate = candidate,
            selfPort = selfPort,
            selfLabel = "wire",
            otherPort = otherPort,
            otherLabel = "MCP",
            externalAccess = externalAccess,
        )

    @Test
    fun outOfRange_rejected() {
        runBlocking {
            assertEquals(PortValidator.PortCheck.OutOfRange, check(candidate = 0))
            assertEquals(PortValidator.PortCheck.OutOfRange, check(candidate = 65536))
        }
    }

    @Test
    fun privilegedPort_rejected() {
        runBlocking {
            assertEquals(PortValidator.PortCheck.Privileged, check(candidate = 1))
            assertEquals(PortValidator.PortCheck.Privileged, check(candidate = 80))
            assertEquals(PortValidator.PortCheck.Privileged, check(candidate = 1023))
        }
    }

    @Test
    fun conflictWithOtherService_rejected() {
        runBlocking {
            val result = check(candidate = 1510, otherPort = 1510)
            assertTrue("须为 Conflict(MCP),实际 $result", result is PortValidator.PortCheck.Conflict)
            assertEquals("MCP", (result as PortValidator.PortCheck.Conflict).otherLabel)
        }
    }

    @Test
    fun selfPort_exemptFromProbe_available() {
        // 重存自身当前端口 = 合法 no-op:即使该端口正被自己监听(探测必假阳性)也直接放行。
        runBlocking {
            val server = ServerSocket(0).also { it.reuseAddress = true }
            try {
                val selfPort = server.localPort
                assertEquals(
                    PortValidator.PortCheck.Available,
                    check(candidate = selfPort, selfPort = selfPort, otherPort = null),
                )
            } finally {
                server.close()
            }
        }
    }

    @Test
    fun occupiedPort_detectedAsInUse() {
        runBlocking {
            val server = ServerSocket(0).also { it.reuseAddress = true }
            try {
                val occupied = server.localPort
                assertEquals(
                    PortValidator.PortCheck.InUse,
                    check(candidate = occupied, selfPort = -1, otherPort = null),
                )
            } finally {
                server.close()
            }
        }
    }

    @Test
    fun freePort_available() {
        runBlocking {
            // 预占后立即关闭:取一个当前空闲且不落 TIME_WAIT 的端口样本。
            val port = ServerSocket(0).use { it.localPort }
            assertEquals(
                PortValidator.PortCheck.Available,
                check(candidate = port, selfPort = -1, otherPort = null),
            )
        }
    }

    @Test
    fun probeAddress_followsExternalAccess() {
        // 0.0.0.0 探测:占用判定与回环一致(同端口语义),仅验证开关路径不抛异常。
        runBlocking {
            val server = ServerSocket(0).also { it.reuseAddress = true }
            try {
                val occupied = server.localPort
                assertEquals(
                    PortValidator.PortCheck.InUse,
                    check(candidate = occupied, selfPort = -1, otherPort = null, externalAccess = true),
                )
            } finally {
                server.close()
            }
        }
    }
}
