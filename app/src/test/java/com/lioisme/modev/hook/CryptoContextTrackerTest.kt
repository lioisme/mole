package com.lioisme.modev.hook

import com.lioisme.modev.core.util.BytesView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 上下文跟踪器 v2:实例存活期唯一 ID / clone 传播 / reset 语义(纯 JVM)。 */
class CryptoContextTrackerTest {

    @Test
    fun `identity is stable per instance and unique across instances`() {
        val a = Any()
        val b = Any()
        val idA1 = CryptoContextTracker.identity(a)
        val idA2 = CryptoContextTracker.identity(a)
        val idB = CryptoContextTracker.identity(b)
        assertEquals(idA1, idA2)
        assertNotEquals(idA1, idB)
    }

    @Test
    fun `context is shared per instance`() {
        val instance = Any()
        val first = CryptoContextTracker.contextFor(instance)
        first.algorithm = "AES/CBC/PKCS5Padding"
        first.inputTotal = 42L
        val second = CryptoContextTracker.contextFor(instance)
        assertEquals("AES/CBC/PKCS5Padding", second.algorithm)
        assertEquals(42L, second.inputTotal)
    }

    @Test
    fun `clone propagates fields with a new identity`() {
        val src = Any()
        val ctx = CryptoContextTracker.contextFor(src)
        ctx.algorithm = "AES/GCM/NoPadding"
        ctx.provider = "AndroidOpenSSL"
        ctx.key = BytesView.of(byteArrayOf(1, 2, 3))
        ctx.iv = BytesView.of(byteArrayOf(9))
        ctx.salt = BytesView.of(byteArrayOf(8))
        ctx.aad = BytesView.of(byteArrayOf(7))
        ctx.aadData = byteArrayOf(7)
        ctx.publicKey = BytesView.of(byteArrayOf(6))
        ctx.gcmTagLen = 128
        ctx.iterationCount = 1000
        ctx.keyLength = 256
        ctx.keysize = 2048
        ctx.lastInitIv = ctx.iv
        ctx.verified = true
        ctx.inputTotal = 5L
        ctx.outputTotal = 6L

        val dst = Any()
        CryptoContextTracker.cloneContext(src, dst)
        val copy = CryptoContextTracker.contextFor(dst)

        assertEquals(ctx.algorithm, copy.algorithm)
        assertEquals(ctx.provider, copy.provider)
        assertEquals(ctx.key, copy.key)
        assertEquals(ctx.iv, copy.iv)
        assertEquals(ctx.salt, copy.salt)
        assertEquals(ctx.aad, copy.aad)
        assertEquals(ctx.publicKey, copy.publicKey)
        assertEquals(128, copy.gcmTagLen)
        assertEquals(1000, copy.iterationCount)
        assertEquals(256, copy.keyLength)
        assertEquals(2048, copy.keysize)
        assertEquals(ctx.lastInitIv, copy.lastInitIv)
        assertEquals(true, copy.verified)
        // 累计量不随 clone 传播(新实例从零开始)
        assertEquals(0L, copy.inputTotal)
        assertEquals(0L, copy.outputTotal)
        // 新实例标识重新分配
        assertNotEquals(CryptoContextTracker.identity(src), CryptoContextTracker.identity(dst))
    }

    @Test
    fun `reset clears totals and aad but keeps key material`() {
        val instance = Any()
        val ctx = CryptoContextTracker.contextFor(instance)
        ctx.key = BytesView.of(byteArrayOf(1, 2, 3))
        ctx.iv = BytesView.of(byteArrayOf(4))
        ctx.inputTotal = 100L
        ctx.outputTotal = 200L
        ctx.aadData = byteArrayOf(5)
        ctx.aad = BytesView.of(byteArrayOf(5))

        CryptoContextTracker.reset(instance)

        assertEquals(0L, ctx.inputTotal)
        assertEquals(0L, ctx.outputTotal)
        assertNull(ctx.aadData)
        assertNull(ctx.aad)
        assertEquals("010203", ctx.key?.hex)
        assertEquals("04", ctx.iv?.hex)
    }

    @Test
    fun `reset on unknown instance is a no-op`() {
        CryptoContextTracker.reset(Any())
        assertTrue(true)
    }

    @Test
    fun `clone of unknown instance is a no-op`() {
        CryptoContextTracker.cloneContext(Any(), Any())
        assertFalse(CryptoContextTracker.contextFor(Any()).inputTotal > 0)
    }
}