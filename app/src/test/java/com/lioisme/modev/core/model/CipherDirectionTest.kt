package com.lioisme.modev.core.model

import javax.crypto.Cipher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** CipherDirection 模式映射(ADR-5,回归 P5-2):展示层唯一消费路径。 */
class CipherDirectionTest {

    @Test
    fun `modes match JCA constants`() {
        assertEquals(Cipher.ENCRYPT_MODE, CipherDirection.ENCRYPT.mode)
        assertEquals(Cipher.DECRYPT_MODE, CipherDirection.DECRYPT.mode)
        assertEquals(Cipher.WRAP_MODE, CipherDirection.WRAP.mode)
        assertEquals(Cipher.UNWRAP_MODE, CipherDirection.UNWRAP.mode)
    }

    @Test
    fun `fromMode resolves all known values`() {
        assertEquals(CipherDirection.ENCRYPT, CipherDirection.fromMode(Cipher.ENCRYPT_MODE))
        assertEquals(CipherDirection.DECRYPT, CipherDirection.fromMode(Cipher.DECRYPT_MODE))
        assertEquals(CipherDirection.WRAP, CipherDirection.fromMode(Cipher.WRAP_MODE))
        assertEquals(CipherDirection.UNWRAP, CipherDirection.fromMode(Cipher.UNWRAP_MODE))
    }

    @Test
    fun `fromMode returns null for unknown or null mode`() {
        assertNull(CipherDirection.fromMode(99))
        assertNull(CipherDirection.fromMode(0))
        assertNull(CipherDirection.fromMode(null))
    }

    @Test
    fun `enum entries are stable in declaration order`() {
        assertEquals(
            listOf(CipherDirection.ENCRYPT, CipherDirection.DECRYPT, CipherDirection.WRAP, CipherDirection.UNWRAP),
            CipherDirection.entries.toList(),
        )
    }
}
