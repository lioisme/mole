package com.lioisme.modev.ui.detail

import com.lioisme.modev.R
import com.lioisme.modev.core.model.CryptoKind
import com.lioisme.modev.core.model.CryptoOperation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** V20 R4:数据流角色映射纯函数全分支。 */
class CryptoFlowRolesTest {

    private fun roles(
        kind: CryptoKind,
        operation: CryptoOperation,
        direction: Int? = null,
    ): Pair<Int?, Int?> = cryptoFlowRoles(kind, operation, direction)

    @Test
    fun cipher_encryptDirection_mapsPlaintextToCiphertext() {
        assertEquals(
            R.string.role_input_plaintext to R.string.role_output_ciphertext,
            roles(CryptoKind.CIPHER, CryptoOperation.DO_FINAL, 1),
        )
    }

    @Test
    fun cipher_decryptDirection_mapsCiphertextToPlaintext() {
        assertEquals(
            R.string.role_input_ciphertext to R.string.role_output_plaintext,
            roles(CryptoKind.CIPHER, CryptoOperation.DO_FINAL, 2),
        )
    }

    @Test
    fun cipher_wrapAndUnwrap_fallBackToGeneric() {
        assertNull(roles(CryptoKind.CIPHER, CryptoOperation.DO_FINAL, 3).first)
        assertNull(roles(CryptoKind.CIPHER, CryptoOperation.DO_FINAL, 4).second)
    }

    @Test
    fun cipher_missingDirection_usesOperationFallback() {
        assertEquals(
            R.string.role_input_plaintext to R.string.role_output_ciphertext,
            roles(CryptoKind.CIPHER, CryptoOperation.ENCRYPT),
        )
        assertEquals(
            R.string.role_input_ciphertext to R.string.role_output_plaintext,
            roles(CryptoKind.CIPHER, CryptoOperation.DECRYPT),
        )
    }

    // V50 孤儿整改:原 `webCrypto_usesOperationFallback` 的主语是死行 CryptoKind.WEB_CRYPTO
    // (已删除)。ENCRYPT/DECRYPT 现为 Cipher doFinal 的真实操作,其角色映射已由上方
    // `cipher_missingDirection_usesOperationFallback` 覆盖,不再另立同形用例。

    @Test
    fun messageDigest_digest_mapsMessageToDigest() {
        assertEquals(
            R.string.role_input_message to R.string.role_output_digest,
            roles(CryptoKind.MESSAGE_DIGEST, CryptoOperation.DIGEST),
        )
    }

    @Test
    fun mac_sign_mapsMessageToMac() {
        assertEquals(
            R.string.role_input_message to R.string.role_output_mac,
            roles(CryptoKind.MAC, CryptoOperation.SIGN),
        )
    }

    @Test
    fun signature_signAndVerify_mapMessageToSignatureOrVerify() {
        assertEquals(
            R.string.role_input_message to R.string.role_output_signature,
            roles(CryptoKind.SIGNATURE, CryptoOperation.SIGN),
        )
        assertEquals(
            R.string.role_input_message to R.string.role_output_verify,
            roles(CryptoKind.SIGNATURE, CryptoOperation.VERIFY),
        )
    }

    @Test
    fun base64_encodeAndDecode_mapDataAndBase64() {
        assertEquals(
            R.string.role_input_data to R.string.role_output_base64,
            roles(CryptoKind.BASE64, CryptoOperation.ENCODE),
        )
        assertEquals(
            R.string.role_input_base64 to R.string.role_output_data,
            roles(CryptoKind.BASE64, CryptoOperation.DECODE),
        )
    }

    @Test
    fun keyKinds_generateOutputsKey() {
        for (kind in listOf(CryptoKind.KEY_GENERATION, CryptoKind.KEY_FACTORY, CryptoKind.KEY_AGREEMENT)) {
            for (op in listOf(
                CryptoOperation.GENERATE,
                CryptoOperation.GENERATE_PRIVATE,
                CryptoOperation.GENERATE_PUBLIC,
                CryptoOperation.GENERATE_SECRET,
                CryptoOperation.CONSTRUCT,
                CryptoOperation.DO_PHASE,
            )) {
                assertNull("$kind $op 应无输入角色", roles(kind, op).first)
                assertEquals("$kind $op 输出应为密钥", R.string.role_output_key, roles(kind, op).second)
            }
        }
    }

    @Test
    fun keyParamKeyStoreRandom_fallBackToGeneric() {
        for (kind in listOf(CryptoKind.KEY_PARAM, CryptoKind.KEY_STORE, CryptoKind.RANDOM)) {
            assertNull(roles(kind, CryptoOperation.CONSTRUCT).first)
            assertNull(roles(kind, CryptoOperation.CONSTRUCT).second)
        }
    }

@Test
    fun mismatchedKindOperation_fallBackToGeneric() {
        assertNull(roles(CryptoKind.MAC, CryptoOperation.DIGEST).first)
        assertNull(roles(CryptoKind.MAC, CryptoOperation.DIGEST).second)
        // 无方向来源时 CIPHER 操作不匹配 → 回落(CIPHER 分支以 direction 为主判据)。
        assertNull(roles(CryptoKind.CIPHER, CryptoOperation.GET_IV).first)
        assertNull(roles(CryptoKind.MESSAGE_DIGEST, CryptoOperation.INIT).second)
        assertNull(roles(CryptoKind.BASE64, CryptoOperation.GET_IV).first)
    }
}
